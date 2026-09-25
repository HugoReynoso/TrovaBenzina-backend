package it.trovabenzina.service;

import java.util.Comparator;
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
import it.trovabenzina.exception.StationNotFoundException;
import it.trovabenzina.mapper.StationMapper;
import it.trovabenzina.repository.CityRepository;
import it.trovabenzina.repository.StationPriceRepository;
import it.trovabenzina.repository.StationRepository;

@Service
public class StationService {

	private final StationRepository stationRepository;
	private final StationPriceRepository stationPriceRepository;
	private final CityRepository cityRepository;

	public StationService(StationRepository stationRepository, StationPriceRepository stationPriceRepository,
			CityRepository cityRepository) {
		this.stationRepository = stationRepository;
		this.stationPriceRepository = stationPriceRepository;
		this.cityRepository = cityRepository;
	}

	@Transactional(readOnly = true)
	public List<StationResponseDto> findAll(Long cityId, String fuelType, Boolean selfService) {
		List<Station> stations = stationRepository.findStations(cityId, normalizeFuelType(fuelType), selfService);
		return mapWithPrices(stations, normalizeFuelType(fuelType), selfService);
	}

	@Transactional(readOnly = true)
	public StationResponseDto findById(Long id) {
		Station station = stationRepository.findDetailsById(id).orElseThrow(() -> new StationNotFoundException(id));
		List<StationPrice> prices = stationPriceRepository.findByStationIdIn(List.of(station.getId()));
		return StationMapper.toDto(station, prices);
	}

	@Transactional(readOnly = true)
	public List<StationResponseDto> findCheapest(Long cityId, String fuelType, Boolean selfService, Integer limit) {
		if (fuelType == null || fuelType.isBlank()) {
			throw new IllegalArgumentException("fuelType is required for cheapest stations");
		}
		int safeLimit = limit == null ? 10 : Math.max(1, Math.min(limit, 100));
		List<Station> stations = stationRepository.findCheapest(cityId, fuelType.trim(), selfService,
				PageRequest.of(0, safeLimit));
		return mapWithPrices(distinctById(stations), fuelType.trim(), selfService);
	}

	@Transactional(readOnly = true)
	public List<StationResponseDto> findNearby(Double lat, Double lng, Long cityId, Double radiusKm, String fuelType,
			Boolean selfService, Integer limit) {
		SearchCenter center = resolveSearchCenter(lat, lng, cityId);
		double safeRadiusKm = radiusKm == null ? 10.0 : Math.max(0.1, Math.min(radiusKm, 100.0));
		int safeLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
		String normalizedFuelType = normalizeFuelType(fuelType);

		List<Station> cityStations = cityId == null ? List.of()
				: stationRepository.findStations(cityId, normalizedFuelType, selfService).stream()
						.filter(this::hasCoordinates)
						.toList();
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

	private List<Station> distinctById(List<Station> stations) {
		Map<Long, Station> uniqueStations = new LinkedHashMap<>();
		for (Station station : stations) {
			uniqueStations.putIfAbsent(station.getId(), station);
		}
		return uniqueStations.values().stream().toList();
	}

	private SearchCenter resolveSearchCenter(Double lat, Double lng, Long cityId) {
		if (lat != null || lng != null) {
			if (lat == null || lng == null) {
				throw new IllegalArgumentException("lat and lng must be provided together");
			}
			return new SearchCenter(lat, lng);
		}
		if (cityId == null) {
			throw new IllegalArgumentException("Either cityId or lat/lng is required");
		}
		City city = cityRepository.findById(cityId).orElseThrow(() -> new CityNotFoundException(cityId));
		if (city.getLatitude() == null || city.getLongitude() == null) {
			throw new IllegalArgumentException("City has no coordinates");
		}
		return new SearchCenter(city.getLatitude(), city.getLongitude());
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

	private record SearchCenter(double lat, double lng) {
	}
}
