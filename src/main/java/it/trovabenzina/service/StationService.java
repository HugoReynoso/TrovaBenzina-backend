package it.trovabenzina.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.trovabenzina.dto.StationResponseDto;
import it.trovabenzina.entity.Station;
import it.trovabenzina.entity.StationPrice;
import it.trovabenzina.exception.StationNotFoundException;
import it.trovabenzina.mapper.StationMapper;
import it.trovabenzina.repository.StationPriceRepository;
import it.trovabenzina.repository.StationRepository;

@Service
public class StationService {

	private final StationRepository stationRepository;
	private final StationPriceRepository stationPriceRepository;

	public StationService(StationRepository stationRepository, StationPriceRepository stationPriceRepository) {
		this.stationRepository = stationRepository;
		this.stationPriceRepository = stationPriceRepository;
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
		return mapWithPrices(stations, fuelType.trim(), selfService);
	}

	private List<StationResponseDto> mapWithPrices(List<Station> stations, String fuelType, Boolean selfService) {
		if (stations.isEmpty()) {
			return List.of();
		}
		List<Long> stationIds = stations.stream().map(Station::getId).toList();
		Map<Long, List<StationPrice>> pricesByStation = stationPriceRepository.findByStationIdIn(stationIds).stream()
				.filter(price -> fuelType == null || price.getFuelType().getCode().equalsIgnoreCase(fuelType))
				.filter(price -> selfService == null || price.getSelfService().equals(selfService))
				.collect(Collectors.groupingBy(price -> price.getStation().getId()));
		return stations.stream()
				.map(station -> StationMapper.toDto(station, pricesByStation.getOrDefault(station.getId(), List.of())))
				.toList();
	}

	private String normalizeFuelType(String fuelType) {
		return fuelType == null || fuelType.isBlank() ? null : fuelType.trim();
	}
}
