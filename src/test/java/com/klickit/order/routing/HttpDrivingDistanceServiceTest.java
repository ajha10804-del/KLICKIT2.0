package com.klickit.order.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klickit.order.entity.DrivingDistanceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpDrivingDistanceServiceTest {

    private DrivingDistanceProperties properties;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;
    private HttpDrivingDistanceService distanceService;

    private static final double STORE_LAT = 23.073427;
    private static final double STORE_LON = 76.828648;
    private static final double DEST_LAT = 23.075611;
    private static final double DEST_LON = 76.850082;

    @BeforeEach
    void setUp() {
        properties = new DrivingDistanceProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://api.openrouteservice.org");
        properties.setApiKey("test-secret-ors-key");
        properties.setMaxDistanceMeters(5000);
        properties.setConnectTimeoutMs(1000);
        properties.setRequestTimeoutMs(1000);

        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        objectMapper = new ObjectMapper();

        distanceService = new HttpDrivingDistanceService(properties, restClientBuilder.build(), objectMapper);
    }

    @Test
    @DisplayName("Valid route response <= 5,000m returns ELIGIBLE")
    void validRoute_under5000m_returnsEligible() {
        String jsonResponse = """
                {
                    "routes": [
                        {
                            "summary": {
                                "distance": 4850.4,
                                "duration": 420.0
                            }
                        }
                    ]
                }
                """;

        mockServer.expect(requestTo("https://api.openrouteservice.org/v2/directions/driving-car"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "test-secret-ors-key"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        DrivingDistanceResult result = distanceService.calculateDistance(STORE_LAT, STORE_LON, DEST_LAT, DEST_LON);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(DrivingDistanceStatus.ELIGIBLE);
        assertThat(result.getDistanceMeters()).isEqualTo(4850);
        assertThat(result.getDiagnosticReason()).isNull();
    }

    @Test
    @DisplayName("Valid route response exactly 5,000m returns ELIGIBLE")
    void validRoute_exactly5000m_returnsEligible() {
        String jsonResponse = """
                {
                    "routes": [
                        {
                            "summary": {
                                "distance": 5000.0,
                                "duration": 500.0
                            }
                        }
                    ]
                }
                """;

        mockServer.expect(requestTo("https://api.openrouteservice.org/v2/directions/driving-car"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        DrivingDistanceResult result = distanceService.calculateDistance(STORE_LAT, STORE_LON, DEST_LAT, DEST_LON);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(DrivingDistanceStatus.ELIGIBLE);
        assertThat(result.getDistanceMeters()).isEqualTo(5000);
    }

    @Test
    @DisplayName("Valid route response at 5,001m returns EXCEEDED")
    void validRoute_at5001m_returnsExceeded() {
        String jsonResponse = """
                {
                    "routes": [
                        {
                            "summary": {
                                "distance": 5001.0,
                                "duration": 510.0
                            }
                        }
                    ]
                }
                """;

        mockServer.expect(requestTo("https://api.openrouteservice.org/v2/directions/driving-car"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        DrivingDistanceResult result = distanceService.calculateDistance(STORE_LAT, STORE_LON, DEST_LAT, DEST_LON);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(DrivingDistanceStatus.EXCEEDED);
        assertThat(result.getDistanceMeters()).isEqualTo(5001);
        assertThat(result.getDiagnosticReason()).contains("exceeds");
    }

    @Test
    @DisplayName("GeoJSON features format is parsed accurately")
    void geoJsonFeaturesFormat_parsedAccurately() {
        String jsonResponse = """
                {
                    "features": [
                        {
                            "properties": {
                                "summary": {
                                    "distance": 3200.0,
                                    "duration": 300.0
                                }
                            }
                        }
                    ]
                }
                """;

        mockServer.expect(requestTo("https://api.openrouteservice.org/v2/directions/driving-car"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        DrivingDistanceResult result = distanceService.calculateDistance(STORE_LAT, STORE_LON, DEST_LAT, DEST_LON);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(DrivingDistanceStatus.ELIGIBLE);
        assertThat(result.getDistanceMeters()).isEqualTo(3200);
    }

    @Test
    @DisplayName("Empty or missing apiKey returns UNAVAILABLE without network call")
    void missingApiKey_returnsUnavailable() {
        properties.setApiKey("");

        DrivingDistanceResult result = distanceService.calculateDistance(STORE_LAT, STORE_LON, DEST_LAT, DEST_LON);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(DrivingDistanceStatus.UNAVAILABLE);
        assertThat(result.getDiagnosticReason()).contains("API key is not configured");
    }

    @Test
    @DisplayName("Routing disabled returns UNAVAILABLE without network call")
    void routingDisabled_returnsUnavailable() {
        properties.setEnabled(false);

        DrivingDistanceResult result = distanceService.calculateDistance(STORE_LAT, STORE_LON, DEST_LAT, DEST_LON);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(DrivingDistanceStatus.UNAVAILABLE);
        assertThat(result.getDiagnosticReason()).contains("disabled");
    }

    @Test
    @DisplayName("Invalid coordinates return UNAVAILABLE without network call")
    void invalidCoordinates_returnUnavailable() {
        DrivingDistanceResult result = distanceService.calculateDistance(95.0, STORE_LON, DEST_LAT, DEST_LON);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(DrivingDistanceStatus.UNAVAILABLE);
        assertThat(result.getDiagnosticReason()).contains("Invalid geographic coordinates");
    }

    @Test
    @DisplayName("Provider HTTP 500 error produces safe UNAVAILABLE status")
    void providerServerError_returnsUnavailable() {
        mockServer.expect(requestTo("https://api.openrouteservice.org/v2/directions/driving-car"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        DrivingDistanceResult result = distanceService.calculateDistance(STORE_LAT, STORE_LON, DEST_LAT, DEST_LON);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(DrivingDistanceStatus.UNAVAILABLE);
        assertThat(result.getDiagnosticReason()).contains("500");
    }

    @Test
    @DisplayName("Provider HTTP 404 / no route produces safe UNAVAILABLE status")
    void providerNoRoute_returnsUnavailable() {
        mockServer.expect(requestTo("https://api.openrouteservice.org/v2/directions/driving-car"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        DrivingDistanceResult result = distanceService.calculateDistance(STORE_LAT, STORE_LON, DEST_LAT, DEST_LON);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(DrivingDistanceStatus.UNAVAILABLE);
    }

    @Test
    @DisplayName("Malformed JSON response produces safe UNAVAILABLE status")
    void malformedJsonResponse_returnsUnavailable() {
        mockServer.expect(requestTo("https://api.openrouteservice.org/v2/directions/driving-car"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("not valid json", MediaType.APPLICATION_JSON));

        DrivingDistanceResult result = distanceService.calculateDistance(STORE_LAT, STORE_LON, DEST_LAT, DEST_LON);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(DrivingDistanceStatus.UNAVAILABLE);
    }
}
