package it.trovabenzina.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.trovabenzina.dto.CityFuelStatisticHistoryDto;
import it.trovabenzina.dto.CityFuelStatisticResponseDto;
import it.trovabenzina.entity.City;
import it.trovabenzina.entity.CityFuelDailyStatistic;
import it.trovabenzina.entity.FuelType;
import it.trovabenzina.exception.CityNotFoundException;
import it.trovabenzina.exception.FuelTypeNotFoundException;
import it.trovabenzina.repository.CityFuelDailyStatisticRepository;
import it.trovabenzina.repository.CityRepository;
import it.trovabenzina.repository.FuelTypeRepository;
import it.trovabenzina.repository.StationPriceRepository;

@Service
public class CityFuelStatisticService {

	private final CityFuelDailyStatisticRepository statisticRepository;
	private final CityRepository cityRepository;
	private final FuelTypeRepository fuelTypeRepository;
	private final StationPriceRepository stationPriceRepository;

	public CityFuelStatisticService(CityFuelDailyStatisticRepository statisticRepository, CityRepository cityRepository,
			FuelTypeRepository fuelTypeRepository, StationPriceRepository stationPriceRepository) {
		this.statisticRepository = statisticRepository;
		this.cityRepository = cityRepository;
		this.fuelTypeRepository = fuelTypeRepository;
		this.stationPriceRepository = stationPriceRepository;
	}

	@Transactional(readOnly = true)
	public CityFuelStatisticResponseDto latest(Long cityId, String fuelType) {
		City city = cityRepository.findById(cityId).orElseThrow(() -> new CityNotFoundException(cityId));
		String normalizedFuelType = normalizeFuelType(fuelType);
		Optional<FuelType> foundFuelType = fuelTypeRepository.findByCodeIgnoreCase(normalizedFuelType);
		if (foundFuelType.isPresent()) {
			Object[] values = normalizeStatisticsResult(
					stationPriceRepository.calculateStatistics(cityId, foundFuelType.get().getId()));
			if (values != null) {
				LocalDateTime updatedAt = values[4] instanceof LocalDateTime dateTime ? dateTime : LocalDateTime.now();
				return new CityFuelStatisticResponseDto(city.getId(), city.getName(), foundFuelType.get().getCode(),
						toBigDecimal(values[0]), toBigDecimal(values[1]), toBigDecimal(values[2]),
						((Number) values[3]).intValue(), updatedAt.toInstant(ZoneOffset.UTC));
			}
		}
		return statisticRepository.findFirstByCityIdAndFuelTypeCodeIgnoreCaseOrderByDateDesc(cityId, normalizedFuelType)
				.map(this::toDto).orElseGet(() -> emptyStatistic(city, normalizedFuelType));
	}

	@Transactional(readOnly = true)
	public List<CityFuelStatisticHistoryDto> history(Long cityId, String fuelType, LocalDate from, LocalDate to) {
		validateInputs(cityId, fuelType);
		LocalDate end = to == null ? LocalDate.now() : to;
		LocalDate start = from == null ? end.minusDays(30) : from;
		if (start.isAfter(end)) {
			throw new IllegalArgumentException("from must be before or equal to to");
		}
		return statisticRepository.findByCityIdAndFuelTypeCodeIgnoreCaseAndDateBetweenOrderByDateAsc(cityId, fuelType,
				start, end).stream().map(stat -> new CityFuelStatisticHistoryDto(stat.getDate(), stat.getAveragePrice(),
						stat.getMinimumPrice(), stat.getMaximumPrice()))
				.toList();
	}

	private void validateInputs(Long cityId, String fuelType) {
		if (!cityRepository.existsById(cityId)) {
			throw new CityNotFoundException(cityId);
		}
		if (fuelType == null || fuelType.isBlank()) {
			throw new IllegalArgumentException("fuelType is required");
		}
		if (fuelTypeRepository.findByCodeIgnoreCase(fuelType).isEmpty()) {
			throw new FuelTypeNotFoundException(fuelType);
		}
	}

	private CityFuelStatisticResponseDto toDto(CityFuelDailyStatistic statistic) {
		return new CityFuelStatisticResponseDto(statistic.getCity().getId(), statistic.getCity().getName(),
				statistic.getFuelType().getCode(), statistic.getAveragePrice(), statistic.getMinimumPrice(),
				statistic.getMaximumPrice(), statistic.getStationCount(),
				statistic.getUpdatedAt() == null ? null : statistic.getUpdatedAt().toInstant(ZoneOffset.UTC));
	}

	private CityFuelStatisticResponseDto emptyStatistic(City city, String fuelType) {
		return new CityFuelStatisticResponseDto(city.getId(), city.getName(), fuelType, BigDecimal.ZERO, BigDecimal.ZERO,
				BigDecimal.ZERO, 0, Instant.now());
	}

	private String normalizeFuelType(String fuelType) {
		if (fuelType == null || fuelType.isBlank()) {
			throw new IllegalArgumentException("fuelType is required");
		}
		String normalized = fuelType.trim().toUpperCase(Locale.ROOT).replace("À", "A").replace("È", "E")
				.replace("É", "E").replace("Ì", "I").replace("Ò", "O").replace("Ù", "U")
				.replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
		return switch (normalized) {
			case "GASOLIO", "DIESEL", "GASOLIO_SELF", "DIESEL_SELF" -> "DIESEL";
			case "BENZINA", "SUPER", "SENZA_PIOMBO", "VERDE" -> "BENZINA";
			case "GPL" -> "GPL";
			case "METANO", "CNG" -> "METANO";
			default -> normalized;
		};
	}

	@Transactional
	public void recalculate(Long cityId, FuelType fuelType, LocalDate date) {
		Object[] values = normalizeStatisticsResult(stationPriceRepository.calculateStatistics(cityId, fuelType.getId()));
		if (values == null) {
			return;
		}
		CityFuelDailyStatistic statistic = statisticRepository.findByCityIdAndFuelTypeIdAndDate(cityId, fuelType.getId(),
				date).orElseGet(CityFuelDailyStatistic::new);
		statistic.setCity(cityRepository.findById(cityId).orElseThrow(() -> new CityNotFoundException(cityId)));
		statistic.setFuelType(fuelType);
		statistic.setDate(date);
		statistic.setAveragePrice(toBigDecimal(values[0]));
		statistic.setMinimumPrice(toBigDecimal(values[1]));
		statistic.setMaximumPrice(toBigDecimal(values[2]));
		statistic.setStationCount(((Number) values[3]).intValue());
		statistic.setUpdatedAt(java.time.LocalDateTime.now());
		statisticRepository.save(statistic);
	}

	private Object[] normalizeStatisticsResult(Object[] values) {
		if (values == null || values.length == 0) {
			return null;
		}
		Object[] row = values.length == 1 && values[0] instanceof Object[] nested ? nested : values;
		return row.length < 5 || row[0] == null ? null : row;
	}

	private BigDecimal toBigDecimal(Object value) {
		if (value instanceof BigDecimal decimal) {
			return decimal;
		}
		if (value instanceof Number number) {
			return BigDecimal.valueOf(number.doubleValue());
		}
		return new BigDecimal(value.toString());
	}
}
