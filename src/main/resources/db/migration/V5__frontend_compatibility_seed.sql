ALTER TABLE cities ADD COLUMN IF NOT EXISTS slug VARCHAR(140);
ALTER TABLE cities ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION;
ALTER TABLE cities ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;
ALTER TABLE city_fuel_daily_statistics ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE stations ALTER COLUMN mimit_id TYPE VARCHAR(40) USING mimit_id::VARCHAR;

UPDATE fuel_types SET code = 'DIESEL', name = 'Diesel' WHERE code = 'GASOLIO';

INSERT INTO regions (id, name) VALUES
    (1, 'Lombardia'),
    (2, 'Lazio'),
    (3, 'Piemonte'),
    (4, 'Campania'),
    (5, 'Emilia-Romagna'),
    (6, 'Toscana')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO provinces (id, name, code, region_id) VALUES
    (1, 'Milano', 'MI', 1),
    (2, 'Roma', 'RM', 2),
    (3, 'Torino', 'TO', 3),
    (4, 'Napoli', 'NA', 4),
    (5, 'Bologna', 'BO', 5),
    (6, 'Firenze', 'FI', 6)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, code = EXCLUDED.code, region_id = EXCLUDED.region_id;

INSERT INTO cities (id, name, slug, province_id, latitude, longitude) VALUES
    (1, 'Milano', 'milano', 1, 45.4642, 9.1900),
    (2, 'Roma', 'roma', 2, 41.9028, 12.4964),
    (3, 'Torino', 'torino', 3, 45.0703, 7.6869),
    (4, 'Napoli', 'napoli', 4, 40.8518, 14.2681),
    (5, 'Bologna', 'bologna', 5, 44.4949, 11.3426),
    (6, 'Firenze', 'firenze', 6, 43.7696, 11.2558)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    slug = EXCLUDED.slug,
    province_id = EXCLUDED.province_id,
    latitude = EXCLUDED.latitude,
    longitude = EXCLUDED.longitude;

INSERT INTO fuel_types (code, name, active) VALUES
    ('BENZINA', 'Benzina', TRUE),
    ('DIESEL', 'Diesel', TRUE),
    ('GPL', 'GPL', TRUE),
    ('METANO', 'Metano', TRUE)
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, active = EXCLUDED.active;

INSERT INTO stations (id, mimit_id, name, brand, address, municipality, province_code, latitude, longitude, active, city_id) VALUES
    (101, 'MI-000101', 'Q8 Loreto', 'Q8', 'Viale Monza 12, Milano', 'Milano', 'MI', 45.4852, 9.2186, TRUE, 1),
    (102, 'MI-000102', 'Eni Porta Romana', 'Eni', 'Corso Lodi 34, Milano', 'Milano', 'MI', 45.4507, 9.2057, TRUE, 1),
    (103, 'MI-000103', 'IP Navigli', 'IP', 'Via Valenza 7, Milano', 'Milano', 'MI', 45.4525, 9.1714, TRUE, 1),
    (104, 'MI-000104', 'Tamoil Sempione', 'Tamoil', 'Corso Sempione 91, Milano', 'Milano', 'MI', 45.4850, 9.1598, TRUE, 1),
    (105, 'MI-000105', 'Esso Bicocca', 'Esso', 'Viale Sarca 226, Milano', 'Milano', 'MI', 45.5205, 9.2138, TRUE, 1),
    (201, 'RM-000201', 'Q8 San Giovanni', 'Q8', 'Via Appia Nuova 180, Roma', 'Roma', 'RM', 41.8840, 12.5156, TRUE, 2)
ON CONFLICT (id) DO UPDATE SET
    mimit_id = EXCLUDED.mimit_id,
    name = EXCLUDED.name,
    brand = EXCLUDED.brand,
    address = EXCLUDED.address,
    municipality = EXCLUDED.municipality,
    province_code = EXCLUDED.province_code,
    latitude = EXCLUDED.latitude,
    longitude = EXCLUDED.longitude,
    active = EXCLUDED.active,
    city_id = EXCLUDED.city_id,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO station_prices (station_id, fuel_type_id, price, self_service, communicated_at, imported_at)
