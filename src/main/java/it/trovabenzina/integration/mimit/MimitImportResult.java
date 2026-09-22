package it.trovabenzina.integration.mimit;

import java.time.Instant;
import java.util.List;

public record MimitImportResult(int stationsRead, int stationsInserted, int stationsUpdated, int stationsSkipped,
		int pricesRead, int pricesInserted, int pricesUpdated, int pricesSkipped, int historyInserted, int errors,
		int statisticsUpdated, Instant startedAt, Instant finishedAt, List<String> messages) {
}
