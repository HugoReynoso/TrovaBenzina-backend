package it.trovabenzina.integration.mimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.LoggerFactory;

@EnabledIfSystemProperty(named = "mimit.live-test", matches = "true")
class MimitDownloadLiveTest {
	private final MimitDownloadService downloads = new MimitDownloadService(Duration.ofSeconds(60),
			Duration.ofSeconds(120), 3, Duration.ofSeconds(2));
	private final MimitCsvParser parser = new MimitCsvParser();

	@Test
	void downloadsAndParsesOfficialStations() {
		String csv = downloads.download("https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv");
		Path file;
		try {
			file = Files.createTempFile("mimit-header-", ".csv");
			try {
				Files.writeString(file, csv);
				parser.validateStationsUpload(file);
			} finally {
				Files.deleteIfExists(file);
			}
		} catch (java.io.IOException ex) {
			throw new java.io.UncheckedIOException(ex);
		}
		var stations = parser.parseStations(csv);
		assertThat(stations).isNotEmpty();
		LoggerFactory.getLogger(getClass()).info("Live MIMIT stations parsed: {}", stations.size());
	}

	@Test
	void downloadsAndParsesOfficialPrices() throws Exception {
		Path csv = downloads.downloadToTempFile("https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv");
		try {
			parser.validatePricesUpload(csv);
			AtomicInteger prices = new AtomicInteger();
			AtomicInteger skipped = new AtomicInteger();
			parser.parsePrices(csv, 1000, batch -> prices.addAndGet(batch.size()), message -> skipped.incrementAndGet());
			assertThat(prices.get()).isPositive();
			LoggerFactory.getLogger(getClass()).info("Live MIMIT prices parsed: {}, skipped: {}", prices, skipped);
		} finally {
			Files.deleteIfExists(csv);
		}
	}
}
