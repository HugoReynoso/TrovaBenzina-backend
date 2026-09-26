package it.trovabenzina.service;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.trovabenzina.dto.StationResponseDto;
import it.trovabenzina.entity.City;
import it.trovabenzina.entity.Station;
import it.trovabenzina.entity.StationPrice;
import it.trovabenzina.exception.CityNotFoundException;
import it.trovabenzina.exception.ProvinceNotFoundException;
import it.trovabenzina.exception.StationNotFoundException;
import it.trovabenzina.mapper.StationMapper;
import it.trovabenzina.repository.CityRepository;
import it.trovabenzina.repository.FuelTypeRepository;
import it.trovabenzina.repository.ProvinceRepository;
import it.trovabenzina.repository.StationPriceRepository;
import it.trovabenzina.repository.StationRepository;

@Service
public class StationService {

	private final StationRepository stationRepository;
	private final StationPriceRepository stationPriceRepository;
	private final CityRepository cityRepository;
	private final ProvinceRepository provinceRepository;
	private final FuelTypeRepository fuelTypeRepository;

	public StationService(StationRepository stationRepository, StationPriceRepository stationPriceRepository,
			CityRepository cityRepository, ProvinceRepository provinceRepository, FuelTypeRepository fuelTypeRepository) {
		this.stationRepository = stationRepository;
		this.stationPriceRepository = stationPriceRepository;
		this.cityRepository = cityRepository;
		this.provinceRepository = provinceRepository;
		this.fuelTypeRepository = fuelTypeRepository;
	}

	@Transactional(readOnly = true)
	public List<StationResponseDto> findAll(Long cityId, Long provinceId, String fuelType, Boolean selfService,
			Integer limit, Double minLat, Double maxLat, Double minLng, Double maxLng) {
		if (cityId == null && provinceId == null && !hasBounds(minLat, maxLat, minLng, maxLng)) {
			return List.of();
		}
		Long effectiveProvinceId = cityId == null ? provinceId : null;
		validateProvince(effectiveProvinceId);
		String normalizedFuelType = normalizeAndValidateFuelType(fuelType);
		int safeLimit = limit == null ? 250 : Math.max(1, Math.min(limit, 1500));
		List<Station> stations = stationRepository.findStations(cityId, effectiveProvinceId, normalizedFuelType,
				selfService, minLat, maxLat, minLng, maxLng, PageRequest.of(0, safeLimit));
		return mapWithPrices(stations, normalizedFuelType, selfService);
	}

	@Transactional(readOnly = true)
	public StationResponseDto findById(Long id) {
		Station station = stationRepository.findDetailsById(id).orElseThrow(() -> new StationNotFoundException(id));
		List<StationPrice> prices = stationPriceRepository.findByStationIdIn(List.of(station.getId()));
		return StationMapper.toDto(station, prices);
	}

	@Transactional(readOnly = true)
	public List<StationResponseDto> findCheapest(Long cityId, Long provinceId, String fuelType, Boolean selfService,
			Integer limit) {
		if (fuelType == null || fuelType.isBlank()) {
			throw new IllegalArgumentException("fuelType is required for cheapest stations");
		}
		Long effectiveProvinceId = cityId == null ? provinceId : null;
		validateProvince(effectiveProvinceId);
		String normalizedFuelType = normalizeAndValidateFuelType(fuelType);
		int safeLimit = limit == null ? 10 : Math.max(1, Math.min(limit, 200));
		List<Station> stations = stationRepository.findCheapest(cityId, effectiveProvinceId, normalizedFuelType,
				selfService, PageRequest.of(0, safeLimit));
		return mapWithPrices(distinctById(stations), normalizedFuelType, selfService);
	}

	@Transactional(readOnly = true)
	public List<StationResponseDto> findNearby(Double lat, Double lng, Long cityId, Double radiusKm, String fuelType,
			Boolean selfService, Integer limit) {
		return findNearby(lat, lng, cityId, null, null, radiusKm, fuelType, selfService, limit);
	}

	@Transactional(readOnly = true)
	public List<StationResponseDto> findNearby(Double lat, Double lng, Long cityId, String cityName, String province,
			Double radiusKm, String fuelType, Boolean selfService, Integer limit) {
		SearchCenter center = resolveSearchCenter(lat, lng, cityId, cityName, province);
		double safeRadiusKm = radiusKm == null ? 10.0 : Math.max(0.1, Math.min(radiusKm, 100.0));
		int safeLimit = limit == null ? 250 : Math.max(1, Math.min(limit, 800));
		String normalizedFuelType = normalizeAndValidateFuelType(fuelType);

		List<Station> cityStations = findExactCityStations(center, normalizedFuelType, selfService);
		if (cityStations.size() >= safeLimit) {
			Map<Long, Double> distancesByStation = distancesByStation(cityStations, center.lat(), center.lng());
			return mapWithPricesAndDistances(cityStations.stream()
					.sorted(Comparator.comparing(station -> distancesByStation.get(station.getId())))
					.limit(safeLimit)
					.toList(), normalizedFuelType, selfService, distancesByStation);
		}

		Map<Long, Double> distancesByStation = new HashMap<>();
		List<Station> stations = stationRepository.findNearbyCandidates(normalizedFuelType, selfService).stream()
				.filter(this::hasCoordinates)
				.filter(station -> {
					double distanceKm = distanceKm(center.lat(), center.lng(), station.getLatitude(), station.getLongitude());
					if (distanceKm <= safeRadiusKm) {
						distancesByStation.put(station.getId(), roundDistance(distanceKm));
						return true;
					}
					return false;
				})
				.sorted(Comparator.comparing(station -> distancesByStation.get(station.getId())))
				.limit(safeLimit)
				.toList();
		return mapWithPricesAndDistances(stations, normalizedFuelType, selfService, distancesByStation);
	}

