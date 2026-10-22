#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
test_dir="$(mktemp -d)"
trap 'rm -rf -- "$test_dir"' EXIT
export TEST_CALL_LOG="$test_dir/calls"
export API_BASE_URL='http://127.0.0.1:8080'
export MIMIT_ADMIN_EMAIL='test@example.test'
export MIMIT_ADMIN_PASSWORD='password-with-"quotes"-and-\slashes'

# Replace only the network transport; exercise the real script and jq expressions.
curl() {
  local output='' header='' data='' url='' form_count=0
  while (($#)); do
    case "$1" in
      --output) output="$2"; shift 2 ;;
      --header) header="$2"; shift 2 ;;
      --data-binary) data="$2"; shift 2 ;;
      --form) form_count=$((form_count + 1)); shift 2 ;;
      --connect-timeout|--max-time|--retry|--retry-delay|--proto|--proto-redir) shift 2 ;;
      --*) shift ;;
      *) url="$1"; shift ;;
    esac
  done
  printf '%s\n' "$url" >> "$TEST_CALL_LOG"
  case "$url" in
    https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv)
      if [[ "${TEST_FAILURE:-}" == bad_csv ]]; then
        printf '<html>Unavailable</html>\n' > "$output"
      else
        printf 'idImpianto|desc_carburante|prezzo|is_self|dtComu\n123|Benzina|1.789|1|22/10/2026 08:00:00\n' > "$output"
      fi ;;
    https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv)
      printf 'idImpianto|Gestore|Bandiera|Tipo Impianto|Nome Impianto|Indirizzo|Comune|Provincia|Latitudine|Longitudine\n123|G|Q8|Stradale|Q8|Via Roma|Milano|MI|45.46|9.19\n' > "$output" ;;
    */api/auth/login)
      jq -e '.email == env.MIMIT_ADMIN_EMAIL and .password == env.MIMIT_ADMIN_PASSWORD' "${data#@}" >/dev/null || return 1
      if [[ "${TEST_FAILURE:-}" == login ]]; then return 22; fi
      printf '{"token":"test-jwt-must-not-appear"}' > "$output" ;;
    */api/admin/mimit/import/upload|*/api/admin/mimit/import/prices/upload)
      grep -Fxq 'Authorization: Bearer test-jwt-must-not-appear' "${header#@}" || return 1
      if [[ "$IMPORT_MODE" == full ]]; then
        [[ "$form_count" == 2 ]] || return 1
      else
        [[ "$form_count" == 1 ]] || return 1
      fi
      if [[ "${TEST_FAILURE:-}" == upload ]]; then
        printf '{"message":"import failed"}' > "$output"; return 22
      elif [[ "${TEST_FAILURE:-}" == no_prices ]]; then
        printf '{"pricesRead":1,"pricesInserted":0,"pricesUpdated":0,"errors":0}' > "$output"
      else
        printf '{"pricesRead":1,"pricesInserted":0,"pricesUpdated":1,"errors":0}' > "$output"
      fi ;;
    *) echo "Unexpected test URL: $url" >&2; return 1 ;;
  esac
}
export -f curl

run_case() {
  export IMPORT_MODE="$1" TEST_FAILURE="$2"
  local expected="$3" result=0
  : > "$TEST_CALL_LOG"
  bash "$script_dir/import-mimit.sh" > "$test_dir/output" 2>&1 || result=$?
  if [[ "$expected" == success ]]; then
    [[ "$result" == 0 ]] || { cat "$test_dir/output"; exit 1; }
  else
    [[ "$result" != 0 ]] || { echo "Expected failure for $TEST_FAILURE"; exit 1; }
  fi
  if grep -Fq 'test-jwt-must-not-appear' "$test_dir/output" ||
     grep -Fq "$MIMIT_ADMIN_PASSWORD" "$test_dir/output"; then
    echo 'A secret leaked into the script output' >&2; exit 1
  fi
  echo "PASS: mode=$IMPORT_MODE failure=${TEST_FAILURE:-none}"
}

run_case full '' success
[[ "$(wc -l < "$TEST_CALL_LOG")" == 4 ]]
run_case prices '' success
[[ "$(wc -l < "$TEST_CALL_LOG")" == 3 ]]
run_case full bad_csv failure
! grep -q '/api/' "$TEST_CALL_LOG"
run_case prices login failure
! grep -q '/api/admin/' "$TEST_CALL_LOG"
run_case full upload failure
[[ "$(grep -c '/api/admin/' "$TEST_CALL_LOG")" == 1 ]]
run_case prices no_prices failure
run_case invalid '' failure
[[ ! -s "$TEST_CALL_LOG" ]]
echo 'All MIMIT workflow script tests passed'
