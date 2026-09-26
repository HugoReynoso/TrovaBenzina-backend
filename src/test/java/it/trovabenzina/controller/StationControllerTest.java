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
		when(stationService.findAll(1L, null, "BENZINA", true, null, null, null, null, null))
				.thenReturn(List.of(station()));

		mockMvc.perform(get("/api/stations").param("cityId", "1").param("fuelType", "BENZINA").param("selfService", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Station Test"));
	}

	@Test
	void stationsEndpointAcceptsProvinceLimitAndBounds() throws Exception {
		when(stationService.findAll(null, 1L, "BENZINA", true, 1500, 45.0, 46.0, 9.0, 10.0))
				.thenReturn(List.of(station()));

		mockMvc.perform(get("/api/stations")
				.param("provinceId", "1")
				.param("fuelType", "BENZINA")
				.param("selfService", "true")
				.param("limit", "1500")
				.param("minLat", "45.0")
				.param("maxLat", "46.0")
				.param("minLng", "9.0")
				.param("maxLng", "10.0"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].cityName").value("Milano"));
	}

	@Test
	void nearbyEndpointReturnsStationsWithDistance() throws Exception {
		when(stationService.findNearby(45.4642, 9.1900, null, null, null, 10.0, "BENZINA", true, 5))
				.thenReturn(List.of(stationWithDistance()));

		mockMvc.perform(get("/api/stations/nearby")
				.param("lat", "45.4642")
				.param("lng", "9.1900")
				.param("radiusKm", "10")
				.param("fuelType", "BENZINA")
				.param("selfService", "true")
				.param("limit", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Station Test"))
				.andExpect(jsonPath("$[0].distanceKm").value(1.23));
	}

	@Test
	void nearbyEndpointAcceptsCityAndProvinceNames() throws Exception {
		when(stationService.findNearby(null, null, null, "Milano", "Milano", 10.0, "BENZINA", true, 5))
				.thenReturn(List.of(stationWithDistance()));

		mockMvc.perform(get("/api/stations/nearby")
				.param("city", "Milano")
				.param("province", "Milano")
				.param("radiusKm", "10")
				.param("fuelType", "BENZINA")
				.param("selfService", "true")
				.param("limit", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].cityName").value("Milano"));
	}

	@Test
	void cheapestEndpointAcceptsProvince() throws Exception {
		when(stationService.findCheapest(null, 1L, "BENZINA", true, 20)).thenReturn(List.of(station()));

		mockMvc.perform(get("/api/stations/cheapest")
				.param("provinceId", "1")
				.param("fuelType", "BENZINA")
				.param("selfService", "true")
				.param("limit", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Station Test"));
	}

	private StationResponseDto station() {
		return new StationResponseDto(1L, "MI-000010", "Station Test", "Brand", "Via Roma", 45.46, 9.19,
				1L, "Milano", "Milano", "Lombardia", null, List.of());
	}

	private StationResponseDto stationWithDistance() {
		return new StationResponseDto(1L, "MI-000010", "Station Test", "Brand", "Via Roma", 45.46, 9.19,
				1L, "Milano", "Milano", "Lombardia", 1.23, List.of());
	}
}
