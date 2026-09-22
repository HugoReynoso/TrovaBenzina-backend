package it.trovabenzina.dto;

import java.math.BigDecimal;
import java.time.Instant;

import it.trovabenzina.entity.PriceReportStatus;

public record PriceReportResponseDto(Long id, Long stationId, String stationName, String brand, String cityName,
		String fuelTypeCode, BigDecimal price, Boolean selfService, String reporterName, String reporterEmail,
		String note, PriceReportStatus status, Instant submittedAt) {
}
