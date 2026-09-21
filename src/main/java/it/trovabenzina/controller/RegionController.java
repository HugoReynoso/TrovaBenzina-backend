package it.trovabenzina.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.trovabenzina.dto.RegionResponseDto;
import it.trovabenzina.service.RegionService;

@RestController
@RequestMapping("/api/regions")
public class RegionController {

	private final RegionService regionService;

	public RegionController(RegionService regionService) {
		this.regionService = regionService;
	}

	@GetMapping
	public List<RegionResponseDto> findAll() {
		return regionService.findAll();
	}
}
