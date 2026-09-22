package it.trovabenzina.integration.mimit;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import it.trovabenzina.exception.MimitImportException;

@Service
public class MimitDownloadService {

	private static final Logger log = LoggerFactory.getLogger(MimitDownloadService.class);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

	private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();

	public String download(String url) {
		if (url == null || url.isBlank()) {
			throw new MimitImportException("MIMIT URL is not configured");
		}
		Instant startedAt = Instant.now();
		try {
			log.info("Starting MIMIT download from {}", url);
			HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT).GET().build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 400) {
				throw new MimitImportException("MIMIT download failed with status " + response.statusCode());
			}
			log.info("Completed MIMIT download from {}: {} chars in {} ms", url, response.body().length(),
					Duration.between(startedAt, Instant.now()).toMillis());
			return response.body();
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new MimitImportException("Unable to download MIMIT data", ex);
		} catch (IOException ex) {
			throw new MimitImportException("Unable to download MIMIT data", ex);
		}
	}
}
