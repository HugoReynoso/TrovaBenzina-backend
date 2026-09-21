package it.trovabenzina.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.trovabenzina.entity.Province;
import it.trovabenzina.entity.Region;
import it.trovabenzina.repository.ProvinceRepository;

@ExtendWith(MockitoExtension.class)
class ProvinceServiceTest {

	@Mock
	private ProvinceRepository provinceRepository;

	@InjectMocks
	private ProvinceService provinceService;

	@Test
	void findAllFiltersByRegionWhenRegionIdIsProvided() {
		Province province = province();
		when(provinceRepository.findByRegionIdOrderByNameAsc(1L)).thenReturn(List.of(province));

		assertThat(provinceService.findAll(1L)).hasSize(1);
		verify(provinceRepository).findByRegionIdOrderByNameAsc(1L);
	}

	private Province province() {
		Region region = new Region();
		region.setId(1L);
		region.setName("Lombardia");
		Province province = new Province();
		province.setId(1L);
		province.setName("Milano");
		province.setCode("MI");
		province.setRegion(region);
		return province;
	}
}
