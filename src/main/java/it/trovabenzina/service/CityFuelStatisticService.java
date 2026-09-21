package it.trovabenzina.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.trovabenzina.dto.CityFuelStatisticHistoryDto;
import it.trovabenzina.dto.CityFuelStatisticResponseDto;
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
		validateInputs(cityId, fuelType);
		CityFuelDailyStatistic statistic = statisticRepository
				.findFirstByCityIdAndFuelTypeCodeIgnoreCaseOrderByDateDesc(cityId, fuelType)
				.orElseThrow(() -> new FuelTypeNotFoundException(fuelType));
		return toDto(statistic);
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
				statistic.getMaximumPrice(), statistic.getStationCount(), statistic.getDate());
	}

	@Transactional
	public void recalculate(Long cityId, FuelType fuelType, LocalDate date) {
		Object[] values = stationPriceRepository.calculateStatistics(cityId, fuelType.getId());
		if (values == null || values.length == 0 || values[0] == null) {
			return;
		}
		CityFuelDailyStatistic statistic = statisticRepository.findByCityIdAndFuelTypeIdAndDate(cityId, fuelType.getId(),
				date).orElseGet(CityFuelDailyStatistic::new);
		statistic.setCity(cityRepository.findById(cityId).orElseThrow(() -> new CityNotFoundException(cityId)));
		statistic.setFuelType(fuelType);
		statistic.setDate(date);
		statistic.setAveragePrice((java.math.BigDecimal) values[0]);
		statistic.setMinimumPrice((java.math.BigDecimal) values[1]);
		statistic.setMaximumPrice((java.math.BigDecimal) values[2]);
		statistic.setStationCount(((Number) values[3]).intValue());
		statisticRepository.save(statistic);
	}
}
