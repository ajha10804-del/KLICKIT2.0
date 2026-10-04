package com.klickit.order.controller;

import com.klickit.common.dto.ApiResponse;
import com.klickit.order.dto.AssignDeliveryRequest;
import com.klickit.order.dto.OrderResponse;
import com.klickit.order.dto.UpdateOrderStatusRequest;
import com.klickit.order.entity.OrderStatus;
import com.klickit.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
@Tag(name = "Admin Orders", description = "Admin-only order management and delivery assignment APIs")
public class AdminOrderController {

    private final OrderService orderService;

    @Operation(summary = "Get all orders", description = "Admin-only endpoint to retrieve all orders or filter by status")
    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrders(
            @RequestParam(required = false) OrderStatus status) {
        List<OrderResponse> orders = (status != null)
                ? orderService.getOrdersByStatus(status)
                : orderService.getAllOrders();
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @Operation(summary = "Get order by ID", description = "Admin-only endpoint to retrieve order details by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable UUID id) {
        OrderResponse order = orderService.getOrderById(id);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @Operation(summary = "Approve order", description = "Admin-only endpoint to approve a placed order into READY_TO_ASSIGN state")
    @RequestMapping(value = "/{id}/approve", method = {RequestMethod.POST, RequestMethod.PATCH})
    public ResponseEntity<ApiResponse<OrderResponse>> approveOrder(@PathVariable UUID id) {
        OrderResponse order = orderService.approveOrder(id);
        return ResponseEntity.ok(ApiResponse.success("Order approved", order));
    }

    @Operation(summary = "Assign delivery partner", description = "Admin-only endpoint to assign an order to a delivery partner")
    @PatchMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<OrderResponse>> assignDeliveryPartner(
            @PathVariable UUID id,
            @Valid @RequestBody AssignDeliveryRequest request) {
        OrderResponse order = orderService.assignDeliveryPartner(id, request);
        return ResponseEntity.ok(ApiResponse.success("Delivery partner assigned", order));
    }

    @Operation(summary = "Reject order", description = "Admin-only endpoint to reject a pending order")
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<OrderResponse>> rejectOrder(@PathVariable UUID id) {
        OrderResponse order = orderService.rejectOrder(id);
        return ResponseEntity.ok(ApiResponse.success("Order rejected", order));
    }

    @Operation(summary = "Update order status", description = "Admin-only endpoint to update an order's lifecycle status")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        OrderResponse order = orderService.updateStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Order status updated", order));
    }
}
