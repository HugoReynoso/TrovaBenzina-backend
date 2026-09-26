package it.trovabenzina.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.trovabenzina.dto.StationResponseDto;
import it.trovabenzina.service.StationService;

@RestController
@RequestMapping("/api/stations")
public class StationController {

	private final StationService stationService;

	public StationController(StationService stationService) {
		this.stationService = stationService;
	}

	@GetMapping
	public List<StationResponseDto> findAll(@RequestParam(required = false) Long cityId,
			@RequestParam(required = false) Long provinceId, @RequestParam(required = false) String fuelType,
			@RequestParam(required = false) Boolean selfService, @RequestParam(required = false) Integer limit,
			@RequestParam(required = false) Double minLat, @RequestParam(required = false) Double maxLat,
			@RequestParam(required = false) Double minLng, @RequestParam(required = false) Double maxLng) {
		return stationService.findAll(cityId, provinceId, fuelType, selfService, limit, minLat, maxLat, minLng, maxLng);
	}

	@GetMapping("/nearby")
	public List<StationResponseDto> nearby(@RequestParam(required = false) Double lat,
			@RequestParam(required = false) Double lng, @RequestParam(required = false) Long cityId,
			@RequestParam(required = false) String cityName, @RequestParam(required = false) String city,
			@RequestParam(required = false) String province, @RequestParam(required = false) String provinceCode,
			@RequestParam(required = false) String provinceName,
			@RequestParam(required = false) Double radiusKm, @RequestParam(required = false) String fuelType,
			@RequestParam(required = false) Boolean selfService, @RequestParam(required = false) Integer limit) {
		return stationService.findNearby(lat, lng, cityId, firstPresent(cityName, city),
				firstPresent(province, provinceCode, provinceName), radiusKm, fuelType, selfService, limit);
	}

	@GetMapping("/{id:\\d+}")
	public StationResponseDto findById(@PathVariable Long id) {
		return stationService.findById(id);
	}

	@GetMapping("/cheapest")
	public List<StationResponseDto> cheapest(@RequestParam(required = false) Long cityId,
			@RequestParam(required = false) Long provinceId, @RequestParam String fuelType,
			@RequestParam(required = false) Boolean selfService, @RequestParam(required = false) Integer limit) {
		return stationService.findCheapest(cityId, provinceId, fuelType, selfService, limit);
	}

	private String firstPresent(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}
}
