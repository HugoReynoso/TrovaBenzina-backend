package it.trovabenzina.controller;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import it.trovabenzina.exception.MimitImportException;
import it.trovabenzina.integration.mimit.MimitImportResult;
import it.trovabenzina.integration.mimit.MimitImportService;
import it.trovabenzina.service.FrontendRebuildService;

@RestController
@RequestMapping("/api/admin/mimit")
public class AdminMimitController {
	private static final Logger log = LoggerFactory.getLogger(AdminMimitController.class);

	private final MimitImportService importService;
	private final FrontendRebuildService frontendRebuildService;

	public AdminMimitController(MimitImportService importService, FrontendRebuildService frontendRebuildService) {
		this.importService = importService;
		this.frontendRebuildService = frontendRebuildService;
	}

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

	@PostMapping(value = "/import/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public MimitImportResult uploadData(@RequestPart("stations") MultipartFile stations,
			@RequestPart("prices") MultipartFile prices) {
		return importUploads(stations, prices);
	}

	@PostMapping(value = "/import/prices/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public MimitImportResult uploadPrices(@RequestPart("prices") MultipartFile prices) {
		return importUploads(null, prices);
	}

	private MimitImportResult importUploads(MultipartFile stations, MultipartFile prices) {
		if (prices.isEmpty() || stations != null && stations.isEmpty()) {
			throw new MimitImportException("Uploaded MIMIT CSV files must not be empty");
		}
		Path stationsPath = null;
		Path pricesPath = null;
		try {
			if (stations != null) {
				stationsPath = saveUpload(stations);
			}
			pricesPath = saveUpload(prices);
			MimitImportResult result = stationsPath == null ? importService.importUploadedPrices(pricesPath)
					: importService.importUploadedData(stationsPath, pricesPath);
			frontendRebuildService.triggerRebuild("uploaded MIMIT import");
			return result;
		} finally {
			deleteUpload(stationsPath);
			deleteUpload(pricesPath);
		}
	}

	private Path saveUpload(MultipartFile file) {
		Path path = null;
		try {
			path = Files.createTempFile("mimit-upload-", ".csv");
			try (InputStream input = file.getInputStream()) {
				Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING);
			}
			return path;
		} catch (IOException ex) {
			deleteUpload(path);
			throw new UncheckedIOException("Unable to store uploaded MIMIT CSV", ex);
		}
	}

	private void deleteUpload(Path path) {
		if (path != null) {
			try {
				Files.deleteIfExists(path);
			} catch (IOException ex) {
				log.warn("Unable to delete uploaded MIMIT file {}", path, ex);
			}
		}
	}
}
