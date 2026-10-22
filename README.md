# TrovaBenzina Backend

Backend monolitico Spring Boot compatibile con il frontend Next.js di TrovaBenzina. Espone JSON camelCase per consultare geografia, distributori, prezzi, statistiche, segnalazioni prezzo e pannello admin.

## Stack

- Java 21
- Spring Boot 3.3.x
- Maven
- Spring Web MVC
- Spring Data JPA
- Spring Security
- PostgreSQL
- Flyway
- Jakarta Validation
- Lombok

## Requisiti

- Java 21
- Maven, oppure wrapper `mvnw`
- PostgreSQL con database locale `trova_benzina`

## Configurazione

La configurazione principale e' in `src/main/resources/application.properties`.

Variabili ambiente supportate:

```bash
DB_URL=jdbc:postgresql://localhost:5432/trova_benzina
DB_USERNAME=postgres
DB_PASSWORD=la_password_del_tuo_postgres_locale
MIMIT_STATIONS_URL=
MIMIT_PRICES_URL=
MIMIT_IMPORT_CRON=0 0 3 * * *
MIMIT_IMPORT_ENABLED=true
FLYWAY_BASELINE_ON_MIGRATE=true
JWT_SECRET=dev-secret-change-me-before-production-please
ADMIN_EMAIL=admin@trovabenzina.it
ADMIN_PASSWORD=trova-admin
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

Gli URL MIMIT sono intenzionalmente vuoti di default: vanno impostati con gli URL ufficiali Open Data quando confermati.

Nota locale: `DB_PASSWORD` non ha un valore di default per evitare di committare password reali. Se PostgreSQL e' configurato con autenticazione SCRAM e avvii senza questa variabile, l'app fallisce in avvio con un errore simile a:

```text
The server requested SCRAM-based authentication, but no password was provided.
```

In quel caso imposta la password del tuo utente PostgreSQL prima di avviare l'app.

Per non impostare le variabili a ogni avvio, copia `.env.properties.example` in `.env.properties` nella root del backend e inserisci li' la tua password locale. Il file `.env.properties` e' ignorato da Git.

## Database e Flyway

Hibernate non modifica lo schema:

```properties
spring.jpa.hibernate.ddl-auto=none
```

Le migration sono in:

```text
src/main/resources/db/migration
```

Migration presenti:

- `V1__initial_geography.sql`: regioni, province, citta'
- `V2__stations_and_fuels.sql`: carburanti e distributori
- `V3__prices.sql`: prezzi correnti
- `V4__daily_statistics.sql`: statistiche giornaliere cittadine

Se il database locale contiene gia' tabelle create manualmente, controlla che nomi tabella e colonne coincidano con le migration prima di avviare Flyway. Le migration usano `CREATE TABLE IF NOT EXISTS` e non cancellano dati, ma Flyway registrera' lo stato nello schema history.

Per un database locale gia' non vuoto ma senza tabella `flyway_schema_history`, la configurazione abilita:

```properties
spring.flyway.baseline-on-migrate=true
```

Questo permette a Flyway di inizializzare la history table e proseguire con le migration successive.

## Avvio

```bash
./mvnw spring-boot:run
```

Su Windows:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/trova_benzina"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="la_password_del_tuo_postgres_locale"
.\mvnw.cmd spring-boot:run
```

Se usi IntelliJ/Eclipse/VS Code, aggiungi le stesse variabili nella Run Configuration del backend. Puoi usare `src/main/resources/application-local.properties.example` come promemoria dei nomi da configurare, senza inserire password nel repository.

## Test e build

```bash
./mvnw test
./mvnw clean package
```

## Struttura

```text
it.trovabenzina
├── controller
├── dto
├── entity
├── exception
├── integration.mimit
├── mapper
├── repository
├── service
└── TrovaBenzinaBackendApplication
```

## API principali

Geografia:

```bash
curl http://localhost:8080/api/regions
curl http://localhost:8080/api/provinces
curl "http://localhost:8080/api/provinces?regionId=1"
curl http://localhost:8080/api/cities
curl "http://localhost:8080/api/cities?provinceId=1"
```

Distributori:

```bash
curl "http://localhost:8080/api/stations?cityId=1&fuelType=BENZINA"
curl http://localhost:8080/api/stations/1
curl "http://localhost:8080/api/stations/cheapest?cityId=1&fuelType=BENZINA&limit=5"
```

Statistiche:

```bash
curl "http://localhost:8080/api/cities/1/fuel-statistics?fuelType=BENZINA"
curl "http://localhost:8080/api/cities/1/fuel-statistics/history?fuelType=BENZINA&from=2026-09-01&to=2026-09-21"
```

Import MIMIT manuale:

```bash
curl -X POST http://localhost:8080/api/admin/mimit/import
```

Segnalazioni prezzo:

