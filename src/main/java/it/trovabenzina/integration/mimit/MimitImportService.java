package it.trovabenzina.integration.mimit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import it.trovabenzina.entity.City;
import it.trovabenzina.entity.FuelType;
import it.trovabenzina.entity.Station;
import it.trovabenzina.entity.StationPrice;
import it.trovabenzina.entity.StationPriceHistory;
import it.trovabenzina.repository.CityRepository;
import it.trovabenzina.repository.FuelTypeRepository;
import it.trovabenzina.repository.StationPriceHistoryRepository;
import it.trovabenzina.repository.StationPriceRepository;
import it.trovabenzina.repository.StationRepository;
import it.trovabenzina.service.CityFuelStatisticService;
import jakarta.persistence.EntityManager;

@Service
public class MimitImportService {

	private static final Logger log = LoggerFactory.getLogger(MimitImportService.class);
	private static final int PRICE_BATCH_SIZE = 1000;

	private final MimitProperties properties;
	private final MimitDownloadService downloadService;
	private final MimitCsvParser csvParser;
	private final StationRepository stationRepository;
	private final StationPriceRepository stationPriceRepository;
	private final StationPriceHistoryRepository stationPriceHistoryRepository;
	private final FuelTypeRepository fuelTypeRepository;
	private final CityRepository cityRepository;
	private final CityFuelStatisticService statisticService;
	private final TransactionTemplate transactionTemplate;
	private final EntityManager entityManager;

	public MimitImportService(MimitProperties properties, MimitDownloadService downloadService, MimitCsvParser csvParser,
			StationRepository stationRepository, StationPriceRepository stationPriceRepository,
			StationPriceHistoryRepository stationPriceHistoryRepository, FuelTypeRepository fuelTypeRepository,
			CityRepository cityRepository, CityFuelStatisticService statisticService,
			PlatformTransactionManager transactionManager, EntityManager entityManager) {
		this.properties = properties;
		this.downloadService = downloadService;
		this.csvParser = csvParser;
		this.stationRepository = stationRepository;
		this.stationPriceRepository = stationPriceRepository;
		this.stationPriceHistoryRepository = stationPriceHistoryRepository;
		this.fuelTypeRepository = fuelTypeRepository;
		this.cityRepository = cityRepository;
		this.statisticService = statisticService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.entityManager = entityManager;
	}

	public MimitImportResult importData() {
		ImportState state = new ImportState(Instant.now());
		log.info("Starting full MIMIT import");
		importStationsInto(state);
		importPricesInto(state);
		MimitImportResult result = state.toResult(Instant.now());
		log.info("Completed full MIMIT import: {}", result);
		return result;
	}

	public MimitImportResult importStations() {
		ImportState state = new ImportState(Instant.now());
		log.info("Starting MIMIT stations import");
		importStationsInto(state);
		MimitImportResult result = state.toResult(Instant.now());
		log.info("Completed MIMIT stations import: {}", result);
		return result;
	}

	public MimitImportResult importPrices() {
		ImportState state = new ImportState(Instant.now());
		log.info("Starting MIMIT prices import");
		importPricesInto(state);
		MimitImportResult result = state.toResult(Instant.now());
		log.info("Completed MIMIT prices import: {}", result);
		return result;
	}

	private void importStationsInto(ImportState state) {
		String stationsCsv = downloadService.download(properties.stationsUrl());
		List<MimitStationRecord> stationRecords = csvParser.parseStations(stationsCsv);
		state.stationsRead += stationRecords.size();
		transactionTemplate.executeWithoutResult(status -> upsertStations(stationRecords, state));
	}

	private void importPricesInto(ImportState state) {
		Path pricesFile = null;
		Set<CityFuelPair> cityFuelPairs = new HashSet<>();
		try {
			pricesFile = downloadService.downloadToTempFile(properties.pricesUrl());
			csvParser.parsePrices(pricesFile, PRICE_BATCH_SIZE, batch -> {
				state.pricesRead += batch.size();
				processPriceBatch(batch, state, cityFuelPairs);
			}, state::skipPrice);
			recalculateStatistics(cityFuelPairs, state);
		} finally {
			deleteTempFile(pricesFile);
		}
	}

