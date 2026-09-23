package it.trovabenzina.integration.mimit;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
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

@Service
public class MimitImportService {

	private static final Logger log = LoggerFactory.getLogger(MimitImportService.class);

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

	public MimitImportService(MimitProperties properties, MimitDownloadService downloadService, MimitCsvParser csvParser,
			StationRepository stationRepository, StationPriceRepository stationPriceRepository,
			StationPriceHistoryRepository stationPriceHistoryRepository, FuelTypeRepository fuelTypeRepository,
			CityRepository cityRepository, CityFuelStatisticService statisticService,
			PlatformTransactionManager transactionManager) {
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
	}

	public MimitImportResult importData() {
		Instant startedAt = Instant.now();
		log.info("Starting MIMIT import");

		String stationsCsv = downloadService.download(properties.stationsUrl());
		List<MimitStationRecord> stationRecords = csvParser.parseStations(stationsCsv);
		String pricesCsv = downloadService.download(properties.pricesUrl());
		List<MimitPriceRecord> priceRecords = csvParser.parsePrices(pricesCsv);

		ImportState state = new ImportState(startedAt);
		state.stationsRead = stationRecords.size();
		state.pricesRead = priceRecords.size();

		transactionTemplate.executeWithoutResult(status -> upsertStations(stationRecords, state));
		Set<CityFuelPair> cityFuelPairs = transactionTemplate.execute(status -> upsertPrices(priceRecords, state));
		if (cityFuelPairs != null) {
			for (CityFuelPair pair : cityFuelPairs) {
				statisticService.recalculate(pair.cityId(), pair.fuelType(), LocalDate.now());
				state.statisticsUpdated++;
			}
		}

		MimitImportResult result = state.toResult(Instant.now());
		log.info("Completed MIMIT import: {}", result);
		return result;
	}

	private void upsertStations(List<MimitStationRecord> records, ImportState state) {
		Map<String, Station> stationsByMimitId = stationRepository.findAll().stream()
				.filter(station -> !isBlank(station.getMimitId()))
				.collect(Collectors.toMap(station -> station.getMimitId().trim(), Function.identity(), (left, right) -> left));
		Map<String, City> citiesByNameProvince = cityRepository.findAllByOrderByNameAsc().stream()
				.filter(city -> city.getProvince() != null && !isBlank(city.getProvince().getCode()))
				.collect(Collectors.toMap(city -> cityKey(city.getName(), city.getProvince().getCode()), Function.identity(),
						(left, right) -> left));
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
				state.message(
					"City not found for station " + record.mimitId() + " (" + record.municipality() + ", "
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
	}

	private Set<CityFuelPair> upsertPrices(List<MimitPriceRecord> records, ImportState state) {
		Set<CityFuelPair> cityFuelPairs = new HashSet<>();
		Map<String, Station> stationsByMimitId = stationRepository.findAll().stream()
				.filter(station -> !isBlank(station.getMimitId()))
				.collect(Collectors.toMap(station -> station.getMimitId().trim(), Function.identity(), (left, right) -> left));
		Map<String, FuelType> fuelTypesByCode = fuelTypeRepository.findAll().stream()
				.filter(fuelType -> !isBlank(fuelType.getCode()))
				.collect(Collectors.toMap(fuelType -> fuelType.getCode().trim().toUpperCase(Locale.ROOT),
						Function.identity(), (left, right) -> left));
		Map<PriceKey, StationPrice> currentPrices = stationPriceRepository.findAll().stream()
				.filter(price -> price.getStation() != null && price.getStation().getId() != null
						&& price.getFuelType() != null && price.getFuelType().getId() != null)
				.collect(Collectors.toMap(price -> new PriceKey(price.getStation().getId(), price.getFuelType().getId(),
						Boolean.TRUE.equals(price.getSelfService())), Function.identity(), (left, right) -> left));
		List<StationPrice> pricesToSave = new ArrayList<>();
		List<StationPriceHistory> historiesToSave = new ArrayList<>();
		Set<PriceKey> pricesToSaveKeys = new HashSet<>();
		LocalDateTime importedAt = LocalDateTime.now();
		for (MimitPriceRecord record : records) {
			if (isBlank(record.stationMimitId()) || isBlank(record.fuelTypeCode()) || record.price() == null
					|| record.price().signum() <= 0) {
				state.skipPrice("Missing station, fuel or valid price");
				continue;
			}
			Station station = stationsByMimitId.get(record.stationMimitId().trim());
			if (station == null) {
				state.skipPrice("Station not found for MIMIT id " + record.stationMimitId());
				continue;
			}
			FuelType fuelType = resolveFuelType(record, fuelTypesByCode);
			PriceKey priceKey = new PriceKey(station.getId(), fuelType.getId(), Boolean.TRUE.equals(record.selfService()));
			StationPrice price = currentPrices.getOrDefault(priceKey, new StationPrice());
			boolean inserted = price.getId() == null;
			boolean changed = inserted || hasPriceChanged(price, record);

			price.setStation(station);
			price.setFuelType(fuelType);
			price.setPrice(record.price());
			price.setSelfService(Boolean.TRUE.equals(record.selfService()));
			price.setCommunicatedAt(record.communicatedAt());
			price.setImportedAt(importedAt);
			if (pricesToSaveKeys.add(priceKey)) {
				pricesToSave.add(price);
			}
			currentPrices.put(priceKey, price);

			if (changed) {
				historiesToSave.add(toHistory(station, fuelType, record, importedAt));
				state.historyInserted++;
			}
			if (station.getCity() != null) {
				cityFuelPairs.add(new CityFuelPair(station.getCity().getId(), fuelType));
			}
			if (inserted) {
				state.pricesInserted++;
			} else {
				state.pricesUpdated++;
			}
		}
		stationPriceRepository.saveAll(pricesToSave);
		stationPriceHistoryRepository.saveAll(historiesToSave);
		return cityFuelPairs;
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

	private record CityFuelPair(Long cityId, FuelType fuelType) {
	}

	private record PriceKey(Long stationId, Long fuelTypeId, Boolean selfService) {
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
