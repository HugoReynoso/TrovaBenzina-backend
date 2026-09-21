package it.trovabenzina.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProvinceCreateDto(
		@NotBlank @Size(max = 100) String name,
		@NotBlank @Size(max = 10) String code,
		@NotNull Long regionId) {
}
