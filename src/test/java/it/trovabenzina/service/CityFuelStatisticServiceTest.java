package it.trovabenzina.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.trovabenzina.dto.CityFuelStatisticResponseDto;
import it.trovabenzina.entity.City;
import it.trovabenzina.entity.CityFuelDailyStatistic;
import it.trovabenzina.entity.FuelType;
import it.trovabenzina.repository.CityFuelDailyStatisticRepository;
import it.trovabenzina.repository.CityRepository;
import it.trovabenzina.repository.FuelTypeRepository;
import it.trovabenzina.repository.StationPriceRepository;

@ExtendWith(MockitoExtension.class)
class CityFuelStatisticServiceTest {

	@Mock
	private CityFuelDailyStatisticRepository statisticRepository;

	@Mock
	private CityRepository cityRepository;

	@Mock
	private FuelTypeRepository fuelTypeRepository;

	@Mock
	private StationPriceRepository stationPriceRepository;

	@InjectMocks
	private CityFuelStatisticService statisticService;

	@Test
	void latestHandlesNestedAggregateResultReturnedByJpa() {
		City city = new City();
		city.setId(1L);
		city.setName("Milano");
		FuelType fuelType = new FuelType();
		fuelType.setId(2L);
		fuelType.setCode("BENZINA");
		LocalDateTime updatedAt = LocalDateTime.of(2026, 9, 22, 8, 30);

		when(cityRepository.findById(1L)).thenReturn(Optional.of(city));
		when(fuelTypeRepository.findByCodeIgnoreCase("BENZINA")).thenReturn(Optional.of(fuelType));
		when(stationPriceRepository.calculateStatistics(1L, 2L))
				.thenReturn(new Object[] { new Object[] { new BigDecimal("1.750"), new BigDecimal("1.700"),
						new BigDecimal("1.800"), 3L, updatedAt } });

		CityFuelStatisticResponseDto response = statisticService.latest(1L, "BENZINA");

		assertThat(response.averagePrice()).isEqualByComparingTo("1.750");
		assertThat(response.minimumPrice()).isEqualByComparingTo("1.700");
		assertThat(response.maximumPrice()).isEqualByComparingTo("1.800");
		assertThat(response.stationCount()).isEqualTo(3);
	}

	@Test
	void latestReturnsEmptyStatisticWhenFuelTypeHasNoDataYet() {
		City city = new City();
		city.setId(1L);
		city.setName("Milano");

		when(cityRepository.findById(1L)).thenReturn(Optional.of(city));
		when(fuelTypeRepository.findByCodeIgnoreCase("BENZINA")).thenReturn(Optional.empty());
		when(statisticRepository.findFirstByCityIdAndFuelTypeCodeIgnoreCaseOrderByDateDesc(1L, "BENZINA"))
				.thenReturn(Optional.empty());

		CityFuelStatisticResponseDto response = statisticService.latest(1L, "BENZINA");

		assertThat(response.fuelTypeCode()).isEqualTo("BENZINA");
		assertThat(response.averagePrice()).isEqualByComparingTo("0");
		assertThat(response.stationCount()).isZero();
	}

	@Test
	void recalculateHandlesDoubleAggregateValuesReturnedByDatabase() {
		City city = new City();
		city.setId(1L);
		FuelType fuelType = new FuelType();
		fuelType.setId(2L);
		LocalDate date = LocalDate.of(2026, 9, 22);
		when(stationPriceRepository.calculateStatistics(1L, 2L))
				.thenReturn(new Object[] { new Object[] { 1.75d, 1.70d, 1.80d, 3L, LocalDateTime.now() } });
		when(statisticRepository.findByCityIdAndFuelTypeIdAndDate(1L, 2L, date)).thenReturn(Optional.empty());
		when(cityRepository.findById(1L)).thenReturn(Optional.of(city));

		statisticService.recalculate(1L, fuelType, date);

		ArgumentCaptor<CityFuelDailyStatistic> captor = ArgumentCaptor.forClass(CityFuelDailyStatistic.class);
		verify(statisticRepository).save(captor.capture());
		assertThat(captor.getValue().getAveragePrice()).isEqualByComparingTo("1.75");
		assertThat(captor.getValue().getMinimumPrice()).isEqualByComparingTo("1.70");
		assertThat(captor.getValue().getMaximumPrice()).isEqualByComparingTo("1.80");
		assertThat(captor.getValue().getStationCount()).isEqualTo(3);
	}
}
