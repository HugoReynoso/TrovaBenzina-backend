package it.trovabenzina.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.trovabenzina.integration.mimit.MimitImportResult;
import it.trovabenzina.integration.mimit.MimitImportService;
import it.trovabenzina.service.FrontendRebuildService;

@RestController
@RequestMapping("/api/admin/mimit")
public class AdminMimitController {

	private final MimitImportService importService;
	private final FrontendRebuildService frontendRebuildService;

	public AdminMimitController(MimitImportService importService, FrontendRebuildService frontendRebuildService) {
		this.importService = importService;
		this.frontendRebuildService = frontendRebuildService;
	}

	// TODO: proteggere endpoint admin prima della produzione.
	@PostMapping("/import")
	public MimitImportResult importMimitData() {
		MimitImportResult result = importService.importData();
		frontendRebuildService.triggerRebuild("admin MIMIT import");
		return result;
	}

	@PostMapping("/import/stations")
	public MimitImportResult importMimitStations() {
		return importService.importStations();
	}

	@PostMapping("/import/prices")
	public MimitImportResult importMimitPrices() {
		MimitImportResult result = importService.importPrices();
		frontendRebuildService.triggerRebuild("admin MIMIT price import");
		return result;
	}
}
