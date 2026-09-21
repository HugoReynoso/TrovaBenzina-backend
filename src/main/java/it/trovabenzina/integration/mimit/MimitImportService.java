package it.trovabenzina.integration.mimit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.trovabenzina.entity.City;
import it.trovabenzina.entity.FuelType;
import it.trovabenzina.entity.Station;
import it.trovabenzina.entity.StationPrice;
import it.trovabenzina.repository.CityRepository;
import it.trovabenzina.repository.FuelTypeRepository;
import it.trovabenzina.repository.StationPriceRepository;
import it.trovabenzina.repository.StationRepository;
import it.trovabenzina.service.CityFuelStatisticService;

@Service
public class MimitImportService {

	private final MimitProperties properties;
	private final MimitDownloadService downloadService;
	private final MimitCsvParser csvParser;
	private final StationRepository stationRepository;
	private final StationPriceRepository stationPriceRepository;
	private final FuelTypeRepository fuelTypeRepository;
	private final CityRepository cityRepository;
	private final CityFuelStatisticService statisticService;

	public MimitImportService(MimitProperties properties, MimitDownloadService downloadService, MimitCsvParser csvParser,
			StationRepository stationRepository, StationPriceRepository stationPriceRepository,
			FuelTypeRepository fuelTypeRepository, CityRepository cityRepository,
			CityFuelStatisticService statisticService) {
		this.properties = properties;
		this.downloadService = downloadService;
		this.csvParser = csvParser;
		this.stationRepository = stationRepository;
		this.stationPriceRepository = stationPriceRepository;
		this.fuelTypeRepository = fuelTypeRepository;
		this.cityRepository = cityRepository;
		this.statisticService = statisticService;
	}

	@Transactional
	public MimitImportResult importData() {
		String stationsCsv = downloadService.download(properties.stationsUrl());
		String pricesCsv = downloadService.download(properties.pricesUrl());

		int stationsImported = upsertStations(stationsCsv);
		ImportPriceResult priceResult = upsertPrices(pricesCsv);
		priceResult.cityFuelPairs().forEach(pair -> statisticService.recalculate(pair.cityId(), pair.fuelType(), LocalDate.now()));
		return new MimitImportResult(stationsImported, priceResult.count(), priceResult.cityFuelPairs().size());
	}

	private int upsertStations(String csv) {
		int count = 0;
		for (MimitStationRecord record : csvParser.parseStations(csv)) {
			if (record.mimitId() == null) {
				continue;
			}
			Station station = stationRepository.findByMimitId(record.mimitId()).orElseGet(Station::new);
			station.setMimitId(record.mimitId());
			station.setName(valueOrFallback(record.name(), "Impianto " + record.mimitId()));
			station.setBrand(record.brand());
			station.setAddress(record.address());
			station.setMunicipality(record.municipality());
			station.setProvinceCode(record.provinceCode());
			station.setLatitude(record.latitude());
			station.setLongitude(record.longitude());
			if (record.municipality() != null && record.provinceCode() != null) {
				cityRepository.findFirstByNameIgnoreCaseAndProvinceCodeIgnoreCase(record.municipality(), record.provinceCode())
						.ifPresent(station::setCity);
			}
			stationRepository.save(station);
			count++;
		}
		return count;
	}

	private ImportPriceResult upsertPrices(String csv) {
		int count = 0;
		Set<CityFuelPair> cityFuelPairs = new HashSet<>();
		for (MimitPriceRecord record : csvParser.parsePrices(csv)) {
			if (record.stationMimitId() == null || record.fuelTypeCode() == null || record.price() == null) {
				continue;
			}
			Station station = stationRepository.findByMimitId(record.stationMimitId()).orElse(null);
			if (station == null) {
				continue;
			}
			FuelType fuelType = fuelTypeRepository.findByCodeIgnoreCase(record.fuelTypeCode()).orElseGet(() -> {
				FuelType created = new FuelType();
				created.setCode(record.fuelTypeCode());
				created.setName(valueOrFallback(record.fuelTypeName(), record.fuelTypeCode()));
				created.setActive(true);
				return fuelTypeRepository.save(created);
			});
			StationPrice price = stationPriceRepository
					.findByStationIdAndFuelTypeIdAndSelfService(station.getId(), fuelType.getId(), record.selfService())
					.orElseGet(StationPrice::new);
			price.setStation(station);
			price.setFuelType(fuelType);
			price.setPrice(record.price());
			price.setSelfService(record.selfService());
			price.setCommunicatedAt(record.communicatedAt());
			price.setImportedAt(LocalDateTime.now());
			stationPriceRepository.save(price);
			if (station.getCity() != null) {
				cityFuelPairs.add(new CityFuelPair(station.getCity().getId(), fuelType));
			}
			count++;
		}
		return new ImportPriceResult(count, cityFuelPairs);
	}

	private String valueOrFallback(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private record ImportPriceResult(int count, Set<CityFuelPair> cityFuelPairs) {
	}

	private record CityFuelPair(Long cityId, FuelType fuelType) {
	}
}