```bash
curl -X POST http://localhost:8080/api/price-reports \
  -H "Content-Type: application/json" \
  -d '{"stationId":103,"stationName":"IP Navigli","brand":"IP","cityName":"Milano","fuelTypeCode":"BENZINA","price":1.689,"selfService":true,"reporterName":"Utente mobile","reporterEmail":"utente@example.com","note":"Prezzo visto sul tabellone"}'
```

Admin:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@trovabenzina.it","password":"trova-admin"}'

curl http://localhost:8080/api/admin/price-reports \
  -H "Authorization: Bearer <token>"

curl -X PATCH http://localhost:8080/api/admin/price-reports/9001/approve \
  -H "Authorization: Bearer <token>"

curl -X PATCH http://localhost:8080/api/admin/price-reports/9001/reject \
  -H "Authorization: Bearer <token>"
```

Gli endpoint `GET /api/admin/**` e `PATCH /api/admin/**` sono protetti da JWT. `POST /api/price-reports` e gli endpoint pubblici restano accessibili al frontend.

## Import MIMIT

Il backend integra l'import ufficiale dai CSV MIMIT Open Data. Non vengono usate fonti terze o scraping.

Se Render non riesce a collegarsi a MIMIT, usa il nuovo import tramite upload:
GitHub Actions scarica i CSV ufficiali e li invia al backend autenticandosi con JWT.
Sono disponibili `POST /api/admin/mimit/import/upload` (parti `stations` e `prices`)
e `POST /api/admin/mimit/import/prices/upload` (parte `prices`).
Guida per secret GitHub, attivazione del workflow e caricamento manuale:
[Import MIMIT tramite upload](docs/mimit-upload.md).

Variabili richieste:

```properties
MIMIT_STATIONS_URL=https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv
MIMIT_PRICES_URL=https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv
MIMIT_IMPORT_ENABLED=false
MIMIT_IMPORT_CRON=0 0 3 * * *
MIMIT_CONNECT_TIMEOUT=60s
MIMIT_REQUEST_TIMEOUT=120s
MIMIT_DOWNLOAD_MAX_ATTEMPTS=3
MIMIT_DOWNLOAD_RETRY_DELAY=2s
```

Gli URL non sono hardcodati nel codice: vanno impostati tramite ambiente o `.env.properties`.

Formato atteso:

- CSV con header
- separatore `|`
- encoding UTF-8
- decimali con `.` o `,`
- righe malformate saltate e loggate senza interrompere tutto l'import

Import manuale:

```bash
curl -X POST http://localhost:8080/api/admin/mimit/import \
  -H "Authorization: Bearer <token>"
```

Scheduler:

```properties
MIMIT_IMPORT_ENABLED=true
MIMIT_IMPORT_CRON=0 0 3 * * *
```

Flusso dati:

```text
download anagrafica -> parsing -> upsert stations -> download prezzi -> parsing -> upsert prezzi correnti -> storico prezzi -> statistiche città
```

Troubleshooting:

- `MIMIT URL is not configured`: imposta `MIMIT_STATIONS_URL` e `MIMIT_PRICES_URL`
- download fallito: HTTP 502 indica la risorsa MIMIT e distingue timeout, errore di rete e status HTTP; la causa completa e' nei log del backend
- timeout: limite configurabile per tentativo (120s di default), al massimo 3 tentativi con pausa di 2s per errori di rete, HTTP 429 e 5xx. Gli errori TLS non vengono ritentati; la verifica dei certificati rimane attiva
- su Render: ridistribuisci il backend aggiornato; puoi regolare i limiti con le variabili `MIMIT_*`. I retry possono portare la durata totale oltre il limite del proxy/client
- per aggiornare solo i prezzi: `POST /api/admin/mimit/import/prices` con `Authorization: Bearer <token>` evita il download e l'import dell'anagrafica
- schema non vuoto senza Flyway history: lascia `FLYWAY_BASELINE_ON_MIGRATE=true` in locale
- import parziale: controlla `messages` e contatori `stationsSkipped`, `pricesSkipped`, `errors` nella risposta

Test live opzionali (download e parsing dei CSV ufficiali, senza modificare il database):

```bash
./mvnw -Dmimit.live-test=true -Dtest=MimitDownloadLiveTest test
```

Se Java 21 su Windows segnala `Unable to establish loopback connection`, esegui i test
con `"-DargLine=-Djdk.net.unixdomain.tmpdir=."` per usare un percorso socket corto.

Package:

```text
it.trovabenzina.integration.mimit
```

Responsabilita':

- `MimitDownloadService`: scarica i CSV configurati
- `MimitCsvParser`: trasforma i CSV in record interni
- `MimitImportService`: upsert distributori, carburanti e prezzi
- `MimitImportScheduler`: avvia l'import periodico con cron configurabile

Flusso:

```text
MIMIT -> download CSV -> parse -> normalizzazione -> upsert Station -> upsert StationPrice -> ricalcolo statistiche
```
