package com.klickit.order.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.klickit.common.util.HaversineDistanceCalculator;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Hosted road-routing service communicating with OpenRouteService Directions API.
 * Configured with configurable connect/request timeouts, masked logging to prevent
 * credential exposure, and safe error mapping to unavailable results.
 */
@Service
@Slf4j
public class HttpDrivingDistanceService implements DrivingDistanceService {

    private final DrivingDistanceProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public HttpDrivingDistanceService(
            DrivingDistanceProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getRequestTimeoutMs()));

        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .build();
    }

    public HttpDrivingDistanceService(
            DrivingDistanceProperties properties,
            RestClient restClient,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void validateConfigurationOnStartup() {
        if (!properties.isEnabled()) {
            log.info("Driving distance verification is disabled via configuration.");
        } else if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("KLICKIT_ROUTING_API_KEY is not configured. Live driving distance verification will mark routes as UNAVAILABLE.");
        } else {
            log.info("HttpDrivingDistanceService initialized successfully with provider: [{}] and endpoint: [{}] (max distance: {} m)",
                    properties.getProvider(), properties.getBaseUrl(), properties.getMaxDistanceMeters());
        }
    }

    @Override
    public DrivingDistanceResult calculateDistance(double originLat, double originLng, double destLat, double destLng) {
        if (!HaversineDistanceCalculator.isValidCoordinate(originLat, originLng)
                || !HaversineDistanceCalculator.isValidCoordinate(destLat, destLng)) {
            return DrivingDistanceResult.unavailable("Invalid geographic coordinates for route calculation");
        }

        if (!properties.isEnabled()) {
            return DrivingDistanceResult.unavailable("Routing verification is disabled");
        }

        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return DrivingDistanceResult.unavailable("Routing provider API key is not configured");
        }

        try {
            String url = properties.getBaseUrl().replaceAll("/+$", "") + "/v2/directions/driving-car";

            // OpenRouteService coordinates format: [longitude, latitude]
            Map<String, Object> requestBody = Map.of(
                    "coordinates", List.of(
                            List.of(originLng, originLat),
                            List.of(destLng, destLat)
                    )
            );

            ResponseEntity<String> response = restClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, apiKey.trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toEntity(String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Routing provider returned non-2xx status: {}", response.getStatusCode());
                return DrivingDistanceResult.unavailable("Routing provider returned non-2xx status: " + response.getStatusCode().value());
            }

            JsonNode root = objectMapper.readTree(response.getBody());

            // OpenRouteService standard response has "routes[0].summary.distance" (in metres)
            // GeoJSON format has "features[0].properties.summary.distance"
            Double distanceMetersRaw = null;
            JsonNode routes = root.path("routes");
            if (routes.isArray() && !routes.isEmpty()) {
                JsonNode summary = routes.get(0).path("summary");
                if (summary.has("distance")) {
                    distanceMetersRaw = summary.path("distance").asDouble();
                }
            }

            if (distanceMetersRaw == null) {
                JsonNode features = root.path("features");
                if (features.isArray() && !features.isEmpty()) {
                    JsonNode summary = features.get(0).path("properties").path("summary");
                    if (summary.has("distance")) {
                        distanceMetersRaw = summary.path("distance").asDouble();
                    }
                }
            }

            if (distanceMetersRaw == null) {
                log.warn("Routing response missing distance in summary payload");
                return DrivingDistanceResult.unavailable("No route found between coordinates");
            }

            int distanceMeters = (int) Math.round(distanceMetersRaw);

            if (distanceMeters <= properties.getMaxDistanceMeters()) {
                return DrivingDistanceResult.eligible(distanceMeters);
            } else {
                return DrivingDistanceResult.exceeded(distanceMeters);
            }

        } catch (ResourceAccessException ex) {
            log.warn("Routing provider connection or timeout error: {}", ex.getMessage());
            return DrivingDistanceResult.unavailable("Routing provider connection timed out or network error");
        } catch (HttpStatusCodeException ex) {
            log.warn("Routing provider rejected request with HTTP status {}: {}", ex.getStatusCode(), ex.getMessage());
            return DrivingDistanceResult.unavailable("Routing provider error: " + ex.getStatusCode().value());
        } catch (Exception ex) {
            log.warn("Driving distance calculation encountered an unexpected error: {}", ex.getMessage());
            return DrivingDistanceResult.unavailable("Driving distance calculation failed: " + ex.getClass().getSimpleName());
        }
    }
}
