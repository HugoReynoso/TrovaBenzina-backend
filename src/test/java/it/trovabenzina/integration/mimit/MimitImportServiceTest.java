package it.trovabenzina.integration.mimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import it.trovabenzina.entity.City;
import it.trovabenzina.entity.FuelType;
import it.trovabenzina.entity.Province;
import it.trovabenzina.entity.Station;
import it.trovabenzina.entity.StationPrice;
import it.trovabenzina.repository.CityRepository;
import it.trovabenzina.repository.FuelTypeRepository;
import it.trovabenzina.repository.StationPriceHistoryRepository;
import it.trovabenzina.repository.StationPriceRepository;
import it.trovabenzina.repository.StationRepository;
import it.trovabenzina.service.CityFuelStatisticService;
import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class MimitImportServiceTest {

	@Mock
	private MimitDownloadService downloadService;
	@Mock
	private StationRepository stationRepository;
	@Mock
	private StationPriceRepository stationPriceRepository;
	@Mock
	private StationPriceHistoryRepository historyRepository;
	@Mock
	private FuelTypeRepository fuelTypeRepository;
	@Mock
	private CityRepository cityRepository;
	@Mock
	private CityFuelStatisticService statisticService;
	@Mock
	private PlatformTransactionManager transactionManager;
	@Mock
	private EntityManager entityManager;

	@TempDir
	private Path tempDir;

	private MimitImportService importService;

	@BeforeEach
	void setUp() {
		when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
		importService = new MimitImportService(new MimitProperties("https://example.test/stations.csv",
				"https://example.test/prices.csv", "0 0 3 * * *", false), downloadService, new MimitCsvParser(),
				stationRepository, stationPriceRepository, historyRepository, fuelTypeRepository, cityRepository,
				statisticService, transactionManager, entityManager);
	}

	@Test
	void importsNewStationPriceAndHistory() throws Exception {
		String stationsCsv = """
				idImpianto|Gestore|Bandiera|Tipo Impianto|Nome Impianto|Indirizzo|Comune|Provincia|Latitudine|Longitudine
				123|Mario Rossi|Q8|Stradale|Q8 Loreto|Via Roma 1|Milano|MI|45.4642|9.1900
				""";
		String pricesCsv = """
				idImpianto|desc_carburante|prezzo|is_self|dtComu
				123|Benzina|1.789|1|22/09/2026 08:30:00
				""";
		Path pricesFile = writeTempCsv(pricesCsv);
		when(downloadService.download("https://example.test/stations.csv")).thenReturn(stationsCsv);
		when(downloadService.downloadToTempFile("https://example.test/prices.csv")).thenReturn(pricesFile);

		City city = new City();
		city.setId(7L);
		city.setName("Milano");
		Province province = new Province();
		province.setCode("MI");
		city.setProvince(province);
		when(cityRepository.findByNamesAndProvinceCodes(anyCollection(), anyCollection())).thenReturn(List.of(city));

		Station savedStation = new Station();
		savedStation.setId(10L);
		savedStation.setMimitId("123");
		savedStation.setCity(city);
		when(stationRepository.findByMimitIdIn(anyCollection())).thenReturn(List.of(), List.of(savedStation));

		FuelType fuelType = new FuelType();
		fuelType.setId(3L);
		fuelType.setCode("BENZINA");
		fuelType.setName("Benzina");
		when(fuelTypeRepository.findAll()).thenReturn(List.of(fuelType));
		when(fuelTypeRepository.findByCodeIgnoreCase("BENZINA")).thenReturn(java.util.Optional.of(fuelType));
		when(stationPriceRepository.findCurrentByStationIdInAndFuelTypeIdIn(anyCollection(), anyCollection()))
				.thenReturn(List.of());

		MimitImportResult result = importService.importData();

		assertThat(result.stationsRead()).isEqualTo(1);
		assertThat(result.stationsInserted()).isEqualTo(1);
		assertThat(result.pricesRead()).isEqualTo(1);
		assertThat(result.pricesInserted()).isEqualTo(1);
		assertThat(result.historyInserted()).isEqualTo(1);
		assertThat(result.statisticsUpdated()).isEqualTo(1);
		verify(stationPriceRepository).saveAll(any());
		verify(statisticService).recalculate(7L, fuelType, LocalDate.now());
		verify(stationPriceRepository, never()).findAll();
	}

	@Test
	void avoidsDuplicateHistoryWhenSamePriceAlreadyExists() throws Exception {
		String stationsCsv = """
				idImpianto|Gestore|Bandiera|Tipo Impianto|Nome Impianto|Indirizzo|Comune|Provincia|Latitudine|Longitudine
				123|Mario Rossi|Q8|Stradale|Q8 Loreto|Via Roma 1|Milano|MI|45.4642|9.1900
				""";
		String pricesCsv = """
				idImpianto|desc_carburante|prezzo|is_self|dtComu
				123|Benzina|1.789|1|22/09/2026 08:30:00
				""";
		Path pricesFile = writeTempCsv(pricesCsv);
		when(downloadService.download("https://example.test/stations.csv")).thenReturn(stationsCsv);
		when(downloadService.downloadToTempFile("https://example.test/prices.csv")).thenReturn(pricesFile);

		City city = new City();
		city.setId(7L);
		city.setName("Milano");
		Province province = new Province();
		province.setCode("MI");
		city.setProvince(province);
		when(cityRepository.findByNamesAndProvinceCodes(anyCollection(), anyCollection())).thenReturn(List.of(city));

		Station station = new Station();
		station.setId(10L);
		station.setMimitId("123");
		station.setCity(city);
		when(stationRepository.findByMimitIdIn(anyCollection())).thenReturn(List.of(station), List.of(station));

		FuelType fuelType = new FuelType();
		fuelType.setId(3L);
		fuelType.setCode("BENZINA");
		when(fuelTypeRepository.findAll()).thenReturn(List.of(fuelType));
		when(fuelTypeRepository.findByCodeIgnoreCase("BENZINA")).thenReturn(java.util.Optional.of(fuelType));

		StationPrice current = new StationPrice();
		current.setId(99L);
		current.setStation(station);
		current.setFuelType(fuelType);
		current.setPrice(new BigDecimal("1.789"));
		current.setSelfService(true);
		current.setCommunicatedAt(java.time.LocalDateTime.of(2026, 9, 22, 8, 30));
		when(stationPriceRepository.findCurrentByStationIdInAndFuelTypeIdIn(anyCollection(), anyCollection()))
				.thenReturn(List.of(current));

		MimitImportResult result = importService.importData();

		assertThat(result.pricesUpdated()).isEqualTo(1);
		assertThat(result.historyInserted()).isZero();
		verify(stationPriceRepository, never()).findAll();
	}

	@Test
	void importPricesProcessesCsvWithoutStationImport() throws Exception {
		String pricesCsv = """
				idImpianto|desc_carburante|prezzo|is_self|dtComu
				123|Benzina|1.789|1|22/09/2026 08:30:00
				""";
		Path pricesFile = writeTempCsv(pricesCsv);
		when(downloadService.downloadToTempFile("https://example.test/prices.csv")).thenReturn(pricesFile);

		City city = new City();
		city.setId(7L);
		Station station = new Station();
		station.setId(10L);
		station.setMimitId("123");
		station.setCity(city);
		when(stationRepository.findByMimitIdIn(anyCollection())).thenReturn(List.of(station));

		FuelType fuelType = new FuelType();
		fuelType.setId(3L);
		fuelType.setCode("BENZINA");
		when(fuelTypeRepository.findAll()).thenReturn(List.of(fuelType));
		when(fuelTypeRepository.findByCodeIgnoreCase("BENZINA")).thenReturn(java.util.Optional.of(fuelType));
		when(stationPriceRepository.findCurrentByStationIdInAndFuelTypeIdIn(anyCollection(), anyCollection()))
				.thenReturn(List.of());

		MimitImportResult result = importService.importPrices();

		assertThat(result.stationsRead()).isZero();
		assertThat(result.pricesRead()).isEqualTo(1);
		assertThat(result.pricesInserted()).isEqualTo(1);
		verify(downloadService, never()).download(eq("https://example.test/stations.csv"));
		verify(stationPriceRepository, never()).findAll();
	}

	private Path writeTempCsv(String csv) throws Exception {
		Path path = tempDir.resolve("mimit.csv");
		Files.writeString(path, csv);
		return path;
	}
}
