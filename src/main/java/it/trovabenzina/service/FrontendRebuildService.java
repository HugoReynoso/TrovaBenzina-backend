package it.trovabenzina.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Il frontend (Vercel) e' un sito statico: i prezzi vengono letti da questa API al momento della build.
 * Dopo ogni import MIMIT riuscito chiediamo a Vercel una nuova build tramite Deploy Hook, cosi' le pagine
 * pubblicate (e quelle viste da Google) mostrano i prezzi appena importati.
 *
 * Configurazione: variabile d'ambiente VERCEL_DEPLOY_HOOK_URL (Vercel > Project > Settings > Git > Deploy Hooks).
 * Se non e' impostata il servizio non fa nulla.
 */
@Service
public class FrontendRebuildService {

	private static final Logger log = LoggerFactory.getLogger(FrontendRebuildService.class);

	private final String deployHookUrl;
	private final HttpClient httpClient;

	public FrontendRebuildService(@Value("${app.frontend.deploy-hook-url:}") String deployHookUrl) {
		this.deployHookUrl = deployHookUrl == null ? "" : deployHookUrl.trim();
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	}

	public boolean isConfigured() {
		return !deployHookUrl.isEmpty();
	}

	/**
	 * Avvia la build del frontend senza bloccare la risposta dell'import.
	 * Eventuali errori vengono solo registrati nei log: l'import resta comunque valido.
	 */
	public void triggerRebuild(String reason) {
		if (!isConfigured()) {
			log.info("Frontend rebuild skipped ({}): app.frontend.deploy-hook-url not configured", reason);
			return;
		}

		HttpRequest request = HttpRequest.newBuilder(URI.create(deployHookUrl))
				.timeout(Duration.ofSeconds(30))
				.POST(HttpRequest.BodyPublishers.noBody())
				.build();

		httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
			if (error != null) {
				log.warn("Frontend rebuild request failed ({}): {}", reason, error.getMessage());
			} else if (response.statusCode() >= 300) {
				log.warn("Frontend rebuild request returned HTTP {} ({})", response.statusCode(), reason);
			} else {
				log.info("Frontend rebuild requested after {} (HTTP {})", reason, response.statusCode());
			}
		});
	}
}
