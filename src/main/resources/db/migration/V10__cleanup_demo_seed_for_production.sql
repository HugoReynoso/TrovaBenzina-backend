-- ============================================================
-- V10 - Rimozione seed demo su database appena creati
--
-- ATTENZIONE:
-- la pulizia completa della geografia viene eseguita SOLO
-- se il database sembra essere ancora quello demo creato da V5.
-- In questo modo il database locale reale non viene toccato.
-- ============================================================


-- 1. Rimuove sempre i record inequivocabilmente demo

DELETE FROM admin_logs
WHERE id IN (7101, 7102, 7103);

DELETE FROM price_reports
WHERE id IN (9001, 9002);


-- 2. Rimuove prezzi collegati alle stazioni demo

DELETE FROM station_price_history
WHERE station_id IN (
    SELECT id
    FROM stations
    WHERE mimit_id IN (
        'MI-000101',
        'MI-000102',
        'MI-000103',
        'MI-000104',
        'MI-000105',
        'RM-000201'
    )
);

DELETE FROM station_prices
WHERE station_id IN (
    SELECT id
    FROM stations
    WHERE mimit_id IN (
        'MI-000101',
        'MI-000102',
        'MI-000103',
        'MI-000104',
        'MI-000105',
        'RM-000201'
    )
);


-- 3. Elimina le stazioni demo

DELETE FROM stations
WHERE mimit_id IN (
    'MI-000101',
    'MI-000102',
    'MI-000103',
    'MI-000104',
    'MI-000105',
    'RM-000201'
);


-- 4. Sul database demo appena creato elimina anche
-- statistiche e geografia seed.
--
-- NON viene eseguito sul DB locale reale con migliaia di comuni.

DO $$
BEGIN

    IF
        (SELECT COUNT(*) FROM regions) <= 6
        AND
        (SELECT COUNT(*) FROM provinces) <= 6
        AND
        (SELECT COUNT(*) FROM cities) <= 6
    THEN

        DELETE FROM city_fuel_daily_statistics;

        DELETE FROM cities
        WHERE id IN (1, 2, 3, 4, 5, 6);

        DELETE FROM provinces
        WHERE id IN (1, 2, 3, 4, 5, 6);

        DELETE FROM regions
        WHERE id IN (1, 2, 3, 4, 5, 6);

    END IF;

END $$;