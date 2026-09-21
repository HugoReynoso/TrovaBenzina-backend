package it.trovabenzina.integration.mimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mimit")
public record MimitProperties(String stationsUrl, String pricesUrl, String importCron, boolean importEnabled) {
}