	private void upsertStations(List<MimitStationRecord> records, ImportState state) {
		if (records.isEmpty()) {
			return;
		}
		Set<String> stationMimitIds = records.stream().map(MimitStationRecord::mimitId).filter(value -> !isBlank(value))
				.map(String::trim).collect(Collectors.toSet());
		Map<String, Station> stationsByMimitId = stationMimitIds.isEmpty() ? new HashMap<>()
				: stationRepository.findByMimitIdIn(stationMimitIds).stream()
						.filter(station -> !isBlank(station.getMimitId()))
						.collect(Collectors.toMap(station -> station.getMimitId().trim(), Function.identity(),
								(left, right) -> left));
		Map<String, City> citiesByNameProvince = findCitiesForStations(records);
		List<Station> stationsToSave = new ArrayList<>();
		for (MimitStationRecord record : records) {
			if (isBlank(record.mimitId())) {
				state.skipStation("Missing station mimitId");
				continue;
			}
			String mimitId = record.mimitId().trim();
			Station station = stationsByMimitId.getOrDefault(mimitId, new Station());
			boolean inserted = station.getId() == null;
			station.setMimitId(mimitId);
			station.setName(valueOrFallback(record.name(), "Impianto " + record.mimitId()));
			station.setBrand(blankToNull(record.brand()));
			station.setAddress(blankToNull(record.address()));
			station.setMunicipality(blankToNull(record.municipality()));
			station.setProvinceCode(normalizeProvinceCode(record.provinceCode()));
			station.setLatitude(record.latitude());
			station.setLongitude(record.longitude());
			station.setActive(true);
			City city = citiesByNameProvince.get(cityKey(record.municipality(), record.provinceCode()));
			if (city != null) {
				station.setCity(city);
			} else {
				state.message("City not found for station " + record.mimitId() + " (" + record.municipality() + ", "
						+ record.provinceCode() + "); station saved without city");
			}
			stationsToSave.add(station);
			stationsByMimitId.put(mimitId, station);
			if (inserted) {
				state.stationsInserted++;
			} else {
				state.stationsUpdated++;
			}
		}
		stationRepository.saveAll(stationsToSave);
		entityManager.flush();
		entityManager.clear();
	}

