package it.trovabenzina.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StationPriceDto(Long fuelTypeId, String fuelTypeCode, String fuelTypeName, BigDecimal price,
		Boolean selfService, LocalDateTime communicatedAt) {
}