SELECT 101, id, 1.729, TRUE, TIMESTAMP '2026-09-20 08:30:00', CURRENT_TIMESTAMP FROM fuel_types WHERE code = 'BENZINA'
ON CONFLICT (station_id, fuel_type_id, self_service) DO UPDATE SET price = EXCLUDED.price, communicated_at = EXCLUDED.communicated_at;
INSERT INTO station_prices (station_id, fuel_type_id, price, self_service, communicated_at, imported_at)
SELECT 101, id, 1.899, FALSE, TIMESTAMP '2026-09-20 08:30:00', CURRENT_TIMESTAMP FROM fuel_types WHERE code = 'BENZINA'
ON CONFLICT (station_id, fuel_type_id, self_service) DO UPDATE SET price = EXCLUDED.price, communicated_at = EXCLUDED.communicated_at;
INSERT INTO station_prices (station_id, fuel_type_id, price, self_service, communicated_at, imported_at)
SELECT 101, id, 1.639, TRUE, TIMESTAMP '2026-09-20 08:30:00', CURRENT_TIMESTAMP FROM fuel_types WHERE code = 'DIESEL'
ON CONFLICT (station_id, fuel_type_id, self_service) DO UPDATE SET price = EXCLUDED.price, communicated_at = EXCLUDED.communicated_at;
INSERT INTO station_prices (station_id, fuel_type_id, price, self_service, communicated_at, imported_at)
SELECT 101, id, 0.719, TRUE, TIMESTAMP '2026-09-20 08:30:00', CURRENT_TIMESTAMP FROM fuel_types WHERE code = 'GPL'
ON CONFLICT (station_id, fuel_type_id, self_service) DO UPDATE SET price = EXCLUDED.price, communicated_at = EXCLUDED.communicated_at;

INSERT INTO station_prices (station_id, fuel_type_id, price, self_service, communicated_at, imported_at)
SELECT 102, id, 1.782, TRUE, TIMESTAMP '2026-09-20 08:30:00', CURRENT_TIMESTAMP FROM fuel_types WHERE code = 'BENZINA'
ON CONFLICT (station_id, fuel_type_id, self_service) DO UPDATE SET price = EXCLUDED.price, communicated_at = EXCLUDED.communicated_at;
INSERT INTO station_prices (station_id, fuel_type_id, price, self_service, communicated_at, imported_at)
SELECT 102, id, 1.954, FALSE, TIMESTAMP '2026-09-20 08:30:00', CURRENT_TIMESTAMP FROM fuel_types WHERE code = 'BENZINA'
ON CONFLICT (station_id, fuel_type_id, self_service) DO UPDATE SET price = EXCLUDED.price, communicated_at = EXCLUDED.communicated_at;
INSERT INTO station_prices (station_id, fuel_type_id, price, self_service, communicated_at, imported_at)
SELECT 102, id, 1.674, TRUE, TIMESTAMP '2026-09-20 08:30:00', CURRENT_TIMESTAMP FROM fuel_types WHERE code = 'DIESEL'
ON CONFLICT (station_id, fuel_type_id, self_service) DO UPDATE SET price = EXCLUDED.price, communicated_at = EXCLUDED.communicated_at;
INSERT INTO station_prices (station_id, fuel_type_id, price, self_service, communicated_at, imported_at)
SELECT 102, id, 1.389, TRUE, TIMESTAMP '2026-09-20 08:30:00', CURRENT_TIMESTAMP FROM fuel_types WHERE code = 'METANO'
ON CONFLICT (station_id, fuel_type_id, self_service) DO UPDATE SET price = EXCLUDED.price, communicated_at = EXCLUDED.communicated_at;

INSERT INTO station_prices (station_id, fuel_type_id, price, self_service, communicated_at, imported_at)
SELECT 103, id, 1.697, TRUE, TIMESTAMP '2026-09-20 08:30:00', CURRENT_TIMESTAMP FROM fuel_types WHERE code = 'BENZINA'
ON CONFLICT (station_id, fuel_type_id, self_service) DO UPDATE SET price = EXCLUDED.price, communicated_at = EXCLUDED.communicated_at;
INSERT INTO station_prices (station_id, fuel_type_id, price, self_service, communicated_at, imported_at)
SELECT 103, id, 1.608, TRUE, TIMESTAMP '2026-09-20 08:30:00', CURRENT_TIMESTAMP FROM fuel_types WHERE code = 'DIESEL'
ON CONFLICT (station_id, fuel_type_id, self_service) DO UPDATE SET price = EXCLUDED.price, communicated_at = EXCLUDED.communicated_at;
INSERT INTO station_prices (station_id, fuel_type_id, price, self_service, communicated_at, imported_at)
SELECT 103, id, 0.704, TRUE, TIMESTAMP '2026-09-20 08:30:00', CURRENT_TIMESTAMP FROM fuel_types WHERE code = 'GPL'
ON CONFLICT (station_id, fuel_type_id, self_service) DO UPDATE SET price = EXCLUDED.price, communicated_at = EXCLUDED.communicated_at;

