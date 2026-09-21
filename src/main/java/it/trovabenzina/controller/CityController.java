package it.trovabenzina.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.trovabenzina.dto.CityFuelStatisticHistoryDto;
import it.trovabenzina.dto.CityFuelStatisticResponseDto;
import it.trovabenzina.dto.CityResponseDto;
import it.trovabenzina.service.CityFuelStatisticService;
import it.trovabenzina.service.CityService;

@RestController
@RequestMapping("/api/cities")
public class CityController {

	private final CityService cityService;
	private final CityFuelStatisticService statisticService;

	public CityController(CityService cityService, CityFuelStatisticService statisticService) {
		this.cityService = cityService;
		this.statisticService = statisticService;
	}

	@GetMapping
	public List<CityResponseDto> findAll(@RequestParam(required = false) Long provinceId) {
		return cityService.findAll(provinceId);
	}

	@GetMapping("/{cityId}/fuel-statistics")
	public CityFuelStatisticResponseDto latestStatistic(@PathVariable Long cityId, @RequestParam String fuelType) {
		return statisticService.latest(cityId, fuelType);
	}

	@GetMapping("/{cityId}/fuel-statistics/history")
	public List<CityFuelStatisticHistoryDto> statisticHistory(@PathVariable Long cityId, @RequestParam String fuelType,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return statisticService.history(cityId, fuelType, from, to);
	}
}
