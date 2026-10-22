package it.trovabenzina.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import it.trovabenzina.exception.MimitImportException;
import it.trovabenzina.integration.mimit.MimitImportResult;
import it.trovabenzina.integration.mimit.MimitImportService;
import it.trovabenzina.security.JwtService;
import it.trovabenzina.service.FrontendRebuildService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.datasource.url=jdbc:h2:mem:mimit-upload;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
		"spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
		"spring.datasource.password=", "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
		"mimit.import.enabled=false", "spring.servlet.multipart.max-file-size=1KB",
		"spring.servlet.multipart.max-request-size=4KB"
})
class AdminMimitUploadIntegrationTest {
	@Autowired
	private TestRestTemplate rest;
	@Autowired
	private JwtService jwt;
	@MockBean
	private MimitImportService imports;
	@MockBean
	private FrontendRebuildService rebuild;

	@Test
	void rejectsAnonymousAndInvalidJwtUploads() {
		for (String endpoint : List.of("/import/upload", "/import/prices/upload")) {
			assertThat(send(endpoint, "", files("stations", "prices")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
			assertThat(send(endpoint, "invalid", files("stations", "prices")).getStatusCode())
					.isEqualTo(HttpStatus.FORBIDDEN);
		}
		verifyNoInteractions(imports, rebuild);
	}

	@Test
	void acceptsFullUploadAndDeletesStagedFiles() {
		AtomicReference<Path> stations = new AtomicReference<>();
		AtomicReference<Path> prices = new AtomicReference<>();
		when(imports.importUploadedData(any(), any())).thenAnswer(call -> {
			stations.set(call.getArgument(0));
			prices.set(call.getArgument(1));
			assertThat(Files.readString(stations.get())).isEqualTo("CSV");
			assertThat(Files.readString(prices.get())).isEqualTo("CSV");
			return result();
		});
		var response = send("/import/upload", token(), files("stations", "prices"));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"pricesInserted\":1");
		assertThat(stations.get()).doesNotExist();
		assertThat(prices.get()).doesNotExist();
		verify(rebuild).triggerRebuild("uploaded MIMIT import");
	}

	@Test
	void acceptsPricesOnlyUpload() {
		when(imports.importUploadedPrices(any())).thenReturn(result());
		assertThat(send("/import/prices/upload", token(), files("prices")).getStatusCode()).isEqualTo(HttpStatus.OK);
		verify(imports).importUploadedPrices(any());
		verify(rebuild).triggerRebuild("uploaded MIMIT import");
	}

	@Test
	void rejectsMissingAndEmptyFiles() {
		var missing = send("/import/upload", token(), files("stations"));
		assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(missing.getBody()).contains("Missing multipart file: prices");
		var empty = files("stations");
		empty.add("prices", file(new byte[0]));
		assertThat(send("/import/upload", token(), empty).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		verifyNoInteractions(imports, rebuild);
	}

	@Test
	void enforcesMultipartSizeLimit() {
		var large = files("stations");
		large.add("prices", file(new byte[2048]));
		assertThat(send("/import/upload", token(), large).getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
		verifyNoInteractions(imports, rebuild);
	}

	@Test
	void failedImportCleansFilesAndDoesNotRebuild() {
		AtomicReference<Path> prices = new AtomicReference<>();
		when(imports.importUploadedPrices(any())).thenAnswer(call -> {
			prices.set(call.getArgument(0));
			throw new MimitImportException("Invalid MIMIT prices CSV header");
		});
		assertThat(send("/import/prices/upload", token(), files("prices")).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(prices.get()).doesNotExist();
		verifyNoInteractions(rebuild);
	}

	private org.springframework.http.ResponseEntity<String> send(String endpoint, String token,
			MultiValueMap<String, Object> files) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		if (!token.isEmpty()) {
			headers.setBearerAuth(token);
		}
		return rest.postForEntity("/api/admin/mimit" + endpoint, new HttpEntity<>(files, headers), String.class);
	}

	private String token() {
		return jwt.createToken("upload-test@example.test");
	}

	private MultiValueMap<String, Object> files(String... parts) {
		MultiValueMap<String, Object> files = new LinkedMultiValueMap<>();
		for (String part : parts) {
			files.add(part, file("CSV".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		}
		return files;
	}

	private ByteArrayResource file(byte[] bytes) {
		return new ByteArrayResource(bytes) {
			@Override
			public String getFilename() {
				return "dataset.csv";
			}
		};
	}

	private MimitImportResult result() {
		return new MimitImportResult(1, 1, 0, 0, 1, 1, 0, 0, 1, 0, 1, Instant.now(), Instant.now(), List.of());
	}
}