INSERT INTO station_prices (station_id, fuel_type_id, price, self_service, communicated_at, imported_at)
SELECT 104, id, 1.836, TRUE, TIMESTAMP '2026-09-20 08:30:00', CURRENT_TIMESTAMP FROM fuel_types WHERE code = 'BENZINA'
ON CONFLICT (station_id, fuel_type_id, self_service) DO UPDATE SET price = EXCLUDED.price, communicated_at = EXCLUDED.communicated_at;
INSERT INTO station_prices (station_id, fuel_type_id, price, self_service, communicated_at, imported_at)
SELECT 104, id, 1.711, TRUE, TIMESTAMP '2026-09-20 08:30:00', CURRENT_TIMESTAMP FROM fuel_types WHERE code = 'DIESEL'
ON CONFLICT (station_id, fuel_type_id, self_service) DO UPDATE SET price = EXCLUDED.price, communicated_at = EXCLUDED.communicated_at;
INSERT INTO station_prices (station_id, fuel_type_id, price, self_service, communicated_at, imported_at)
SELECT 104, id, 1.429, TRUE, TIMESTAMP '2026-09-20 08:30:00', CURRENT_TIMESTAMP FROM fuel_types WHERE code = 'METANO'
ON CONFLICT (station_id, fuel_type_id, self_service) DO UPDATE SET price = EXCLUDED.price, communicated_at = EXCLUDED.communicated_at;

INSERT INTO station_prices (station_id, fuel_type_id, price, self_service, communicated_at, imported_at)
SELECT 105, id, 1.749, TRUE, TIMESTAMP '2026-09-20 08:30:00', CURRENT_TIMESTAMP FROM fuel_types WHERE code = 'BENZINA'
ON CONFLICT (station_id, fuel_type_id, self_service) DO UPDATE SET price = EXCLUDED.price, communicated_at = EXCLUDED.communicated_at;
INSERT INTO station_prices (station_id, fuel_type_id, price, self_service, communicated_at, imported_at)
SELECT 105, id, 1.624, TRUE, TIMESTAMP '2026-09-20 08:30:00', CURRENT_TIMESTAMP FROM fuel_types WHERE code = 'DIESEL'
ON CONFLICT (station_id, fuel_type_id, self_service) DO UPDATE SET price = EXCLUDED.price, communicated_at = EXCLUDED.communicated_at;
INSERT INTO station_prices (station_id, fuel_type_id, price, self_service, communicated_at, imported_at)
SELECT 105, id, 0.735, TRUE, TIMESTAMP '2026-09-20 08:30:00', CURRENT_TIMESTAMP FROM fuel_types WHERE code = 'GPL'
ON CONFLICT (station_id, fuel_type_id, self_service) DO UPDATE SET price = EXCLUDED.price, communicated_at = EXCLUDED.communicated_at;

INSERT INTO station_prices (station_id, fuel_type_id, price, self_service, communicated_at, imported_at)
SELECT 201, id, 1.766, TRUE, TIMESTAMP '2026-09-20 08:30:00', CURRENT_TIMESTAMP FROM fuel_types WHERE code = 'BENZINA'
ON CONFLICT (station_id, fuel_type_id, self_service) DO UPDATE SET price = EXCLUDED.price, communicated_at = EXCLUDED.communicated_at;
INSERT INTO station_prices (station_id, fuel_type_id, price, self_service, communicated_at, imported_at)
SELECT 201, id, 1.649, TRUE, TIMESTAMP '2026-09-20 08:30:00', CURRENT_TIMESTAMP FROM fuel_types WHERE code = 'DIESEL'
ON CONFLICT (station_id, fuel_type_id, self_service) DO UPDATE SET price = EXCLUDED.price, communicated_at = EXCLUDED.communicated_at;

