package it.trovabenzina.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.trovabenzina.dto.PriceReportCreateDto;
import it.trovabenzina.dto.PriceReportResponseDto;
import it.trovabenzina.service.PriceReportService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/price-reports")
public class PriceReportController {

	private final PriceReportService priceReportService;

	public PriceReportController(PriceReportService priceReportService) {
		this.priceReportService = priceReportService;
	}

	@PostMapping
	public PriceReportResponseDto create(@Valid @RequestBody PriceReportCreateDto request) {
		return priceReportService.create(request);
	}
}
