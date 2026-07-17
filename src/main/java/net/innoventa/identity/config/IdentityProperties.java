package net.innoventa.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "identity")
public record IdentityProperties(String issuer, Map<String, ClientProperties> clients) {

    public record ClientProperties(String clientId, String clientSecret, String redirectUri, String audience) {
    }

}