	private Map<String, City> findCitiesForStations(List<MimitStationRecord> records) {
		Set<String> cityNames = records.stream().map(MimitStationRecord::municipality).filter(value -> !isBlank(value))
				.map(value -> value.trim().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
		Set<String> provinceCodes = records.stream().map(MimitStationRecord::provinceCode).filter(value -> !isBlank(value))
				.map(value -> value.trim().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
		if (cityNames.isEmpty() || provinceCodes.isEmpty()) {
			return Map.of();
		}
		return cityRepository.findByNamesAndProvinceCodes(cityNames, provinceCodes).stream()
				.filter(city -> city.getProvince() != null && !isBlank(city.getProvince().getCode()))
				.collect(Collectors.toMap(city -> cityKey(city.getName(), city.getProvince().getCode()), Function.identity(),
						(left, right) -> left));
	}

	private void processPriceBatch(List<MimitPriceRecord> batch, ImportState state, Set<CityFuelPair> cityFuelPairs) {
		try {
			PriceBatchResult result = transactionTemplate.execute(status -> upsertPriceBatch(batch));
			if (result != null) {
				state.add(result);
				cityFuelPairs.addAll(result.cityFuelPairs());
			}
		} catch (RuntimeException ex) {
			state.errors++;
			state.pricesSkipped += batch.size();
			state.message("MIMIT price batch failed and was rolled back: " + ex.getMessage());
			log.error("MIMIT price batch failed and was rolled back", ex);
		}
	}

	private PriceBatchResult upsertPriceBatch(List<MimitPriceRecord> records) {
		PriceBatchAccumulator result = new PriceBatchAccumulator();
		Map<String, Station> stationsByMimitId = findStationsForPriceBatch(records);
		Map<String, FuelType> fuelTypesByCode = loadFuelTypesByCode();
		List<PriceRow> rows = new ArrayList<>(records.size());

		for (MimitPriceRecord record : records) {
			if (isBlank(record.stationMimitId()) || isBlank(record.fuelTypeCode()) || record.price() == null
					|| record.price().signum() <= 0) {
				result.pricesSkipped++;
				continue;
			}
			Station station = stationsByMimitId.get(record.stationMimitId().trim());
			if (station == null) {
				result.pricesSkipped++;
				continue;
			}
			FuelType fuelType = resolveFuelType(record, fuelTypesByCode);
			rows.add(new PriceRow(record, station, fuelType));
		}
		if (rows.isEmpty()) {
			entityManager.clear();
			return result.toResult();
		}

		Set<Long> stationIds = rows.stream().map(row -> row.station().getId()).collect(Collectors.toSet());
		Set<Long> fuelTypeIds = rows.stream().map(row -> row.fuelType().getId()).collect(Collectors.toSet());
		Map<PriceKey, StationPrice> currentPrices = stationPriceRepository
				.findCurrentByStationIdInAndFuelTypeIdIn(stationIds, fuelTypeIds).stream()
				.collect(Collectors.toMap(price -> new PriceKey(price.getStation().getId(), price.getFuelType().getId(),
						Boolean.TRUE.equals(price.getSelfService())), Function.identity(), (left, right) -> left,
						LinkedHashMap::new));

		List<StationPrice> pricesToSave = new ArrayList<>();
		List<StationPriceHistory> historiesToSave = new ArrayList<>();
		Set<PriceKey> pricesToSaveKeys = new HashSet<>();
		LocalDateTime importedAt = LocalDateTime.now();
		for (PriceRow row : rows) {
			MimitPriceRecord record = row.record();
			Station station = row.station();
			FuelType fuelType = row.fuelType();
			PriceKey priceKey = new PriceKey(station.getId(), fuelType.getId(), Boolean.TRUE.equals(record.selfService()));
			StationPrice price = currentPrices.getOrDefault(priceKey, new StationPrice());
			boolean inserted = price.getId() == null && !pricesToSaveKeys.contains(priceKey);
			boolean changed = inserted || hasPriceChanged(price, record);

			price.setStation(station);
			price.setFuelType(fuelType);
			price.setPrice(record.price());
			price.setSelfService(Boolean.TRUE.equals(record.selfService()));
			price.setCommunicatedAt(record.communicatedAt());
			price.setImportedAt(importedAt);
			if (pricesToSaveKeys.add(priceKey)) {
				pricesToSave.add(price);
				if (inserted) {
					result.pricesInserted++;
				} else {
					result.pricesUpdated++;
				}
			}
			currentPrices.put(priceKey, price);

			if (changed) {
				historiesToSave.add(toHistory(station, fuelType, record, importedAt));
				result.historyInserted++;
			}
			if (station.getCity() != null) {
				result.cityFuelPairs.add(new CityFuelPair(station.getCity().getId(), fuelType.getCode()));
			}
		}
		stationPriceRepository.saveAll(pricesToSave);
		stationPriceHistoryRepository.saveAll(historiesToSave);
		entityManager.flush();
		entityManager.clear();
		return result.toResult();
	}

	private Map<String, Station> findStationsForPriceBatch(List<MimitPriceRecord> records) {
		Set<String> stationMimitIds = records.stream().map(MimitPriceRecord::stationMimitId).filter(value -> !isBlank(value))
				.map(String::trim).collect(Collectors.toSet());
		if (stationMimitIds.isEmpty()) {
			return Map.of();
		}
		return stationRepository.findByMimitIdIn(stationMimitIds).stream()
				.filter(station -> !isBlank(station.getMimitId()))
				.collect(Collectors.toMap(station -> station.getMimitId().trim(), Function.identity(), (left, right) -> left));
	}

	private Map<String, FuelType> loadFuelTypesByCode() {
		return fuelTypeRepository.findAll().stream().filter(fuelType -> !isBlank(fuelType.getCode()))
				.collect(Collectors.toMap(fuelType -> fuelType.getCode().trim().toUpperCase(Locale.ROOT),
						Function.identity(), (left, right) -> left, LinkedHashMap::new));
	}

	private void recalculateStatistics(Set<CityFuelPair> cityFuelPairs, ImportState state) {
		for (CityFuelPair pair : cityFuelPairs) {
			fuelTypeRepository.findByCodeIgnoreCase(pair.fuelTypeCode()).ifPresent(fuelType -> {
				statisticService.recalculate(pair.cityId(), fuelType, LocalDate.now());
				state.statisticsUpdated++;
			});
		}
	}

	private StationPriceHistory toHistory(Station station, FuelType fuelType, MimitPriceRecord record,
			LocalDateTime importedAt) {
		StationPriceHistory history = new StationPriceHistory();
		history.setStation(station);
		history.setFuelType(fuelType);
		history.setPrice(record.price());
		history.setSelfService(Boolean.TRUE.equals(record.selfService()));
		history.setCommunicatedAt(record.communicatedAt());
		history.setImportedAt(importedAt);
		return history;
	}

	private FuelType resolveFuelType(MimitPriceRecord record, Map<String, FuelType> fuelTypesByCode) {
		String code = csvParser.normalizeFuelTypeName(record.fuelTypeCode());
		return fuelTypesByCode.computeIfAbsent(code, key -> {
			FuelType created = new FuelType();
			created.setCode(key);
			created.setName(valueOrFallback(record.fuelTypeName(), key));
			created.setActive(true);
			return fuelTypeRepository.save(created);
		});
	}

	private boolean hasPriceChanged(StationPrice price, MimitPriceRecord record) {
		return price.getPrice() == null || price.getPrice().compareTo(record.price()) != 0
				|| !Objects.equals(price.getCommunicatedAt(), record.communicatedAt());
	}

	private String cityKey(String municipality, String provinceCode) {
		return valueOrFallback(municipality, "").toUpperCase(Locale.ROOT) + "|"
				+ valueOrFallback(provinceCode, "").toUpperCase(Locale.ROOT);
	}

	private String valueOrFallback(String value, String fallback) {
		return isBlank(value) ? fallback : value.trim();
	}

	private String blankToNull(String value) {
		return isBlank(value) ? null : value.trim();
	}

	private String normalizeProvinceCode(String value) {
		return isBlank(value) ? null : value.trim().toUpperCase(Locale.ROOT);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private void deleteTempFile(Path path) {
		if (path == null) {
			return;
		}
		try {
			Files.deleteIfExists(path);
		} catch (IOException ex) {
			log.warn("Unable to delete temporary MIMIT file {}", path, ex);
		}
	}

	private record CityFuelPair(Long cityId, String fuelTypeCode) {
	}

	private record PriceKey(Long stationId, Long fuelTypeId, Boolean selfService) {
	}

	private record PriceRow(MimitPriceRecord record, Station station, FuelType fuelType) {
	}

	private record PriceBatchResult(int pricesInserted, int pricesUpdated, int pricesSkipped, int historyInserted,
			Set<CityFuelPair> cityFuelPairs) {
	}

	private static final class PriceBatchAccumulator {
		private int pricesInserted;
		private int pricesUpdated;
		private int pricesSkipped;
		private int historyInserted;
		private final Set<CityFuelPair> cityFuelPairs = new HashSet<>();

		private PriceBatchResult toResult() {
			return new PriceBatchResult(pricesInserted, pricesUpdated, pricesSkipped, historyInserted,
					Set.copyOf(cityFuelPairs));
		}
	}

	private static final class ImportState {
		private final Instant startedAt;
		private final List<String> messages = new ArrayList<>();
		private int stationsRead;
		private int stationsInserted;
		private int stationsUpdated;
		private int stationsSkipped;
		private int pricesRead;
		private int pricesInserted;
		private int pricesUpdated;
		private int pricesSkipped;
		private int historyInserted;
		private int errors;
		private int statisticsUpdated;

		private ImportState(Instant startedAt) {
			this.startedAt = startedAt;
		}

		private void add(PriceBatchResult result) {
			pricesInserted += result.pricesInserted();
			pricesUpdated += result.pricesUpdated();
			pricesSkipped += result.pricesSkipped();
			historyInserted += result.historyInserted();
		}

		private void skipStation(String reason) {
			stationsSkipped++;
			message(reason);
		}

		private void skipPrice(String reason) {
			pricesSkipped++;
			message(reason);
		}

		private void message(String message) {
			if (messages.size() < 100) {
				messages.add(message);
			}
			if (messages.size() <= 20) {
				log.warn("MIMIT import: {}", message);
			}
		}

		private MimitImportResult toResult(Instant finishedAt) {
			return new MimitImportResult(stationsRead, stationsInserted, stationsUpdated, stationsSkipped, pricesRead,
					pricesInserted, pricesUpdated, pricesSkipped, historyInserted, errors, statisticsUpdated, startedAt,
					finishedAt, List.copyOf(messages));
		}
	}
}
