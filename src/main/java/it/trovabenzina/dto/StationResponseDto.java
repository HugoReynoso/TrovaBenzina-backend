package it.trovabenzina.dto;

import java.util.List;

public record StationResponseDto(Long id, String mimitId, String name, String brand, String address, Double latitude,
		Double longitude, Long cityId, String cityName, String provinceName, String regionName, Double distanceKm,
		List<StationPriceDto> prices) {
}
