package it.trovabenzina.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CityFuelStatisticResponseDto(Long cityId, String cityName, String fuelTypeCode, BigDecimal averagePrice,
		BigDecimal minimumPrice, BigDecimal maximumPrice, Integer stationCount, Instant updatedAt) {
}
