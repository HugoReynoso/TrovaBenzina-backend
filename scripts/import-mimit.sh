#!/usr/bin/env bash
set -euo pipefail
umask 077

: "${API_BASE_URL:?Set API_BASE_URL to the backend origin}"
: "${MIMIT_ADMIN_EMAIL:?Set MIMIT_ADMIN_EMAIL}"
: "${MIMIT_ADMIN_PASSWORD:?Set MIMIT_ADMIN_PASSWORD}"
IMPORT_MODE="${IMPORT_MODE:-full}"
API_BASE_URL="${API_BASE_URL%/}"

case "$IMPORT_MODE" in
  full|prices) ;;
  *) echo 'IMPORT_MODE must be full or prices' >&2; exit 1 ;;
esac
case "$API_BASE_URL" in
  https://*|http://localhost:*|http://127.0.0.1:*) ;;
  *) echo 'Use HTTPS for the backend (HTTP is allowed only on localhost)' >&2; exit 1 ;;
esac

work_dir="$(mktemp -d)"
trap 'rm -rf -- "$work_dir"' EXIT

download() {
  local dataset="$1"
  curl --fail --silent --show-error --location --proto '=https' --proto-redir '=https' \
    --connect-timeout 20 --max-time 120 --retry 2 --retry-all-errors --retry-delay 5 \
    --output "$work_dir/$dataset.csv" \
    "https://www.mimit.gov.it/images/exportCSV/$dataset.csv"
  test -s "$work_dir/$dataset.csv"
}

echo 'Downloading official MIMIT prices'
download prezzo_alle_8
grep -Fq 'idImpianto|desc_carburante|prezzo|is_self|dtComu' "$work_dir/prezzo_alle_8.csv" || {
  echo 'Unexpected MIMIT prices header; import cancelled' >&2; exit 1;
}
if [[ "$IMPORT_MODE" == full ]]; then
  echo 'Downloading official MIMIT stations'
  download anagrafica_impianti_attivi
  grep -Fq 'idImpianto|Gestore|Bandiera|Tipo Impianto|Nome Impianto|Indirizzo|Comune|Provincia|Latitudine|Longitudine' \
    "$work_dir/anagrafica_impianti_attivi.csv" || {
      echo 'Unexpected MIMIT stations header; import cancelled' >&2; exit 1;
    }
fi

# Credentials and JWT stay in private temporary files, not in command arguments or logs.
jq -n '{email: env.MIMIT_ADMIN_EMAIL, password: env.MIMIT_ADMIN_PASSWORD}' > "$work_dir/login.json"
curl --fail --silent --show-error --connect-timeout 20 --max-time 120 \
  --header 'Content-Type: application/json' --data-binary "@$work_dir/login.json" \
  --output "$work_dir/login-response.json" "$API_BASE_URL/api/auth/login"
token="$(jq -er '.token | select(type == "string" and length > 0)' "$work_dir/login-response.json")"
if [[ "${GITHUB_ACTIONS:-}" == true ]]; then
  printf '::add-mask::%s\n' "$token"
fi
printf 'Authorization: Bearer %s\n' "$token" > "$work_dir/auth.headers"
unset token

endpoint='/api/admin/mimit/import/prices/upload'
form_args=(--form "prices=@$work_dir/prezzo_alle_8.csv;type=text/csv")
if [[ "$IMPORT_MODE" == full ]]; then
  endpoint='/api/admin/mimit/import/upload'
  form_args+=(--form "stations=@$work_dir/anagrafica_impianti_attivi.csv;type=text/csv")
fi
echo "Uploading MIMIT CSVs ($IMPORT_MODE)"
# Do not retry uploads: a lost HTTP response can follow already committed database batches.
curl --fail-with-body --silent --show-error --connect-timeout 20 --max-time 1200 \
  --header "@$work_dir/auth.headers" "${form_args[@]}" \
  --output "$work_dir/import-response.json" "$API_BASE_URL$endpoint" || {
    cat "$work_dir/import-response.json" >&2 2>/dev/null || true
    echo 'Upload/import failed. Check backend logs before retrying: batches may already be committed.' >&2
    exit 1
  }
jq . "$work_dir/import-response.json"
jq -e '.pricesRead > 0 and (.pricesInserted + .pricesUpdated) > 0 and .errors == 0' \
  "$work_dir/import-response.json" > /dev/null || {
    echo 'Import returned no applied prices or reported errors; inspect the result above.' >&2
    exit 1
  }
echo 'MIMIT import completed'
