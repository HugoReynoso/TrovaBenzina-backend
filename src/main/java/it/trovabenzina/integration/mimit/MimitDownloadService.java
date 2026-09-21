package it.trovabenzina.integration.mimit;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;

import it.trovabenzina.exception.MimitImportException;

@Service
public class MimitDownloadService {

	private final HttpClient httpClient = HttpClient.newHttpClient();

	public String download(String url) {
		if (url == null || url.isBlank()) {
			throw new MimitImportException("MIMIT URL is not configured");
		}
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 400) {
				throw new MimitImportException("MIMIT download failed with status " + response.statusCode());
			}
			return response.body();
		} catch (IOException | InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new MimitImportException("Unable to download MIMIT data", ex);
		}
	}
}
