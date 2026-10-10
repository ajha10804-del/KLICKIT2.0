package com.klickit.order.routing;

import com.klickit.order.entity.DrivingDistanceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Immutable typed result representing the outcome of a driving distance calculation.
 */
@Getter
@Builder
@AllArgsConstructor
public class DrivingDistanceResult {

    private final Integer distanceMeters;
    private final DrivingDistanceStatus status;
    private final String diagnosticReason;

    public static DrivingDistanceResult eligible(int distanceMeters) {
        return new DrivingDistanceResult(distanceMeters, DrivingDistanceStatus.ELIGIBLE, null);
    }

    public static DrivingDistanceResult exceeded(int distanceMeters) {
        return new DrivingDistanceResult(distanceMeters, DrivingDistanceStatus.EXCEEDED, "Distance exceeds maximum allowed threshold");
    }

    public static DrivingDistanceResult unavailable(String diagnosticReason) {
        return new DrivingDistanceResult(null, DrivingDistanceStatus.UNAVAILABLE, diagnosticReason);
    }
}
