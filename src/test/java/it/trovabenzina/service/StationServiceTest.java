package it.trovabenzina.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import it.trovabenzina.entity.City;
import it.trovabenzina.entity.FuelType;
import it.trovabenzina.entity.Province;
import it.trovabenzina.entity.Region;
import it.trovabenzina.entity.Station;
import it.trovabenzina.entity.StationPrice;
import it.trovabenzina.repository.StationPriceRepository;
import it.trovabenzina.repository.StationRepository;

@ExtendWith(MockitoExtension.class)
class StationServiceTest {

	@Mock
	private StationRepository stationRepository;

	@Mock
	private StationPriceRepository stationPriceRepository;

	@InjectMocks
	private StationService stationService;

	@Test
	void findAllReturnsStationsWithFilteredPrices() {
		Station station = station();
		StationPrice price = price(station, "BENZINA", true);
		when(stationRepository.findStations(1L, "BENZINA", true)).thenReturn(List.of(station));
		when(stationPriceRepository.findByStationIdIn(List.of(1L))).thenReturn(List.of(price));

		assertThat(stationService.findAll(1L, "BENZINA", true).getFirst().prices()).hasSize(1);
	}

	@Test
	void cheapestDefaultsLimitAndMapsPrices() {
		Station station = station();
		when(stationRepository.findCheapest(eq(1L), eq("BENZINA"), eq(true), any(Pageable.class)))
				.thenReturn(List.of(station));
		when(stationPriceRepository.findByStationIdIn(List.of(1L))).thenReturn(List.of(price(station, "BENZINA", true)));

		assertThat(stationService.findCheapest(1L, "BENZINA", true, null)).hasSize(1);
	}

	private Station station() {
		Region region = new Region();
		region.setId(1L);
		region.setName("Lombardia");
		Province province = new Province();
		province.setId(1L);
		province.setName("Milano");
		province.setCode("MI");
		province.setRegion(region);
		City city = new City();
		city.setId(1L);
		city.setName("Milano");
		city.setProvince(province);
		Station station = new Station();
		station.setId(1L);
		station.setMimitId(10L);
		station.setName("Station Test");
		station.setCity(city);
		station.setActive(true);
		return station;
	}

	private StationPrice price(Station station, String code, Boolean selfService) {
		FuelType fuelType = new FuelType();
		fuelType.setId(1L);
		fuelType.setCode(code);
		fuelType.setName(code);
		StationPrice price = new StationPrice();
		price.setId(1L);
		price.setStation(station);
		price.setFuelType(fuelType);
		price.setPrice(new BigDecimal("1.759"));
		price.setSelfService(selfService);
		return price;
	}
}
