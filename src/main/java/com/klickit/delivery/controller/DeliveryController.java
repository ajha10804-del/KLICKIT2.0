package com.klickit.delivery.controller;

import com.klickit.common.dto.ApiResponse;
import com.klickit.delivery.dto.CreateDeliveryPartnerRequest;
import com.klickit.delivery.dto.DeliveryPartnerResponse;
import com.klickit.delivery.service.DeliveryService;
import com.klickit.order.dto.LocationUpdateRequest;
import com.klickit.order.dto.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/delivery")
@RequiredArgsConstructor
@Tag(name = "Delivery", description = "Delivery partner fulfillment and assignment APIs")
public class DeliveryController {

    private final DeliveryService deliveryService;

    @Operation(summary = "Provision a delivery partner", description = "Admin-only endpoint to provision a new driver (creates User and DeliveryPartner)")
    @PostMapping("/partners")
    public ResponseEntity<ApiResponse<DeliveryPartnerResponse>> createPartner(
            @Valid @RequestBody CreateDeliveryPartnerRequest request) {
        DeliveryPartnerResponse partner = deliveryService.createPartner(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Delivery partner created", partner));
    }

    @Operation(summary = "Get all delivery partners", description = "Retrieve list of all active delivery partners")
    @GetMapping("/partners")
    public ResponseEntity<ApiResponse<List<DeliveryPartnerResponse>>> getAllPartners() {
        List<DeliveryPartnerResponse> partners = deliveryService.getAllPartners();
        return ResponseEntity.ok(ApiResponse.success(partners));
    }

    @Operation(summary = "Get my assigned orders", description = "Delivery partner endpoint to retrieve orders assigned to the authenticated partner")
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyAssignedOrders() {
        List<OrderResponse> orders = deliveryService.getMyAssignedOrders();
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @Operation(summary = "Get assigned orders by partner ID", description = "Retrieve assigned orders for a specific delivery partner")
    @GetMapping("/orders/{partnerId}")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAssignedOrders(
            @PathVariable UUID partnerId) {
        List<OrderResponse> orders = deliveryService.getAssignedOrders(partnerId);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @Operation(summary = "Start delivery", description = "Delivery partner marks an assigned order as OUT_FOR_DELIVERY")
    @PatchMapping({"/orders/{orderId}/start", "/orders/{orderId}/out-for-delivery"})
    public ResponseEntity<ApiResponse<OrderResponse>> startDelivery(
            @PathVariable UUID orderId) {
        OrderResponse order = deliveryService.startDelivery(orderId);
        return ResponseEntity.ok(ApiResponse.success("Order marked as out for delivery", order));
    }

    @Operation(summary = "Share live delivery location", description = "Assigned delivery partner updates GPS while the order is OUT_FOR_DELIVERY")
    @PatchMapping("/orders/{orderId}/location")
    public ResponseEntity<ApiResponse<OrderResponse>> updateLiveLocation(
            @PathVariable UUID orderId,
            @Valid @RequestBody LocationUpdateRequest request) {
        OrderResponse order = deliveryService.updateLiveLocation(orderId, request);
        return ResponseEntity.ok(ApiResponse.success("Live location updated", order));
    }

    @Operation(summary = "Mark order as delivered", description = "Delivery partner marks an assigned order as DELIVERED")
    @PatchMapping("/orders/{orderId}/delivered")
    public ResponseEntity<ApiResponse<OrderResponse>> markDelivered(
            @PathVariable UUID orderId) {
        OrderResponse order = deliveryService.markDelivered(orderId);
        return ResponseEntity.ok(ApiResponse.success("Order marked as delivered", order));
    }
}
