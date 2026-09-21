package it.trovabenzina.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CityFuelStatisticResponseDto(Long cityId, String cityName, String fuelType, BigDecimal averagePrice,
		BigDecimal minimumPrice, BigDecimal maximumPrice, Integer stationCount, LocalDate date) {
}
