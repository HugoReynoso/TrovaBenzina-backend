CREATE TABLE IF NOT EXISTS fuel_types (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS stations (
    id BIGSERIAL PRIMARY KEY,
    mimit_id VARCHAR(40) UNIQUE,
    name VARCHAR(255) NOT NULL,
    brand VARCHAR(120),
    address VARCHAR(255),
    municipality VARCHAR(120),
    province_code VARCHAR(10),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    city_id BIGINT REFERENCES cities(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stations_mimit_id ON stations(mimit_id);
CREATE INDEX IF NOT EXISTS idx_stations_city_id ON stations(city_id);
CREATE INDEX IF NOT EXISTS idx_stations_brand ON stations(brand);

INSERT INTO fuel_types (code, name, active)
VALUES
    ('BENZINA', 'Benzina', TRUE),
    ('DIESEL', 'Diesel', TRUE),
    ('GPL', 'GPL', TRUE),
    ('METANO', 'Metano', TRUE)
ON CONFLICT (code) DO NOTHING;
