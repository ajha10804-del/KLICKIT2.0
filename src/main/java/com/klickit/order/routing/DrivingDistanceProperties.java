package com.klickit.order.routing;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Strongly typed configuration properties for the road-routing provider.
 */
@Component
@Getter
@Setter
public class DrivingDistanceProperties {

    @Value("${klickit.routing.enabled:true}")
    private boolean enabled = true;

    @Value("${klickit.routing.provider:openrouteservice}")
    private String provider = "openrouteservice";

    @Value("${klickit.routing.base-url:https://api.openrouteservice.org}")
    private String baseUrl = "https://api.openrouteservice.org";

    @Value("${klickit.routing.api-key:}")
    private String apiKey = "";

    @Value("${klickit.routing.connect-timeout-ms:3000}")
    private int connectTimeoutMs = 3000;

    @Value("${klickit.routing.request-timeout-ms:3000}")
    private int requestTimeoutMs = 3000;

    @Value("${klickit.routing.max-distance-meters:5000}")
    private int maxDistanceMeters = 5000;
}
