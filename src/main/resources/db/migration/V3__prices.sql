CREATE TABLE IF NOT EXISTS station_prices (
    id BIGSERIAL PRIMARY KEY,
    station_id BIGINT NOT NULL REFERENCES stations(id),
    fuel_type_id BIGINT NOT NULL REFERENCES fuel_types(id),
    price NUMERIC(8, 3) NOT NULL,
    self_service BOOLEAN NOT NULL,
    communicated_at TIMESTAMP,
    imported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_station_price_current UNIQUE (station_id, fuel_type_id, self_service)
);

CREATE INDEX IF NOT EXISTS idx_station_prices_station_id ON station_prices(station_id);
CREATE INDEX IF NOT EXISTS idx_station_prices_fuel_type_id ON station_prices(fuel_type_id);
CREATE INDEX IF NOT EXISTS idx_station_prices_price ON station_prices(price);
