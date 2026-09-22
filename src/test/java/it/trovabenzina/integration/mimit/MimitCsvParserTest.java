package it.trovabenzina.integration.mimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MimitCsvParserTest {

	private final MimitCsvParser parser = new MimitCsvParser();

	@Test
	void parsesPipeSeparatedStationRows() {
		String csv = """
				idImpianto|Gestore|Bandiera|Indirizzo|Comune|Provincia|Latitudine|Longitudine
				123|Mario Rossi|Q8|Via Roma 1|Milano|MI|45,4642|9.1900
				""";

		MimitStationRecord record = parser.parseStations(csv).getFirst();

		assertThat(record.mimitId()).isEqualTo("123");
		assertThat(record.name()).isEqualTo("Mario Rossi");
		assertThat(record.brand()).isEqualTo("Q8");
		assertThat(record.provinceCode()).isEqualTo("MI");
		assertThat(record.latitude()).isEqualTo(45.4642);
	}

	@Test
	void ignoresMimitExtractionPreambleBeforeHeader() {
		String csv = """
				Estrazione del 2026-09-20
				idImpianto|descCarburante|prezzo|isSelf|dtComu
				3464|Gasolio|2.809|0|18/09/2026 20:00:10
				""";

		MimitPriceRecord record = parser.parsePrices(csv).getFirst();

		assertThat(record.stationMimitId()).isEqualTo("3464");
		assertThat(record.fuelTypeCode()).isEqualTo("DIESEL");
		assertThat(record.price()).isEqualByComparingTo("2.809");
		assertThat(record.selfService()).isFalse();
	}

	@Test
	void parsesQuotedPipeSeparatedPriceRows() {
		String csv = """
				idImpianto|desc_carburante|prezzo|is_self|dtComu
				123|"Benzina"|1,789|1|22/09/2026 08:30:00
				""";

		MimitPriceRecord record = parser.parsePrices(csv).getFirst();

		assertThat(record.stationMimitId()).isEqualTo("123");
		assertThat(record.fuelTypeCode()).isEqualTo("BENZINA");
		assertThat(record.price()).isEqualByComparingTo(new BigDecimal("1.789"));
		assertThat(record.selfService()).isTrue();
		assertThat(record.communicatedAt()).isNotNull();
	}

	@Test
	void skipsMalformedPriceRowsWithoutStoppingImport() {
		String csv = """
				idImpianto|desc_carburante|prezzo|is_self|dtComu
				123|Benzina||1|22/09/2026 08:30:00
				124|Gasolio|1.700|0|2026-09-22 09:00:00
				""";

		assertThat(parser.parsePrices(csv)).singleElement().satisfies(record -> {
			assertThat(record.stationMimitId()).isEqualTo("124");
			assertThat(record.fuelTypeCode()).isEqualTo("DIESEL");
			assertThat(record.selfService()).isFalse();
		});
	}
}
