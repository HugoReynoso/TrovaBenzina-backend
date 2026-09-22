package it.trovabenzina.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.trovabenzina.dto.AdminLogResponseDto;
import it.trovabenzina.dto.PriceReportResponseDto;
import it.trovabenzina.service.AdminLogService;
import it.trovabenzina.service.PriceReportService;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

	private final PriceReportService priceReportService;
	private final AdminLogService adminLogService;

	public AdminController(PriceReportService priceReportService, AdminLogService adminLogService) {
		this.priceReportService = priceReportService;
		this.adminLogService = adminLogService;
	}

	@GetMapping("/price-reports")
	public List<PriceReportResponseDto> priceReports() {
		return priceReportService.findAll();
	}

	@PatchMapping("/price-reports/{id}/approve")
	public PriceReportResponseDto approve(@PathVariable Long id) {
		return priceReportService.approve(id);
	}

	@PatchMapping("/price-reports/{id}/reject")
	public PriceReportResponseDto reject(@PathVariable Long id) {
		return priceReportService.reject(id);
	}

	@GetMapping("/logs")
	public List<AdminLogResponseDto> logs() {
		return adminLogService.findLatest();
	}
}
