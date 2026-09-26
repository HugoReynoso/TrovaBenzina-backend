package it.trovabenzina.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

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
import it.trovabenzina.repository.CityRepository;
import it.trovabenzina.repository.StationPriceRepository;
import it.trovabenzina.repository.StationRepository;

@ExtendWith(MockitoExtension.class)
class StationServiceTest {

	@Mock
	private StationRepository stationRepository;

	@Mock
	private StationPriceRepository stationPriceRepository;

	@Mock
	private CityRepository cityRepository;

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

	@Test
	void cheapestRemovesDuplicateStationsKeepingPriceOrder() {
		Station station = station();
		when(stationRepository.findCheapest(eq(1L), eq("BENZINA"), eq(null), any(Pageable.class)))
				.thenReturn(List.of(station, station));
		when(stationPriceRepository.findByStationIdIn(List.of(1L))).thenReturn(List.of(price(station, "BENZINA", true)));

		assertThat(stationService.findCheapest(1L, "BENZINA", null, 10)).hasSize(1);
	}

	@Test
	void nearbyFiltersByCoordinatesAndReturnsDistance() {
		Station near = station(1L, 45.465, 9.191);
		Station far = station(2L, 45.900, 9.800);
		when(stationRepository.findNearbyCandidates("BENZINA", true)).thenReturn(List.of(far, near));
		when(stationPriceRepository.findByStationIdIn(List.of(1L))).thenReturn(List.of(price(near, "BENZINA", true)));

		List<it.trovabenzina.dto.StationResponseDto> result = stationService.findNearby(45.4642, 9.1900, null, 5.0,
				"BENZINA", true, 10);

		assertThat(result).hasSize(1);
		assertThat(result.getFirst().id()).isEqualTo(1L);
		assertThat(result.getFirst().distanceKm()).isNotNull();
	}

	@Test
	void nearbyByCityUsesExactCityStationsWhenEnoughResultsExist() {
		Station station = station(1L, 45.465, 9.191);
		City city = station.getCity();
		city.setLatitude(45.4642);
		city.setLongitude(9.1900);
		when(cityRepository.findById(1L)).thenReturn(Optional.of(city));
		when(stationRepository.findStations(1L, "BENZINA", true)).thenReturn(List.of(station));
		when(stationPriceRepository.findByStationIdIn(List.of(1L))).thenReturn(List.of(price(station, "BENZINA", true)));

		List<it.trovabenzina.dto.StationResponseDto> result = stationService.findNearby(null, null, 1L, 10.0, "BENZINA",
				true, 1);

		assertThat(result).hasSize(1);
		assertThat(result.getFirst().id()).isEqualTo(1L);
	}

	@Test
	void nearbyByCityNameAndProvinceResolvesCityBeforeSearchingStations() {
		Station station = station(1L, 45.465, 9.191);
		City city = station.getCity();
		city.setLatitude(45.4642);
		city.setLongitude(9.1900);
		when(cityRepository.findByNameAndOptionalProvince("Milano", "Milano")).thenReturn(List.of(city));
		when(stationRepository.findStations(1L, "BENZINA", true)).thenReturn(List.of(station));
		when(stationPriceRepository.findByStationIdIn(List.of(1L))).thenReturn(List.of(price(station, "BENZINA", true)));

		List<it.trovabenzina.dto.StationResponseDto> result = stationService.findNearby(null, null, null, "Milano",
				"Milano", 10.0, "BENZINA", true, 1);

		assertThat(result).hasSize(1);
		assertThat(result.getFirst().cityName()).isEqualTo("Milano");
	}

	private Station station() {
		return station(1L, 45.46, 9.19);
	}

	private Station station(Long id, Double latitude, Double longitude) {
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
		station.setId(id);
		station.setMimitId("MI-0000" + id);
		station.setName("Station Test");
		station.setCity(city);
		station.setLatitude(latitude);
		station.setLongitude(longitude);
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
