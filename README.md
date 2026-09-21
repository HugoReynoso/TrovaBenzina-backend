# TrovaBenzina Backend

Backend monolitico Spring Boot per il progetto TrovaBenzina. L'obiettivo e' esporre API per consultare distributori, prezzi carburante, statistiche cittadine e preparare l'import automatico dagli Open Data MIMIT.

## Stack

- Java 21
- Spring Boot
- Maven
- Spring Web MVC
- Spring Data JPA
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
DB_PASSWORD=
MIMIT_STATIONS_URL=
MIMIT_PRICES_URL=
MIMIT_IMPORT_CRON=0 0 3 * * *
MIMIT_IMPORT_ENABLED=true
```

Gli URL MIMIT sono intenzionalmente vuoti di default: vanno impostati con gli URL ufficiali Open Data quando confermati.

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

## Avvio

```bash
./mvnw spring-boot:run
```

Su Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

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

L'endpoint admin e' temporaneo e deve essere protetto prima della produzione.

## Import MIMIT

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
