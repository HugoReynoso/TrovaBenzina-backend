package it.trovabenzina.integration.mimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.net.ssl.SSLHandshakeException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import it.trovabenzina.exception.MimitDownloadException;
import it.trovabenzina.exception.MimitImportException;

class MimitDownloadServiceTest {
	private HttpServer server;
	private MimitDownloadService service;
	private final AtomicInteger requests = new AtomicInteger();
	private static final String CSV = "idImpianto;desc_carburante;prezzo\n123;Benzina;1.789\n";

	@BeforeEach
	void setUp() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.start();
		service = new MimitDownloadService(Duration.ofSeconds(1), Duration.ofSeconds(2), 3, Duration.ZERO);
	}

	@AfterEach
	void tearDown() {
		server.stop(0);
		Thread.interrupted();
	}

	@Test
	void downloadsUtf8Csv() {
		respond("/data", 200, CSV + "Citta: Forl\u00ec");
		assertThat(service.download(url("/data"))).isEqualTo(CSV + "Citta: Forl\u00ec");
	}

	@Test
	void followsRedirects() {
		server.createContext("/redirect", exchange -> {
			exchange.getResponseHeaders().add("Location", "/data");
			exchange.sendResponseHeaders(302, -1);
			exchange.close();
		});
		respond("/data", 200, CSV);
		assertThat(service.download(url("/redirect"))).isEqualTo(CSV);
	}

	@Test
	void retriesServerFailureAndReplacesPartialFile() throws IOException {
		server.createContext("/data", exchange -> {
			boolean first = requests.incrementAndGet() == 1;
			byte[] body = (first ? "long error page that must not remain in the CSV file".repeat(20) : CSV)
					.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(first ? 503 : 200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		Path file = service.downloadToTempFile(url("/data"));
		try {
			assertThat(Files.readString(file)).isEqualTo(CSV);
			assertThat(requests).hasValue(2);
		} finally {
			Files.deleteIfExists(file);
		}
	}

	@Test
	void doesNotRetry404AndCleansUpFailedFile() throws IOException {
		Set<Path> before = temporaryDownloads();
		respond("/data", 404, "missing");
		assertThatThrownBy(() -> service.downloadToTempFile(url("/data")))
				.isInstanceOf(MimitDownloadException.class).hasMessageContaining("HTTP 404");
		assertThat(requests).hasValue(1);
		assertThat(temporaryDownloads()).isSubsetOf(before);
	}

	@Test
	void limitsAttemptsForServiceUnavailable() {
		respond("/data", 503, "unavailable");
		assertThatThrownBy(() -> service.download(url("/data")))
				.isInstanceOf(MimitDownloadException.class).hasMessageContaining("HTTP 503");
		assertThat(requests).hasValue(3);
	}

	@Test
	void retriesRateLimit() {
		respond("/data", 429, "rate limited");
		assertThatThrownBy(() -> service.download(url("/data"))).hasMessageContaining("HTTP 429");
		assertThat(requests).hasValue(3);
	}

	@Test
	void timesOutSlowServer() {
		server.createContext("/slow", exchange -> {
			try {
				Thread.sleep(500);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			} finally {
				exchange.close();
			}
		});
		MimitDownloadService shortTimeout = new MimitDownloadService(HttpClient.newHttpClient(),
				Duration.ofMillis(100), 1, Duration.ZERO);
		assertThatThrownBy(() -> shortTimeout.download(url("/slow")))
				.isInstanceOf(MimitDownloadException.class).hasMessageContaining("timed out")
				.hasCauseInstanceOf(HttpTimeoutException.class);
	}

	@Test
	void retriesNetworkFailure() throws Exception {
		HttpClient client = mock(HttpClient.class);
		HttpResponse<Object> response = mock(HttpResponse.class);
		when(response.statusCode()).thenReturn(200);
		when(response.body()).thenReturn(CSV);
		when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenThrow(new IOException("connection reset")).thenReturn(response);
		MimitDownloadService retrying = new MimitDownloadService(client, Duration.ofSeconds(2), 3, Duration.ZERO);
		assertThat(retrying.download(url("/data"))).isEqualTo(CSV);
		verify(client, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
	}

	@Test
	void preservesInterruptAndDoesNotRetry() throws Exception {
		HttpClient client = mock(HttpClient.class);
		when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenThrow(new InterruptedException("cancelled"));
		MimitDownloadService interrupted = new MimitDownloadService(client, Duration.ofSeconds(2), 3, Duration.ZERO);
		assertThatThrownBy(() -> interrupted.download(url("/data"))).hasMessageContaining("interrupted");
		assertThat(Thread.currentThread().isInterrupted()).isTrue();
		Thread.interrupted();
		verify(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
	}

	@Test
	void doesNotRetryTlsFailures() throws Exception {
		HttpClient client = mock(HttpClient.class);
		when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenThrow(new SSLHandshakeException("untrusted certificate"));
		MimitDownloadService tls = new MimitDownloadService(client, Duration.ofSeconds(2), 3, Duration.ZERO);
		assertThatThrownBy(() -> tls.download(url("/data"))).hasMessageContaining("SSLHandshakeException");
		verify(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
	}

	@Test
	void rejectsMissingUrls() {
		assertThatThrownBy(() -> service.download(" ")).isInstanceOf(MimitImportException.class)
				.hasMessage("MIMIT URL is not configured");
		assertThatThrownBy(() -> service.downloadToTempFile(null)).isInstanceOf(MimitImportException.class);
	}

	private void respond(String path, int status, String text) {
		server.createContext(path, exchange -> {
			requests.incrementAndGet();
			byte[] body = text.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
	}

	private String url(String path) {
		return "http://127.0.0.1:" + server.getAddress().getPort() + path;
	}

	private Set<Path> temporaryDownloads() throws IOException {
		try (var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
			return files.filter(file -> file.getFileName().toString().startsWith("mimit-"))
					.collect(Collectors.toSet());
		}
	}
}
