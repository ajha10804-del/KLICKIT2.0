package com.klickit.tracking.controller;

import com.klickit.common.dto.ApiResponse;
import com.klickit.tracking.dto.LocationUpdateRequest;
import com.klickit.tracking.dto.TrackingResponse;
import com.klickit.tracking.service.TrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/tracking")
@RequiredArgsConstructor
@Tag(name = "Tracking", description = "Live delivery tracking and rider GPS updates")
public class TrackingController {

    private final TrackingService trackingService;

    @Operation(summary = "Get live tracking for an order", description = "Retrieve live tracking information for an order (requires authenticated customer, assigned driver, or admin)")
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<TrackingResponse>> getTracking(@PathVariable UUID orderId) {
        TrackingResponse response = trackingService.getTracking(orderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Update live delivery location for an assigned order", description = "Update live rider GPS location for an assigned order (requires authenticated assigned delivery partner)")
    @PostMapping("/{orderId}/location")
    public ResponseEntity<ApiResponse<TrackingResponse>> updateLocation(
            @PathVariable UUID orderId,
            @Valid @RequestBody LocationUpdateRequest request) {
        TrackingResponse response = trackingService.updateDeliveryLocation(orderId, request);
        return ResponseEntity.ok(ApiResponse.success("Location updated successfully", response));
    }
}
