package it.trovabenzina.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CityCreateDto(
		@NotBlank @Size(max = 120) String name,
		@NotNull Long provinceId) {
}
