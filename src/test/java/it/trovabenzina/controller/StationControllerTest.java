package it.trovabenzina.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import it.trovabenzina.dto.StationResponseDto;
import it.trovabenzina.service.StationService;

class StationControllerTest {

	private StationService stationService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		stationService = Mockito.mock(StationService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new StationController(stationService)).build();
	}

	@Test
	void stationsEndpointReturnsStations() throws Exception {
		when(stationService.findAll(1L, "BENZINA", true)).thenReturn(List.of(station()));

		mockMvc.perform(get("/api/stations").param("cityId", "1").param("fuelType", "BENZINA").param("selfService", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Station Test"));
	}

	private StationResponseDto station() {
		return new StationResponseDto(1L, "MI-000010", "Station Test", "Brand", "Via Roma", 45.46, 9.19,
				1L, "Milano", "Milano", "Lombardia", null, List.of());
	}
}
