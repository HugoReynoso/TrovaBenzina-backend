CREATE TABLE IF NOT EXISTS city_fuel_daily_statistics (
    id BIGSERIAL PRIMARY KEY,
    city_id BIGINT NOT NULL REFERENCES cities(id),
    fuel_type_id BIGINT NOT NULL REFERENCES fuel_types(id),
    date DATE NOT NULL,
    average_price NUMERIC(8, 3) NOT NULL,
    minimum_price NUMERIC(8, 3) NOT NULL,
    maximum_price NUMERIC(8, 3) NOT NULL,
    station_count INTEGER NOT NULL,
    CONSTRAINT uk_city_fuel_stat_date UNIQUE (city_id, fuel_type_id, date)
);

CREATE INDEX IF NOT EXISTS idx_city_fuel_stats_city_id ON city_fuel_daily_statistics(city_id);
CREATE INDEX IF NOT EXISTS idx_city_fuel_stats_fuel_type_id ON city_fuel_daily_statistics(fuel_type_id);
CREATE INDEX IF NOT EXISTS idx_city_fuel_stats_date ON city_fuel_daily_statistics(date);
CREATE INDEX IF NOT EXISTS idx_city_fuel_stats_lookup ON city_fuel_daily_statistics(city_id, fuel_type_id, date);
