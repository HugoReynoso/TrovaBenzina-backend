package it.trovabenzina.integration.mimit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MimitPriceRecord(String stationMimitId, String fuelTypeCode, String fuelTypeName, BigDecimal price,
		Boolean selfService, LocalDateTime communicatedAt) {
}
