package it.trovabenzina.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.trovabenzina.dto.ProvinceResponseDto;
import it.trovabenzina.service.ProvinceService;

@RestController
@RequestMapping("/api/provinces")
public class ProvinceController {

	private final ProvinceService provinceService;

	public ProvinceController(ProvinceService provinceService) {
		this.provinceService = provinceService;
	}

	@GetMapping
	public List<ProvinceResponseDto> findAll(@RequestParam(required = false) Long regionId) {
		return provinceService.findAll(regionId);
	}
}
