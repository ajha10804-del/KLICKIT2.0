package com.klickit.order.entity;

/**
 * Status of the road driving distance verification for an order.
 *
 * ELIGIBLE: Verified road distance is less than or equal to the maximum allowed threshold (5,000 metres).
 * EXCEEDED: Verified road distance exceeds the maximum allowed threshold.
 * UNAVAILABLE: Distance could not be calculated (e.g. routing timeout, invalid coordinates, missing route).
 * PENDING: Calculation has not yet completed or has been scheduled.
 */
public enum DrivingDistanceStatus {
    PENDING,
    ELIGIBLE,
    EXCEEDED,
    UNAVAILABLE
}
