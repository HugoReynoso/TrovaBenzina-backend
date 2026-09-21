package it.trovabenzina.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.trovabenzina.entity.Region;
import it.trovabenzina.repository.RegionRepository;

@ExtendWith(MockitoExtension.class)
class RegionServiceTest {

	@Mock
	private RegionRepository regionRepository;

	@InjectMocks
	private RegionService regionService;

	@Test
	void findAllReturnsRegionsSortedByName() {
		Region lombardia = region(1L, "Lombardia");
		Region abruzzo = region(2L, "Abruzzo");
		when(regionRepository.findAll()).thenReturn(List.of(lombardia, abruzzo));

		assertThat(regionService.findAll()).extracting("name").containsExactly("Abruzzo", "Lombardia");
	}

	private Region region(Long id, String name) {
		Region region = new Region();
		region.setId(id);
		region.setName(name);
		return region;
	}
}
