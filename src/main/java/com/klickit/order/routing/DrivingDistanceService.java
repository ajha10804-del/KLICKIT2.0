package com.klickit.order.routing;

/**
 * Provider-neutral service abstraction for calculating road-network driving distance.
 */
public interface DrivingDistanceService {

    /**
     * Calculates road-network driving distance between the origin and destination coordinates.
     *
     * @param originLat Origin latitude
     * @param originLng Origin longitude
     * @param destLat   Destination latitude
     * @param destLng   Destination longitude
     * @return DrivingDistanceResult containing distance in metres, calculation status, and safe diagnostic reason
     */
    DrivingDistanceResult calculateDistance(double originLat, double originLng, double destLat, double destLng);
}
