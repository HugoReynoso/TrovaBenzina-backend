# Import MIMIT tramite upload

Questo percorso evita le connessioni in uscita da Render verso MIMIT: il download
avviene sul runner GitHub Actions, poi i file vengono inviati al backend via HTTPS.
La fonte resta il Ministero, senza mirror di terze parti.

## Attivazione

1. Pubblica queste modifiche su GitHub e distribuisci il backend aggiornato su Render.
2. In GitHub, Settings > Secrets and variables > Actions > Secrets, crea:
   - `MIMIT_ADMIN_EMAIL`: email di un amministratore del backend di produzione.
   - `MIMIT_ADMIN_PASSWORD`: relativa password. Non usare le credenziali demo.
3. In Actions > Import MIMIT data > Run workflow scegli `full` per anagrafica e prezzi,
   oppure `prices` per aggiornare solo i prezzi dei distributori gia' presenti.
4. Verifica che il risultato riporti prezzi inseriti/aggiornati e zero errori.
5. Per abilitare la pianificazione, crea la variabile Actions `MIMIT_ACTIONS_ENABLED=true`.
   Il workflow e' predisposto ogni giorno alle 09:17 UTC (10:17 in inverno, 11:17 in estate a Roma).
   Senza questa variabile i run schedulati saltano il job; il lancio manuale resta disponibile.
6. Su Render imposta `MIMIT_IMPORT_ENABLED=false` per disattivare lo scheduler che
   tenta il download diretto. Questa variabile non disabilita gli endpoint manuali/upload.

Il backend predefinito del workflow e' `https://trova-benzina-api.onrender.com`.
Per modificarlo imposta la variabile Actions `MIMIT_API_BASE_URL` con l'origine HTTPS,
senza `/api` finale. La password viene usata per ottenere un JWT nuovo a ogni esecuzione.
Secret, risposte login e token non vengono stampati nei log o caricati come artifact.

Il workflow si avvia solo manualmente o secondo la pianificazione; non parte su push o pull request.
I job di questo workflow sono serializzati, senza cancellare un import in corso.
Gli import manuali o il vecchio scheduler non partecipano a questa coda: evita di lanciarli
mentre il workflow sta importando.

## Endpoint

Entrambi richiedono `Authorization: Bearer <JWT_ADMIN>` e `multipart/form-data`.
Riutilizzano upsert, storico, statistiche e richiesta di rebuild del frontend esistenti.
Il rebuild parte solo se il servizio di import termina senza eccezioni; resta disattivato
se `VERCEL_DEPLOY_HOOK_URL` non e' configurato.

| Metodo | Percorso | Parti file richieste |
| --- | --- | --- |
| POST | `/api/admin/mimit/import/upload` | `stations`, `prices` |
| POST | `/api/admin/mimit/import/prices/upload` | `prices` |

Gli endpoint precedenti `/import`, `/import/stations`, `/import/prices` restano disponibili
e continuano a scaricare direttamente dal ministero. Su un servizio con problemi di
connettivita' MIMIT usa i nuovi endpoint upload.

I file devono essere CSV ufficiali UTF-8, separatore `|`, intestazione originale e
almeno una riga valida per dataset. E' ammessa la riga iniziale della data di estrazione.
L'upload completo valida entrambi i file prima di modificare il database.
Le righe individuali malformate vengono trattate come nell'import esistente.

Limiti predefiniti, modificabili su Render:

```properties
MIMIT_UPLOAD_MAX_FILE_SIZE=20MB
MIMIT_UPLOAD_MAX_REQUEST_SIZE=45MB
```

I file vengono elaborati da disco e rimossi alla fine, anche se l'import fallisce.
Il file anagrafica viene letto in memoria per riutilizzare il parser esistente;
i prezzi sono validati ed elaborati a batch da 1000 righe.

Risposte di errore: `400` per file mancanti/vuoti o CSV non validi, `413` per upload
oltre i limiti e `403` per richieste prive di un JWT valido.

## Caricamento manuale

Scarica sul computer i file ufficiali:

- [Anagrafica](https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv)
- [Prezzi](https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv)

Ottieni un token con il normale `POST /api/auth/login`. In Postman usa il nuovo URL,
Authorization > Bearer Token e Body > form-data: entrambe le chiavi `stations` e
`prices` devono essere di tipo File. Lascia che Postman generi il Content-Type con boundary.

Esempio Bash, assumendo che `ADMIN_TOKEN` sia gia' impostato nella sessione:

```bash
curl --fail-with-body --max-time 1200 \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -F 'stations=@anagrafica_impianti_attivi.csv;type=text/csv' \
  -F 'prices=@prezzo_alle_8.csv;type=text/csv' \
  https://trova-benzina-api.onrender.com/api/admin/mimit/import/upload
```

Per i soli prezzi ometti `stations` e usa `/api/admin/mimit/import/prices/upload`.
Da PowerShell usa `curl.exe`, con la sintassi di continuazione righe di PowerShell,
oppure configura la stessa richiesta in Postman.

Lo script `scripts/import-mimit.sh` puo' anche essere eseguito in Bash con `curl` e
`jq`, impostando `API_BASE_URL`, `MIMIT_ADMIN_EMAIL`, `MIMIT_ADMIN_PASSWORD` e
`IMPORT_MODE=full` oppure `prices`.

## Errori e verifica

- Il runner GitHub deve poter raggiungere sia MIMIT sia il backend. Il primo run
  manuale verifica la connettivita' reale da GitHub; i test locali non la garantiscono.
- I download GET hanno retry; l'upload POST non viene ritentato automaticamente.
  Il salvataggio resta a batch: un errore del database, un timeout del proxy o una
  risposta persa possono lasciare un import parziale. Controlla i log prima di rilanciarlo.
- Una risposta `200` contiene contatori, scarti e messaggi. Lo script fallisce anche
  se non risultano prezzi applicati o il contatore `errors` e' maggiore di zero.
- Per evitare dati giornalieri non ancora pubblicati, la pianificazione e' successiva
  all'orario osservato di pubblicazione. Non viene garantita la freschezza del dataset:
  controlla `communicatedAt` e la data di estrazione se il ministero pubblica in ritardo.
- Test: `./mvnw test`. I test HTTP verificano JWT, multipart, limiti di dimensione e cleanup.
- Test script, con Bash e jq disponibili: `bash scripts/test-import-mimit.sh`.
- Test live opzionali: `./mvnw -Dmimit.live-test=true -Dtest=MimitDownloadLiveTest test`.

Riferimento per la configurazione dei secret:
[GitHub Actions secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets).
