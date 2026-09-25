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
			@RequestParam(required = false) String fuelType, @RequestParam(required = false) Boolean selfService) {
		return stationService.findAll(cityId, fuelType, selfService);
	}

	@GetMapping("/nearby")
	public List<StationResponseDto> nearby(@RequestParam(required = false) Double lat,
			@RequestParam(required = false) Double lng, @RequestParam(required = false) Long cityId,
			@RequestParam(required = false) Double radiusKm, @RequestParam(required = false) String fuelType,
			@RequestParam(required = false) Boolean selfService, @RequestParam(required = false) Integer limit) {
		return stationService.findNearby(lat, lng, cityId, radiusKm, fuelType, selfService, limit);
	}

	@GetMapping("/{id}")
	public StationResponseDto findById(@PathVariable Long id) {
		return stationService.findById(id);
	}

	@GetMapping("/cheapest")
	public List<StationResponseDto> cheapest(@RequestParam(required = false) Long cityId, @RequestParam String fuelType,
			@RequestParam(required = false) Boolean selfService, @RequestParam(required = false) Integer limit) {
		return stationService.findCheapest(cityId, fuelType, selfService, limit);
	}
}
