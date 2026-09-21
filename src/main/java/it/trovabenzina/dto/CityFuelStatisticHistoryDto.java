package it.trovabenzina.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CityFuelStatisticHistoryDto(LocalDate date, BigDecimal averagePrice, BigDecimal minimumPrice,
		BigDecimal maximumPrice) {
}
