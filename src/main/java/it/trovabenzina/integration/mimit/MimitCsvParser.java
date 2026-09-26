package it.trovabenzina.integration.mimit;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

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
		List<String[]> rows = parseStationRows(csv);
		for (int i = 0; i < rows.size(); i++) {
			String[] values = rows.get(i);
			long rowNumber = i + 1L;
			try {
				String mimitId = cleanValue(values[0]);
				if (isBlank(mimitId)) {
					log.warn("Skipping MIMIT station row {}: missing station id", rowNumber);
					continue;
				}
				records.add(toStationRecord(values));
			} catch (RuntimeException ex) {
				log.warn("Skipping malformed MIMIT station row {}: {}", rowNumber, ex.getMessage());
			}
		}
		return records;
	}

	private MimitStationRecord toStationRecord(String[] values) {
		String mimitId = cleanValue(values[0]);
		String manager = cleanValue(values[1]);
		String brand = cleanValue(values[2]);
		String name = cleanValue(values[4]);
		String address = cleanValue(values[5]);
		String municipality = cleanValue(values[6]);
		String provinceCode = cleanValue(values[7]);
		String latitude = cleanValue(values[8]);
		String longitude = cleanValue(values[9]);
		return new MimitStationRecord(mimitId, valueOrFallback(name, manager), brand, address, municipality,
				provinceCode, parseDouble(latitude), parseDouble(longitude));
	}

	private List<String[]> parseStationRows(String csv) {
		if (isBlank(csv)) {
			return List.of();
		}
		String normalizedCsv = removePreamble(csv);
		String[] lines = normalizedCsv.split("\n", -1);
		List<String[]> rows = new ArrayList<>();
		for (int i = 1; i < lines.length; i++) {
			if (lines[i].isBlank()) {
				continue;
			}
			String[] tokens = lines[i].split("\\|", -1);
			if (tokens.length == 10) {
				rows.add(tokens);
			} else if (tokens.length > 10) {
				log.warn("Recovered malformed MIMIT row {} with {} columns", i + 1, tokens.length);
				rows.add(recoverStationRow(tokens));
			} else {
				log.warn("Skipping malformed MIMIT station row {}: expected 10 columns, found {}", i + 1, tokens.length);
			}
		}
		return rows;
	}

	private String[] recoverStationRow(String[] tokens) {
		String[] values = new String[10];
		values[0] = tokens[0];
		values[1] = tokens[1];
		values[2] = tokens[2];
		values[3] = tokens[3];
		values[6] = tokens[tokens.length - 4];
		values[7] = tokens[tokens.length - 3];
		values[8] = tokens[tokens.length - 2];
		values[9] = tokens[tokens.length - 1];
		String[] central = Arrays.copyOfRange(tokens, 4, tokens.length - 4);
		values[4] = central.length <= 1 ? "" : joinClean(Arrays.copyOf(central, central.length - 1));
		values[5] = central.length == 0 ? "" : cleanValue(central[central.length - 1]);
		return values;
	}

	private String joinClean(String[] values) {
		return Arrays.stream(values).map(this::cleanValue).filter(value -> !isBlank(value))
				.collect(java.util.stream.Collectors.joining(" | "));
	}

	public List<MimitPriceRecord> parsePrices(String csv) {
		List<MimitPriceRecord> records = new ArrayList<>();
		for (CSVRecord row : parseRecords(csv)) {
			MimitPriceRecord record = toPriceRecord(row, message -> log.warn("{}", message));
			if (record != null) {
				records.add(record);
			}
		}
		return records;
	}

	public void parsePrices(Path csvPath, int batchSize, Consumer<List<MimitPriceRecord>> batchConsumer,
			Consumer<String> skipConsumer) {
		if (batchSize < 1) {
			throw new IllegalArgumentException("batchSize must be greater than zero");
		}
		CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(MIMIT_DELIMITER).setHeader().setQuote(null)
				.setSkipHeaderRecord(true).setIgnoreEmptyLines(true).setTrim(true).build();
		try (Reader reader = readerStartingAtFirstDelimitedLine(csvPath); CSVParser parser = format.parse(reader)) {
			List<MimitPriceRecord> batch = new ArrayList<>(batchSize);
			for (CSVRecord row : parser) {
				MimitPriceRecord record = toPriceRecord(row, skipConsumer);
				if (record == null) {
					continue;
				}
				batch.add(record);
				if (batch.size() >= batchSize) {
					batchConsumer.accept(List.copyOf(batch));
					batch.clear();
				}
			}
			if (!batch.isEmpty()) {
				batchConsumer.accept(List.copyOf(batch));
			}
		} catch (IllegalArgumentException | IOException ex) {
			throw new MimitImportException("Invalid MIMIT CSV content", ex);
		}
	}

	private MimitPriceRecord toPriceRecord(CSVRecord row, Consumer<String> skipConsumer) {
		try {
			String stationMimitId = first(row, "idimpianto", "id_impianto", "id impianto", "station_mimit_id");
			String fuelName = first(row, "desc_carburante", "carburante", "fuel_type_name", "fuel", "nomecarburante");
			BigDecimal price = parseBigDecimal(first(row, "prezzo", "price"));
			if (isBlank(stationMimitId) || isBlank(fuelName) || price == null) {
				skipConsumer.accept("Skipping MIMIT price row " + row.getRecordNumber()
						+ ": missing station, fuel or price");
				return null;
			}
			return new MimitPriceRecord(stationMimitId, normalizeFuelTypeName(first(row, "codice_carburante",
					"fuel_type_code", "fuel_code", "carburante", "desc_carburante", "nomecarburante")), fuelName,
					price, parseBoolean(first(row, "is_self", "self", "self_service", "tipo", "servito")),
					parseDateTime(first(row, "dtcomu", "dt_comu", "data_comunicazione", "communicated_at")));
		} catch (RuntimeException ex) {
			skipConsumer.accept("Skipping malformed MIMIT price row " + row.getRecordNumber() + ": " + ex.getMessage());
			return null;
		}
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

	private Reader readerStartingAtFirstDelimitedLine(Path csvPath) throws IOException {
		Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8);
		StringBuilder firstLine = new StringBuilder();
		int value;
		boolean foundDelimiter = false;
		while ((value = reader.read()) != -1) {
			char current = (char) value;
			if (current == '\r') {
				continue;
			}
			if (current == '\n') {
				if (foundDelimiter) {
					break;
				}
				firstLine.setLength(0);
				continue;
			}
			firstLine.append(current);
			if (current == MIMIT_DELIMITER) {
				foundDelimiter = true;
			}
		}
		if (!foundDelimiter) {
			reader.close();
			return new StringReader("");
		}
		return new PrefixedReader(firstLine.append('\n').toString(), reader);
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
					return cleanValue(entry.getValue());
				}
			}
		}
		return null;
	}

	private String cleanValue(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
			return trimmed.substring(1, trimmed.length() - 1).trim();
		}
		return trimmed;
	}

	private String valueOrFallback(String value, String fallback) {
		return isBlank(value) ? fallback : value;
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

	private static final class PrefixedReader extends Reader {
		private final StringReader prefix;
		private final Reader delegate;

		private PrefixedReader(String prefix, Reader delegate) {
			this.prefix = new StringReader(prefix);
			this.delegate = delegate;
		}

		@Override
		public int read(char[] cbuf, int off, int len) throws IOException {
			int read = prefix.read(cbuf, off, len);
			return read != -1 ? read : delegate.read(cbuf, off, len);
		}

		@Override
		public void close() throws IOException {
			try {
				prefix.close();
			} finally {
				delegate.close();
			}
		}
	}
}
