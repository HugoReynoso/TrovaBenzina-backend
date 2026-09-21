package it.trovabenzina.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.trovabenzina.integration.mimit.MimitImportResult;
import it.trovabenzina.integration.mimit.MimitImportService;

@RestController
@RequestMapping("/api/admin/mimit")
public class AdminMimitController {

	private final MimitImportService importService;

	public AdminMimitController(MimitImportService importService) {
		this.importService = importService;
	}

	// TODO: proteggere endpoint admin prima della produzione.
	@PostMapping("/import")
	public MimitImportResult importMimitData() {
		return importService.importData();
	}
}