	private List<StationResponseDto> mapWithPrices(List<Station> stations, String fuelType, Boolean selfService) {
		return mapWithPricesAndDistances(stations, fuelType, selfService, Map.of());
	}

	private List<StationResponseDto> mapWithPricesAndDistances(List<Station> stations, String fuelType,
			Boolean selfService, Map<Long, Double> distancesByStation) {
		if (stations.isEmpty()) {
			return List.of();
		}
		List<Long> stationIds = stations.stream().map(Station::getId).toList();
		Map<Long, List<StationPrice>> pricesByStation = stationPriceRepository.findByStationIdIn(stationIds).stream()
				.filter(price -> fuelType == null || price.getFuelType().getCode().equalsIgnoreCase(fuelType))
				.filter(price -> selfService == null || price.getSelfService().equals(selfService))
				.collect(Collectors.groupingBy(price -> price.getStation().getId()));
		return stations.stream()
				.map(station -> StationMapper.toDto(station, pricesByStation.getOrDefault(station.getId(), List.of()),
						distancesByStation.get(station.getId())))
				.toList();
	}

	private String normalizeFuelType(String fuelType) {
		return fuelType == null || fuelType.isBlank() ? null : fuelType.trim();
	}

	private String normalizeAndValidateFuelType(String fuelType) {
		String normalizedFuelType = normalizeFuelType(fuelType);
		if (normalizedFuelType != null && fuelTypeRepository.findByCodeIgnoreCase(normalizedFuelType).isEmpty()) {
			throw new IllegalArgumentException("Invalid fuelType " + normalizedFuelType);
		}
		return normalizedFuelType;
	}

	private void validateProvince(Long provinceId) {
		if (provinceId != null && !provinceRepository.existsById(provinceId)) {
			throw new ProvinceNotFoundException(provinceId);
		}
	}

	private boolean hasBounds(Double minLat, Double maxLat, Double minLng, Double maxLng) {
		return minLat != null || maxLat != null || minLng != null || maxLng != null;
	}

	private List<Station> distinctById(List<Station> stations) {
		Map<Long, Station> uniqueStations = new LinkedHashMap<>();
		for (Station station : stations) {
			uniqueStations.putIfAbsent(station.getId(), station);
		}
		return uniqueStations.values().stream().toList();
	}

	private SearchCenter resolveSearchCenter(Double lat, Double lng, Long cityId, String cityName, String province) {
		if (lat != null || lng != null) {
			if (lat == null || lng == null) {
				throw new IllegalArgumentException("lat and lng must be provided together");
			}
			return new SearchCenter(lat, lng, null, null, null);
		}
		City city = resolveCity(cityId, cityName, province);
		if (city.getLatitude() == null || city.getLongitude() == null) {
			throw new IllegalArgumentException("City has no coordinates");
		}
		return new SearchCenter(city.getLatitude(), city.getLongitude(), city.getId(), city.getName(),
				city.getProvince() == null ? null : city.getProvince().getCode());
	}

	private City resolveCity(Long cityId, String cityName, String province) {
		if (cityId != null) {
			return cityRepository.findById(cityId).orElseThrow(() -> new CityNotFoundException(cityId));
		}
		if (cityName == null || cityName.isBlank()) {
			throw new IllegalArgumentException("Either cityId, cityName or lat/lng is required");
		}
		return cityRepository.findByNameAndOptionalProvince(cityName.trim(), normalizeSearchText(province)).stream()
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("City not found with name " + cityName));
	}

	private String normalizeSearchText(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private List<Station> findExactCityStations(SearchCenter center, String fuelType, Boolean selfService) {
		if (center.cityId() == null) {
			return List.of();
		}
		List<Station> stations = new ArrayList<>(stationRepository.findStations(center.cityId(), null, fuelType,
				selfService, null, null, null, null, PageRequest.of(0, 800)));
		if (center.cityName() != null && center.provinceCode() != null) {
			stations.addAll(stationRepository.findStationsByMunicipalityAndProvinceCode(center.cityName(),
					center.provinceCode(), fuelType, selfService));
		}
		return distinctById(stations).stream().filter(this::hasCoordinates).toList();
	}

	private Map<Long, Double> distancesByStation(List<Station> stations, double lat, double lng) {
		return stations.stream().collect(Collectors.toMap(Station::getId,
				station -> roundDistance(distanceKm(lat, lng, station.getLatitude(), station.getLongitude())),
				(left, right) -> left));
	}

	private boolean hasCoordinates(Station station) {
		return station.getLatitude() != null && station.getLongitude() != null;
	}

	private double distanceKm(double lat1, double lng1, double lat2, double lng2) {
		double earthRadiusKm = 6371.0088;
		double latDistance = Math.toRadians(lat2 - lat1);
		double lngDistance = Math.toRadians(lng2 - lng1);
		double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
						* Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return earthRadiusKm * c;
	}

	private double roundDistance(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private record SearchCenter(double lat, double lng, Long cityId, String cityName, String provinceCode) {
	}
}
