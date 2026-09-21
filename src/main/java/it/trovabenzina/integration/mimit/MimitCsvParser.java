package it.trovabenzina.integration.mimit;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class MimitCsvParser {

	public List<MimitStationRecord> parseStations(String csv) {
		List<Map<String, String>> rows = parseRows(csv);
		List<MimitStationRecord> records = new ArrayList<>();
		for (Map<String, String> row : rows) {
			Long mimitId = parseLong(first(row, "idimpianto", "id_impianto", "mimit_id"));
			records.add(new MimitStationRecord(mimitId, first(row, "nome", "name", "gestore"),
					first(row, "bandiera", "brand"), first(row, "indirizzo", "address"),
					first(row, "comune", "municipality"), first(row, "provincia", "province", "province_code"),
					parseDouble(first(row, "latitudine", "latitude")), parseDouble(first(row, "longitudine", "longitude"))));
		}
		return records;
	}

	public List<MimitPriceRecord> parsePrices(String csv) {
		List<Map<String, String>> rows = parseRows(csv);
		List<MimitPriceRecord> records = new ArrayList<>();
		for (Map<String, String> row : rows) {
			Long stationMimitId = parseLong(first(row, "idimpianto", "id_impianto", "station_mimit_id"));
			String fuelName = first(row, "desc_carburante", "carburante", "fuel_type_name", "fuel");
			String fuelCode = normalizeFuelCode(first(row, "codice_carburante", "fuel_type_code", "fuel_code", "carburante"));
			records.add(new MimitPriceRecord(stationMimitId, fuelCode, fuelName, parseBigDecimal(first(row, "prezzo", "price")),
					parseBoolean(first(row, "is_self", "self", "self_service")), parseDateTime(first(row, "dtcomu", "communicated_at"))));
		}
		return records;
	}

	private List<Map<String, String>> parseRows(String csv) {
		if (csv == null || csv.isBlank()) {
			return List.of();
		}
		String[] lines = csv.replace("\r\n", "\n").replace('\r', '\n').split("\n");
		if (lines.length < 2) {
			return List.of();
		}
		String separator = lines[0].contains(";") ? ";" : ",";
		String[] headers = split(lines[0], separator);
		List<Map<String, String>> rows = new ArrayList<>();
		for (int i = 1; i < lines.length; i++) {
			if (lines[i].isBlank()) {
				continue;
			}
			String[] values = split(lines[i], separator);
			Map<String, String> row = new HashMap<>();
			for (int j = 0; j < headers.length && j < values.length; j++) {
				row.put(normalize(headers[j]), trimQuotes(values[j]));
			}
			rows.add(row);
		}
		return rows;
	}

	private String[] split(String line, String separator) {
		return line.split(separator, -1);
	}

	private String first(Map<String, String> row, String... keys) {
		for (String key : keys) {
			String value = row.get(normalize(key));
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(" ", "_");
	}

	private String trimQuotes(String value) {
		return value == null ? null : value.trim().replaceAll("^\"|\"$", "");
	}

	private Long parseLong(String value) {
		return value == null || value.isBlank() ? null : Long.valueOf(value.trim());
	}

	private Double parseDouble(String value) {
		return value == null || value.isBlank() ? null : Double.valueOf(value.replace(',', '.'));
	}

	private BigDecimal parseBigDecimal(String value) {
		return value == null || value.isBlank() ? null : new BigDecimal(value.replace(',', '.'));
	}

	private Boolean parseBoolean(String value) {
		if (value == null || value.isBlank()) {
			return Boolean.FALSE;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		return normalized.equals("1") || normalized.equals("true") || normalized.equals("self") || normalized.equals("si");
	}

	private LocalDateTime parseDateTime(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.trim();
		List<DateTimeFormatter> formatters = List.of(DateTimeFormatter.ISO_LOCAL_DATE_TIME,
				DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
		for (DateTimeFormatter formatter : formatters) {
			try {
				return LocalDateTime.parse(trimmed, formatter);
			} catch (java.time.format.DateTimeParseException ignored) {
				// Try the next common format used by CSV exports.
			}
		}
		return null;
	}

	private String normalizeFuelCode(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
	}
}
