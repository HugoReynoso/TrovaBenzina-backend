package it.trovabenzina.dto;

import java.util.List;

public record StationResponseDto(Long id, Long mimitId, String name, String brand, String address, String municipality,
		String provinceCode, Double latitude, Double longitude, Boolean active, Long cityId, String cityName,
		Long provinceId, String provinceName, Long regionId, String regionName, List<StationPriceDto> prices) {
}
