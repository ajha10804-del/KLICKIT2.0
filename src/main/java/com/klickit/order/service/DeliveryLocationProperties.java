package com.klickit.order.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Strongly typed configuration properties for delivery location validation,
 * store service radius enforcement, and campus gate geofencing.
 *
 * Campus radius (1100 m) is derived from the geodesic distance between
 * VIT Bhopal Main Gate (23.075611, 76.850082) and the Block 6 hostel (23.075327, 76.860658),
 * which is ~1082.37 m. Setting the radius to 1100 m reliably covers the residential campus.
 */
@Component
public class DeliveryLocationProperties {

    private final double storeLatitude;
    private final double storeLongitude;
    private final double maxRadiusKm;
    private final double campusGateLatitude;
    private final double campusGateLongitude;
    private final double campusRadiusM;
    private final boolean enforceRange;

    public DeliveryLocationProperties() {
        this(23.073427650432762, 76.82864818972119, 6.0, 23.075611, 76.850082, 1100.0, false);
    }

    @Autowired
    public DeliveryLocationProperties(
            @Value("${klickit.delivery.store.latitude:23.073427650432762}") double storeLatitude,
            @Value("${klickit.delivery.store.longitude:76.82864818972119}") double storeLongitude,
            @Value("${klickit.delivery.store.max-radius-km:6.0}") double maxRadiusKm,
            @Value("${klickit.delivery.campus.gate-latitude:23.075611}") double campusGateLatitude,
            @Value("${klickit.delivery.campus.gate-longitude:76.850082}") double campusGateLongitude,
            @Value("${klickit.delivery.campus.radius-m:1100.0}") double campusRadiusM,
            @Value("${klickit.delivery.enforce-range:true}") boolean enforceRange) {
        this.storeLatitude = storeLatitude;
        this.storeLongitude = storeLongitude;
        this.maxRadiusKm = maxRadiusKm;
        this.campusGateLatitude = campusGateLatitude;
        this.campusGateLongitude = campusGateLongitude;
        this.campusRadiusM = campusRadiusM;
        this.enforceRange = enforceRange;
    }

    public double getStoreLatitude() {
        return storeLatitude;
    }

    public double getStoreLongitude() {
        return storeLongitude;
    }

    public double getMaxRadiusKm() {
        return maxRadiusKm;
    }

    public double getCampusGateLatitude() {
        return campusGateLatitude;
    }

    public double getCampusGateLongitude() {
        return campusGateLongitude;
    }

    public double getCampusRadiusM() {
        return campusRadiusM;
    }

    public boolean isEnforceRange() {
        return enforceRange;
    }
}
