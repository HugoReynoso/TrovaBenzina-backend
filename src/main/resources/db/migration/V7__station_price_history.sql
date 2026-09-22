CREATE TABLE IF NOT EXISTS station_price_history (
    id BIGSERIAL PRIMARY KEY,
    station_id BIGINT NOT NULL REFERENCES stations(id),
    fuel_type_id BIGINT NOT NULL REFERENCES fuel_types(id),
    price NUMERIC(8, 3) NOT NULL,
    self_service BOOLEAN NOT NULL,
    communicated_at TIMESTAMP,
    imported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_station_price_history_station_id ON station_price_history(station_id);
CREATE INDEX IF NOT EXISTS idx_station_price_history_fuel_type_id ON station_price_history(fuel_type_id);
CREATE INDEX IF NOT EXISTS idx_station_price_history_communicated_at ON station_price_history(communicated_at);
CREATE INDEX IF NOT EXISTS idx_station_price_history_lookup
    ON station_price_history(station_id, fuel_type_id, self_service, communicated_at);
