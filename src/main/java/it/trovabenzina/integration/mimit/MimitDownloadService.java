package it.trovabenzina.integration.mimit;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import javax.net.ssl.SSLException;

import it.trovabenzina.exception.MimitImportException;
import it.trovabenzina.exception.MimitDownloadException;

@Service
public class MimitDownloadService {

	private static final Logger log = LoggerFactory.getLogger(MimitDownloadService.class);
	private final HttpClient httpClient;
	private final Duration requestTimeout;
	private final int maxAttempts;
	private final Duration retryDelay;

	@Autowired
	public MimitDownloadService(@Value("${mimit.download.connect-timeout:60s}") Duration connectTimeout,
			@Value("${mimit.download.request-timeout:120s}") Duration requestTimeout,
			@Value("${mimit.download.max-attempts:3}") int maxAttempts,
			@Value("${mimit.download.retry-delay:2s}") Duration retryDelay) {
		this(HttpClient.newBuilder().connectTimeout(connectTimeout).followRedirects(HttpClient.Redirect.NORMAL).build(),
				requestTimeout, maxAttempts, retryDelay);
	}

	MimitDownloadService(HttpClient httpClient, Duration requestTimeout, int maxAttempts, Duration retryDelay) {
		if (requestTimeout.isZero() || requestTimeout.isNegative() || maxAttempts < 1 || retryDelay.isNegative()) {
			throw new IllegalArgumentException("Invalid MIMIT download timeout or retry configuration");
		}
		this.httpClient = httpClient;
		this.requestTimeout = requestTimeout;
		this.maxAttempts = maxAttempts;
		this.retryDelay = retryDelay;
	}

	public String download(String url) {
		if (url == null || url.isBlank()) {
			throw new MimitImportException("MIMIT URL is not configured");
		}
		return send(url, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
	}

	public Path downloadToTempFile(String url) {
		if (url == null || url.isBlank()) {
			throw new MimitImportException("MIMIT URL is not configured");
		}
		Path tempFile = null;
		boolean completed = false;
		try {
			tempFile = Files.createTempFile("mimit-", ".csv");
			// A retry replaces any partial body left by the previous attempt.
			send(url, HttpResponse.BodyHandlers.ofFile(tempFile, StandardOpenOption.WRITE,
					StandardOpenOption.TRUNCATE_EXISTING));
			completed = true;
			return tempFile;
		} catch (IOException ex) {
			throw new MimitDownloadException("Unable to create MIMIT temporary file", ex);
		} finally {
			if (!completed && tempFile != null) {
				try {
					Files.deleteIfExists(tempFile);
				} catch (IOException ex) {
					log.warn("Unable to delete failed MIMIT download {}", tempFile, ex);
				}
			}
		}
	}

	private <T> HttpResponse<T> send(String url, HttpResponse.BodyHandler<T> handler) {
		URI uri = URI.create(url);
		String resource = uri.getHost() + uri.getPath();
		HttpRequest request = HttpRequest.newBuilder(uri).timeout(requestTimeout).GET().build();
		Instant startedAt = Instant.now();
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				log.info("Starting MIMIT download from {} (attempt {}/{}, timeout {})", resource, attempt,
						maxAttempts, requestTimeout);
				HttpResponse<T> response = httpClient.send(request, handler);
				int status = response.statusCode();
				if (status >= 200 && status < 300) {
					log.info("Completed MIMIT download from {} in {} ms", resource,
							Duration.between(startedAt, Instant.now()).toMillis());
					return response;
				}
				if ((status == 429 || status >= 500 && status < 600) && attempt < maxAttempts) {
					log.warn("MIMIT download from {} returned HTTP {}; retrying", resource, status);
				} else {
					throw new MimitDownloadException("MIMIT download failed with HTTP " + status + " for " + resource);
				}
			} catch (InterruptedException ex) {
				throw interrupted(resource, ex);
			} catch (IOException ex) {
				log.warn("MIMIT download from {} failed (attempt {}/{})", resource, attempt, maxAttempts, ex);
				if (attempt == maxAttempts || ex instanceof SSLException) {
					String detail = ex instanceof HttpTimeoutException ? "timed out (request timeout " + requestTimeout + ")"
							: "failed (" + ex.getClass().getSimpleName() + ")";
					throw new MimitDownloadException("MIMIT download " + detail + " for " + resource, ex);
				}
			}
			try {
				Thread.sleep(retryDelay.toMillis());
			} catch (InterruptedException ex) {
				throw interrupted(resource, ex);
			}
		}
		throw new IllegalStateException("MIMIT download attempts exhausted");
	}

	private MimitDownloadException interrupted(String resource, InterruptedException ex) {
		Thread.currentThread().interrupt();
		return new MimitDownloadException("MIMIT download interrupted for " + resource, ex);
	}
}
