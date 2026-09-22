package it.trovabenzina.integration.mimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import it.trovabenzina.entity.City;
import it.trovabenzina.entity.FuelType;
import it.trovabenzina.entity.Station;
import it.trovabenzina.entity.StationPrice;
import it.trovabenzina.repository.CityRepository;
import it.trovabenzina.repository.FuelTypeRepository;
import it.trovabenzina.repository.StationPriceHistoryRepository;
import it.trovabenzina.repository.StationPriceRepository;
import it.trovabenzina.repository.StationRepository;
import it.trovabenzina.service.CityFuelStatisticService;

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

	private MimitImportService importService;

	@BeforeEach
	void setUp() {
		when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
		importService = new MimitImportService(new MimitProperties("https://example.test/stations.csv",
				"https://example.test/prices.csv", "0 0 3 * * *", false), downloadService, new MimitCsvParser(),
				stationRepository, stationPriceRepository, historyRepository, fuelTypeRepository, cityRepository,
				statisticService, transactionManager);
	}

	@Test
	void importsNewStationPriceAndHistory() {
		String stationsCsv = """
				idImpianto|Gestore|Bandiera|Indirizzo|Comune|Provincia|Latitudine|Longitudine
				123|Mario Rossi|Q8|Via Roma 1|Milano|MI|45.4642|9.1900
				""";
		String pricesCsv = """
				idImpianto|desc_carburante|prezzo|is_self|dtComu
				123|Benzina|1.789|1|22/09/2026 08:30:00
				""";
		when(downloadService.download("https://example.test/stations.csv")).thenReturn(stationsCsv);
		when(downloadService.download("https://example.test/prices.csv")).thenReturn(pricesCsv);

		City city = new City();
		city.setId(7L);
		city.setName("Milano");
		when(cityRepository.findFirstByNameIgnoreCaseAndProvinceCodeIgnoreCase("Milano", "MI")).thenReturn(Optional.of(city));

		Station savedStation = new Station();
		savedStation.setId(10L);
		savedStation.setMimitId("123");
		savedStation.setCity(city);
		when(stationRepository.findByMimitId("123")).thenReturn(Optional.empty(), Optional.of(savedStation));
		when(stationRepository.save(any(Station.class))).thenReturn(savedStation);

		FuelType fuelType = new FuelType();
		fuelType.setId(3L);
		fuelType.setCode("BENZINA");
		fuelType.setName("Benzina");
		when(fuelTypeRepository.findByCodeIgnoreCase("BENZINA")).thenReturn(Optional.of(fuelType));
		when(stationPriceRepository.findByStationIdAndFuelTypeIdAndSelfService(10L, 3L, true)).thenReturn(Optional.empty());
		when(historyRepository.existsByStationIdAndFuelTypeIdAndSelfServiceAndPriceAndCommunicatedAt(any(), any(), any(),
				any(BigDecimal.class), any())).thenReturn(false);

		MimitImportResult result = importService.importData();

		assertThat(result.stationsRead()).isEqualTo(1);
		assertThat(result.stationsInserted()).isEqualTo(1);
		assertThat(result.pricesRead()).isEqualTo(1);
		assertThat(result.pricesInserted()).isEqualTo(1);
		assertThat(result.historyInserted()).isEqualTo(1);
		assertThat(result.statisticsUpdated()).isEqualTo(1);
		verify(stationPriceRepository).save(any(StationPrice.class));
		verify(statisticService).recalculate(7L, fuelType, LocalDate.now());
	}

	@Test
	void avoidsDuplicateHistoryWhenSamePriceAlreadyExists() {
		String stationsCsv = """
				idImpianto|Gestore|Comune|Provincia
				123|Mario Rossi|Milano|MI
				""";
		String pricesCsv = """
				idImpianto|desc_carburante|prezzo|is_self|dtComu
				123|Benzina|1.789|1|22/09/2026 08:30:00
				""";
		when(downloadService.download("https://example.test/stations.csv")).thenReturn(stationsCsv);
		when(downloadService.download("https://example.test/prices.csv")).thenReturn(pricesCsv);

		City city = new City();
		city.setId(7L);
		when(cityRepository.findFirstByNameIgnoreCaseAndProvinceCodeIgnoreCase("Milano", "MI")).thenReturn(Optional.of(city));

		Station station = new Station();
		station.setId(10L);
		station.setMimitId("123");
		station.setCity(city);
		when(stationRepository.findByMimitId("123")).thenReturn(Optional.of(station), Optional.of(station));

		FuelType fuelType = new FuelType();
		fuelType.setId(3L);
		fuelType.setCode("BENZINA");
		when(fuelTypeRepository.findByCodeIgnoreCase("BENZINA")).thenReturn(Optional.of(fuelType));

		StationPrice current = new StationPrice();
		current.setId(99L);
		current.setPrice(new BigDecimal("1.789"));
		current.setCommunicatedAt(java.time.LocalDateTime.of(2026, 9, 22, 8, 30));
		when(stationPriceRepository.findByStationIdAndFuelTypeIdAndSelfService(10L, 3L, true))
				.thenReturn(Optional.of(current));

		MimitImportResult result = importService.importData();

		assertThat(result.pricesUpdated()).isEqualTo(1);
		assertThat(result.historyInserted()).isZero();
	}
}
