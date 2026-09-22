package it.trovabenzina.integration.mimit;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import it.trovabenzina.exception.MimitImportException;

@Component
public class MimitCsvParser {

	private static final Logger log = LoggerFactory.getLogger(MimitCsvParser.class);
	private static final char MIMIT_DELIMITER = '|';

	public List<MimitStationRecord> parseStations(String csv) {
		List<MimitStationRecord> records = new ArrayList<>();
		for (CSVRecord row : parseRecords(csv)) {
			try {
				String mimitId = first(row, "idimpianto", "id_impianto", "id impianto", "mimit_id");
				if (isBlank(mimitId)) {
					log.warn("Skipping MIMIT station row {}: missing station id", row.getRecordNumber());
					continue;
				}
				records.add(new MimitStationRecord(mimitId, first(row, "nome", "name", "gestore", "nomeimpianto"),
						first(row, "bandiera", "brand", "marchio"), first(row, "indirizzo", "address"),
						first(row, "comune", "municipality"), first(row, "provincia", "siglaprovincia", "province_code"),
						parseDouble(first(row, "latitudine", "latitude", "lat")), parseDouble(first(row, "longitudine",
								"longitude", "lng", "lon"))));
			} catch (RuntimeException ex) {
				log.warn("Skipping malformed MIMIT station row {}: {}", row.getRecordNumber(), ex.getMessage());
			}
		}
		return records;
	}

	public List<MimitPriceRecord> parsePrices(String csv) {
		List<MimitPriceRecord> records = new ArrayList<>();
		for (CSVRecord row : parseRecords(csv)) {
			try {
				String stationMimitId = first(row, "idimpianto", "id_impianto", "id impianto", "station_mimit_id");
				String fuelName = first(row, "desc_carburante", "carburante", "fuel_type_name", "fuel", "nomecarburante");
				BigDecimal price = parseBigDecimal(first(row, "prezzo", "price"));
				if (isBlank(stationMimitId) || isBlank(fuelName) || price == null) {
					log.warn("Skipping MIMIT price row {}: missing station, fuel or price", row.getRecordNumber());
					continue;
				}
				records.add(new MimitPriceRecord(stationMimitId, normalizeFuelTypeName(first(row, "codice_carburante",
						"fuel_type_code", "fuel_code", "carburante", "desc_carburante", "nomecarburante")), fuelName,
						price, parseBoolean(first(row, "is_self", "self", "self_service", "tipo", "servito")),
						parseDateTime(first(row, "dtcomu", "dt_comu", "data_comunicazione", "communicated_at"))));
			} catch (RuntimeException ex) {
				log.warn("Skipping malformed MIMIT price row {}: {}", row.getRecordNumber(), ex.getMessage());
			}
		}
		return records;
	}

	public String normalizeFuelTypeName(String rawName) {
		if (isBlank(rawName)) {
			return null;
		}
		String normalized = rawName.trim().toUpperCase(Locale.ROOT).replace("À", "A").replace("È", "E")
				.replace("É", "E").replace("Ì", "I").replace("Ò", "O").replace("Ù", "U")
				.replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
		return switch (normalized) {
			case "GASOLIO", "DIESEL", "GASOLIO_SELF", "DIESEL_SELF" -> "DIESEL";
			case "BENZINA", "SUPER", "SENZA_PIOMBO", "VERDE" -> "BENZINA";
			case "GPL" -> "GPL";
			case "METANO", "CNG" -> "METANO";
			default -> normalized;
		};
	}

	private List<CSVRecord> parseRecords(String csv) {
		if (isBlank(csv)) {
			return List.of();
		}
		String normalizedCsv = removePreamble(csv);
		CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(MIMIT_DELIMITER).setHeader().setQuote(null)
				.setSkipHeaderRecord(true).setIgnoreEmptyLines(true).setTrim(true).build();
		try (CSVParser parser = format.parse(new StringReader(normalizedCsv))) {
			return parser.stream().toList();
		} catch (IllegalArgumentException | IOException ex) {
			throw new MimitImportException("Invalid MIMIT CSV content", ex);
		}
	}

	private String removePreamble(String csv) {
		String normalized = csv.replace("\r\n", "\n").replace('\r', '\n');
		String[] lines = normalized.split("\n", -1);
		for (int i = 0; i < lines.length; i++) {
			if (lines[i].indexOf(MIMIT_DELIMITER) >= 0) {
				return String.join("\n", java.util.Arrays.copyOfRange(lines, i, lines.length));
			}
		}
		return normalized;
	}

	private String first(CSVRecord row, String... keys) {
		Map<String, String> values = row.toMap();
		for (String key : keys) {
			for (Map.Entry<String, String> entry : values.entrySet()) {
				if (normalizeHeader(entry.getKey()).equals(normalizeHeader(key)) && !isBlank(entry.getValue())) {
					return entry.getValue().trim();
				}
			}
		}
		return null;
	}

	private String normalizeHeader(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
	}

	private Double parseDouble(String value) {
		return isBlank(value) ? null : Double.valueOf(value.trim().replace(',', '.'));
	}

	private BigDecimal parseBigDecimal(String value) {
		return isBlank(value) ? null : new BigDecimal(value.trim().replace(',', '.'));
	}

	private Boolean parseBoolean(String value) {
		if (isBlank(value)) {
			return Boolean.FALSE;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		return normalized.equals("1") || normalized.equals("true") || normalized.equals("self")
				|| normalized.equals("self service") || normalized.equals("self-service") || normalized.equals("si")
				|| normalized.equals("sì") || normalized.equals("no logo self");
	}

	private LocalDateTime parseDateTime(String value) {
		if (isBlank(value)) {
			return null;
		}
		String trimmed = value.trim();
		try {
			return OffsetDateTime.parse(trimmed).toLocalDateTime();
		} catch (DateTimeParseException ignored) {
			// Try explicit local date-time formats below.
		}
		List<DateTimeFormatter> formatters = List.of(DateTimeFormatter.ISO_LOCAL_DATE_TIME,
				DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"), DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
				DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
		for (DateTimeFormatter formatter : formatters) {
			try {
				return LocalDateTime.parse(trimmed, formatter);
			} catch (DateTimeParseException ignored) {
				// Try the next known MIMIT/export format.
			}
		}
		log.warn("Unable to parse MIMIT date '{}'", trimmed);
		return null;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
