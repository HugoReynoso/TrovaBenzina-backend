package it.trovabenzina.integration.mimit;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "mimit.import.enabled", havingValue = "true")
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
		Instant startedAt = Instant.now();
		log.info("Scheduled MIMIT import started with cron {}", properties.importCron());
		try {
			MimitImportResult result = importService.importData();
			log.info("Scheduled MIMIT import completed in {} ms: {}", Duration.between(startedAt, Instant.now()).toMillis(),
					result);
		} catch (RuntimeException ex) {
			log.warn("Scheduled MIMIT import failed after {} ms: {}", Duration.between(startedAt, Instant.now()).toMillis(),
					ex.getMessage());
		}
	}
}
