package it.trovabenzina.dto;

public record CityResponseDto(Long id, String name, Long provinceId, String provinceName, String provinceCode,
		Long regionId, String regionName) {
}
