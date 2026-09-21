package it.trovabenzina.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.trovabenzina.entity.City;
import it.trovabenzina.entity.Province;
import it.trovabenzina.entity.Region;
import it.trovabenzina.repository.CityRepository;

@ExtendWith(MockitoExtension.class)
class CityServiceTest {

	@Mock
	private CityRepository cityRepository;

	@InjectMocks
	private CityService cityService;

	@Test
	void findByIdMapsProvinceAndRegion() {
		when(cityRepository.findById(1L)).thenReturn(Optional.of(city()));

		assertThat(cityService.findById(1L).regionName()).isEqualTo("Lombardia");
	}

	@Test
	void findAllFiltersByProvince() {
		when(cityRepository.findByProvinceIdOrderByNameAsc(1L)).thenReturn(List.of(city()));

		assertThat(cityService.findAll(1L)).extracting("name").containsExactly("Milano");
	}

	private City city() {
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
		return city;
	}
}
