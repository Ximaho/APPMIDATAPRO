package com.midatacredito.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Cliente HTTP dedicado a la API de Anthropic, con URL base, versión de API y tiempos de espera.
 * El análisis de una imagen puede tardar decenas de segundos, por eso el timeout de lectura es amplio.
 */
@Configuration
public class AnthropicClientConfig {

    public static final String ANTHROPIC_VERSION = "2023-06-01";

    @Bean
    public RestClient anthropicRestClient(RestClient.Builder builder,
                                          @Value("${anthropic.api-url}") String apiUrl,
                                          @Value("${anthropic.timeout-seconds}") int timeoutSeconds) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        return builder
                .baseUrl(apiUrl)
                .requestFactory(requestFactory)
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .build();
    }
}
