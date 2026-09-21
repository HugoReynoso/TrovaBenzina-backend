package it.trovabenzina.integration.mimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MimitImportScheduler {

	private static final Logger log = LoggerFactory.getLogger(MimitImportScheduler.class);

	private final MimitProperties properties;
	private final MimitImportService importService;

	public MimitImportScheduler(MimitProperties properties, MimitImportService importService) {
		this.properties = properties;
		this.importService = importService;
	}

	@Scheduled(cron = "${mimit.import.cron}")
	public void scheduledImport() {
		if (!properties.importEnabled()) {
			return;
		}
		try {
			MimitImportResult result = importService.importData();
			log.info("MIMIT import completed: {}", result);
		} catch (RuntimeException ex) {
			log.warn("MIMIT import failed: {}", ex.getMessage());
		}
	}
}
