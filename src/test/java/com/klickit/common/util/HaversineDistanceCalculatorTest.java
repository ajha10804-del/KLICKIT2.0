package com.klickit.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HaversineDistanceCalculatorTest {

    @Test
    @DisplayName("Identical points produce 0.0 km and 0.0 meters")
    void identicalPoints_returnZero() {
        double lat = 22.7196;
        double lon = 75.8577;

        double km = HaversineDistanceCalculator.calculateDistanceKm(lat, lon, lat, lon);
        double meters = HaversineDistanceCalculator.calculateDistanceMeters(lat, lon, lat, lon);

        assertThat(km).isCloseTo(0.0, within(0.0001));
        assertThat(meters).isCloseTo(0.0, within(0.1));
    }

    @Test
    @DisplayName("Known reference distance between Indore Store and VIT Main Gate is ~240 meters")
    void referencePoints_distanceMatchesKnownProximity() {
        double storeLat = 22.7196;
        double storeLon = 75.8577;
        double gateLat = 22.7200;
        double gateLon = 75.8600;

        double km = HaversineDistanceCalculator.calculateDistanceKm(storeLat, storeLon, gateLat, gateLon);
        double meters = HaversineDistanceCalculator.calculateDistanceMeters(storeLat, storeLon, gateLat, gateLon);

        assertThat(km).isCloseTo(0.24, within(0.05));
        assertThat(meters).isCloseTo(240.0, within(50.0));
    }

    @Test
    @DisplayName("isValidCoordinate returns true for valid coordinates within [-90, 90] and [-180, 180]")
    void isValidCoordinate_validPoints_returnsTrue() {
        assertThat(HaversineDistanceCalculator.isValidCoordinate(0.0, 0.0)).isTrue();
        assertThat(HaversineDistanceCalculator.isValidCoordinate(90.0, 180.0)).isTrue();
        assertThat(HaversineDistanceCalculator.isValidCoordinate(-90.0, -180.0)).isTrue();
        assertThat(HaversineDistanceCalculator.isValidCoordinate(22.7196, 75.8577)).isTrue();
    }

    @Test
    @DisplayName("isValidCoordinate returns false for null, NaN, infinite, or out-of-bounds coordinates")
    void isValidCoordinate_invalidPoints_returnsFalse() {
        assertThat(HaversineDistanceCalculator.isValidCoordinate(null, 75.0)).isFalse();
        assertThat(HaversineDistanceCalculator.isValidCoordinate(22.0, null)).isFalse();
        assertThat(HaversineDistanceCalculator.isValidCoordinate(null, null)).isFalse();

        assertThat(HaversineDistanceCalculator.isValidCoordinate(Double.NaN, 75.0)).isFalse();
        assertThat(HaversineDistanceCalculator.isValidCoordinate(22.0, Double.NaN)).isFalse();
        assertThat(HaversineDistanceCalculator.isValidCoordinate(Double.POSITIVE_INFINITY, 75.0)).isFalse();
        assertThat(HaversineDistanceCalculator.isValidCoordinate(22.0, Double.NEGATIVE_INFINITY)).isFalse();

        assertThat(HaversineDistanceCalculator.isValidCoordinate(90.1, 75.0)).isFalse();
        assertThat(HaversineDistanceCalculator.isValidCoordinate(-90.1, 75.0)).isFalse();
        assertThat(HaversineDistanceCalculator.isValidCoordinate(22.0, 180.1)).isFalse();
        assertThat(HaversineDistanceCalculator.isValidCoordinate(22.0, -180.1)).isFalse();
    }
}
