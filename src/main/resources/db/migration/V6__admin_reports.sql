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

CREATE INDEX IF NOT EXISTS idx_price_reports_status ON price_reports(status);
CREATE INDEX IF NOT EXISTS idx_price_reports_station_id ON price_reports(station_id);
CREATE INDEX IF NOT EXISTS idx_admin_logs_created_at ON admin_logs(created_at);
