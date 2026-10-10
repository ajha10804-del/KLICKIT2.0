package com.klickit.common.util;

public final class HaversineDistanceCalculator {

    public static final double EARTH_RADIUS_KM = 6371.0;

    private HaversineDistanceCalculator() {
        // Utility class
    }

    public static boolean isValidCoordinate(Double lat, Double lon) {
        if (lat == null || lon == null) {
            return false;
        }
        if (Double.isNaN(lat) || Double.isInfinite(lat) || Double.isNaN(lon) || Double.isInfinite(lon)) {
            return false;
        }
        return lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0;
    }

    public static double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);

        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_KM * c;
    }

    public static double calculateDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        return calculateDistanceKm(lat1, lon1, lat2, lon2) * 1000.0;
    }
}
