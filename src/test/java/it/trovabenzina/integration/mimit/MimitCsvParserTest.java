package it.trovabenzina.integration.mimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MimitCsvParserTest {

	private final MimitCsvParser parser = new MimitCsvParser();

	@TempDir
	private Path tempDir;

	@Test
	void parsesPipeSeparatedStationRows() {
		String csv = """
				idImpianto|Gestore|Bandiera|Tipo Impianto|Nome Impianto|Indirizzo|Comune|Provincia|Latitudine|Longitudine
				123|Mario Rossi|Q8|Stradale|Q8 Loreto|Via Roma 1|Milano|MI|45,4642|9.1900
				""";

		MimitStationRecord record = parser.parseStations(csv).getFirst();

		assertThat(record.mimitId()).isEqualTo("123");
		assertThat(record.name()).isEqualTo("Q8 Loreto");
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
	void recoversStationRowsWithUnescapedPipeInsideCentralTextFields() {
		String csv = """
				Estrazione del 2026-09-20
				idImpianto|Gestore|Bandiera|Tipo Impianto|Nome Impianto|Indirizzo|Comune|Provincia|Latitudine|Longitudine
				40820|STOIL SIMPLE|Pompe Bianche|Stradale|STOIL SIMPLE | gestori.prezzibenzina.it|STR. PROV.LE 82 SPINETTA SALE 15122|ALESSANDRIA|AL|44.91704718250436|8.70067298412323
				""";

		MimitStationRecord record = parser.parseStations(csv).getFirst();

		assertThat(record.mimitId()).isEqualTo("40820");
		assertThat(record.brand()).isEqualTo("Pompe Bianche");
		assertThat(record.name()).isEqualTo("STOIL SIMPLE | gestori.prezzibenzina.it");
		assertThat(record.address()).isEqualTo("STR. PROV.LE 82 SPINETTA SALE 15122");
		assertThat(record.municipality()).isEqualTo("ALESSANDRIA");
		assertThat(record.provinceCode()).isEqualTo("AL");
		assertThat(record.latitude()).isEqualTo(44.91704718250436);
		assertThat(record.longitude()).isEqualTo(8.70067298412323);
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

	@Test
	void streamsPriceRowsInBatchesFromFile() throws Exception {
		String csv = """
				Estrazione del 2026-09-20
				idImpianto|desc_carburante|prezzo|is_self|dtComu
				123|Benzina|1.789|1|22/09/2026 08:30:00
				124|Gasolio|1.700|0|2026-09-22 09:00:00
				125|GPL|0.750|1|2026-09-22 09:30:00
				""";
		Path path = tempDir.resolve("prices.csv");
		Files.writeString(path, csv);
		List<List<MimitPriceRecord>> batches = new ArrayList<>();

		parser.parsePrices(path, 2, batches::add, ignored -> {
		});

		assertThat(batches).hasSize(2);
		assertThat(batches.getFirst()).hasSize(2);
		assertThat(batches.get(1)).hasSize(1);
		assertThat(batches.getFirst().getFirst().fuelTypeCode()).isEqualTo("BENZINA");
		assertThat(batches.getFirst().get(1).fuelTypeCode()).isEqualTo("DIESEL");
	}
}
