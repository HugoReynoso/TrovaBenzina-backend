package it.trovabenzina.dto;

public record CityResponseDto(Long id, String name, String slug, Long provinceId, String provinceName,
		String regionName, Double latitude, Double longitude) {
}
