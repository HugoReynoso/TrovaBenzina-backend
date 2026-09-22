package it.trovabenzina.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record StationPriceDto(String fuelTypeCode, String fuelTypeName, BigDecimal price, Boolean selfService,
		Instant communicatedAt) {
}
