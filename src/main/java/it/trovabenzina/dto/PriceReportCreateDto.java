package it.trovabenzina.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PriceReportCreateDto(
		@NotNull Long stationId,
		@NotBlank @Size(max = 255) String stationName,
		@Size(max = 120) String brand,
		@NotBlank @Size(max = 120) String cityName,
		@NotBlank @Size(max = 50) String fuelTypeCode,
		@NotNull @DecimalMin("0.001") @DecimalMax("9.999") BigDecimal price,
		@NotNull Boolean selfService,
		@Size(max = 120) String reporterName,
		@Email @Size(max = 180) String reporterEmail,
		@Size(max = 500) String note) {
}
