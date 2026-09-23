-- ============================================================
-- V8 - Allineamento schema produzione
-- TrovaBenzina
-- ============================================================


-- ------------------------------------------------------------
-- 1. CITY SLUG
--
-- Uno slug non può essere UNIQUE a livello nazionale perché
-- esistono comuni con lo stesso nome in province diverse.
--
-- Esempi reali:
-- livo
-- peglio
-- samone
-- san-teodoro
-- paterno
-- castro
--
-- L'unicità corretta è:
-- provincia + slug
-- ------------------------------------------------------------

ALTER TABLE cities
    DROP CONSTRAINT IF EXISTS cities_slug_key;

CREATE UNIQUE INDEX IF NOT EXISTS ux_cities_province_slug
    ON cities (province_id, slug);


-- ------------------------------------------------------------
-- 2. CITY ALIASES
--
-- Serve per collegare denominazioni MIMIT storiche / alternative
-- con il comune ISTAT corrente.
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS city_aliases (

    id BIGSERIAL PRIMARY KEY,

    alias_name VARCHAR(150) NOT NULL,

    province_code VARCHAR(10),

    city_id BIGINT NOT NULL REFERENCES cities(id)

);

CREATE UNIQUE INDEX IF NOT EXISTS ux_city_aliases_name_province
    ON city_aliases (
        UPPER(TRIM(alias_name)),
        UPPER(TRIM(COALESCE(province_code, '')))
    );

CREATE INDEX IF NOT EXISTS idx_city_aliases_city_id
    ON city_aliases(city_id);


-- ------------------------------------------------------------
-- 3. FUEL BRANDS
--
-- Anagrafica marchi carburante.
--
-- Non colleghiamo ancora stations.brand con una FK perché MIMIT
-- restituisce molti valori differenti / non normalizzati.
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS fuel_brands (

    id BIGSERIAL PRIMARY KEY,

    name VARCHAR(100) NOT NULL UNIQUE,

    logo_url VARCHAR(500),

    website_url VARCHAR(500),

    active BOOLEAN NOT NULL DEFAULT TRUE

);

CREATE INDEX IF NOT EXISTS idx_fuel_brands_active
    ON fuel_brands(active);