INSERT INTO city_fuel_daily_statistics (city_id, fuel_type_id, date, average_price, minimum_price, maximum_price, station_count, updated_at)
SELECT 1, id, DATE '2026-09-01', 1.759, 1.697, 1.836, 5, TIMESTAMP '2026-09-20 08:30:00' FROM fuel_types WHERE code = 'BENZINA'
ON CONFLICT (city_id, fuel_type_id, date) DO UPDATE SET average_price = EXCLUDED.average_price, minimum_price = EXCLUDED.minimum_price, maximum_price = EXCLUDED.maximum_price, station_count = EXCLUDED.station_count, updated_at = EXCLUDED.updated_at;
INSERT INTO city_fuel_daily_statistics (city_id, fuel_type_id, date, average_price, minimum_price, maximum_price, station_count, updated_at)
SELECT 1, id, DATE '2026-09-01', 1.651, 1.608, 1.711, 5, TIMESTAMP '2026-09-20 08:30:00' FROM fuel_types WHERE code = 'DIESEL'
ON CONFLICT (city_id, fuel_type_id, date) DO UPDATE SET average_price = EXCLUDED.average_price, minimum_price = EXCLUDED.minimum_price, maximum_price = EXCLUDED.maximum_price, station_count = EXCLUDED.station_count, updated_at = EXCLUDED.updated_at;
INSERT INTO city_fuel_daily_statistics (city_id, fuel_type_id, date, average_price, minimum_price, maximum_price, station_count, updated_at)
SELECT 1, id, DATE '2026-09-01', 0.719, 0.704, 0.735, 3, TIMESTAMP '2026-09-20 08:30:00' FROM fuel_types WHERE code = 'GPL'
ON CONFLICT (city_id, fuel_type_id, date) DO UPDATE SET average_price = EXCLUDED.average_price, minimum_price = EXCLUDED.minimum_price, maximum_price = EXCLUDED.maximum_price, station_count = EXCLUDED.station_count, updated_at = EXCLUDED.updated_at;
INSERT INTO city_fuel_daily_statistics (city_id, fuel_type_id, date, average_price, minimum_price, maximum_price, station_count, updated_at)
SELECT 1, id, DATE '2026-09-01', 1.409, 1.389, 1.429, 2, TIMESTAMP '2026-09-20 08:30:00' FROM fuel_types WHERE code = 'METANO'
ON CONFLICT (city_id, fuel_type_id, date) DO UPDATE SET average_price = EXCLUDED.average_price, minimum_price = EXCLUDED.minimum_price, maximum_price = EXCLUDED.maximum_price, station_count = EXCLUDED.station_count, updated_at = EXCLUDED.updated_at;

CREATE TABLE IF NOT EXISTS admin_users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(180) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS price_reports (
    id BIGSERIAL PRIMARY KEY,
    station_id BIGINT NOT NULL REFERENCES stations(id),
    station_name VARCHAR(255) NOT NULL,
    brand VARCHAR(120),
    city_name VARCHAR(120) NOT NULL,
    fuel_type_id BIGINT NOT NULL REFERENCES fuel_types(id),
    price NUMERIC(8, 3) NOT NULL,
    self_service BOOLEAN NOT NULL,
    reporter_name VARCHAR(120),
    reporter_email VARCHAR(180),
    note VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS admin_logs (
    id BIGSERIAL PRIMARY KEY,
    level VARCHAR(20) NOT NULL,
    area VARCHAR(40) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO price_reports (id, station_id, station_name, brand, city_name, fuel_type_id, price, self_service, reporter_name, reporter_email, note, status, submitted_at)
SELECT 9001, 103, 'IP Navigli', 'IP', 'Milano', id, 1.689, TRUE, 'Utente mobile', NULL, 'Prezzo visto sul tabellone alle 08:10.', 'PENDING', TIMESTAMP '2026-09-21 08:20:00' FROM fuel_types WHERE code = 'BENZINA'
ON CONFLICT (id) DO NOTHING;
INSERT INTO price_reports (id, station_id, station_name, brand, city_name, fuel_type_id, price, self_service, reporter_name, reporter_email, note, status, submitted_at)
SELECT 9002, 101, 'Q8 Loreto', 'Q8', 'Milano', id, 1.632, TRUE, NULL, 'utente@example.com', NULL, 'PENDING', TIMESTAMP '2026-09-21 09:05:00' FROM fuel_types WHERE code = 'DIESEL'
ON CONFLICT (id) DO NOTHING;

INSERT INTO admin_logs (id, level, area, message, created_at) VALUES
    (7101, 'WARNING', 'api', 'Fallback mock attivato: NEXT_PUBLIC_API_BASE_URL non configurato o backend non raggiungibile.', TIMESTAMP '2026-09-21 09:12:00'),
    (7102, 'INFO', 'reports', 'Nuova segnalazione prezzo ricevuta per IP Navigli.', TIMESTAMP '2026-09-21 08:20:00'),
    (7103, 'ERROR', 'frontend', 'Esempio log bug: errore client non bloccante nella futura integrazione monitoring.', TIMESTAMP '2026-09-20 18:42:00')
ON CONFLICT (id) DO NOTHING;
