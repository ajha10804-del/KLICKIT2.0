package com.klickit.delivery.service;

import com.klickit.common.exception.ResourceNotFoundException;
import com.klickit.delivery.dto.CreateDeliveryPartnerRequest;
import com.klickit.delivery.dto.DeliveryPartnerResponse;
import com.klickit.delivery.entity.DeliveryPartner;
import com.klickit.delivery.repository.DeliveryPartnerRepository;
import com.klickit.order.dto.OrderResponse;
import com.klickit.order.entity.Order;
import com.klickit.order.entity.OrderStatus;
import com.klickit.order.repository.OrderRepository;
import com.klickit.user.entity.User;
import com.klickit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import com.klickit.user.entity.Role;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryService {

    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public DeliveryPartnerResponse createPartner(CreateDeliveryPartnerRequest request) {
        String trimmedEmail = request.getEmail() != null ? request.getEmail().trim() : "";
        String trimmedPhone = request.getPhone() != null ? request.getPhone().trim() : "";
        String trimmedName = request.getName() != null ? request.getName().trim() : "";

        if (userRepository.existsByEmail(trimmedEmail)) {
            throw new IllegalStateException("User email already exists");
        }
        if (userRepository.existsByPhone(trimmedPhone)) {
            throw new IllegalStateException("User phone already exists");
        }
        if (deliveryPartnerRepository.findByPhone(trimmedPhone).isPresent()) {
            throw new IllegalStateException("Delivery partner phone already exists");
        }

        User user = User.builder()
                .name(trimmedName)
                .email(trimmedEmail)
                .phone(trimmedPhone)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.DELIVERY_PARTNER)
                .build();
        User savedUser = userRepository.save(user);

        DeliveryPartner partner = DeliveryPartner.builder()
                .name(trimmedName)
                .phone(trimmedPhone)
                .user(savedUser)
                .build();
        DeliveryPartner savedPartner = deliveryPartnerRepository.save(partner);
        return DeliveryPartnerResponse.from(savedPartner);
    }

    public List<DeliveryPartnerResponse> getAllPartners() {
        return deliveryPartnerRepository.findAll().stream()
                .map(DeliveryPartnerResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyAssignedOrders() {
        DeliveryPartner partner = getAuthenticatedDeliveryPartner();
        return getOrdersForPartner(partner.getId());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAssignedOrders(UUID partnerId) {
        DeliveryPartner currentPartner = getAuthenticatedDeliveryPartner();
        if (!currentPartner.getId().equals(partnerId)) {
            throw new AccessDeniedException("You are not authorized to access orders for another delivery partner");
        }
        return getOrdersForPartner(currentPartner.getId());
    }

    @Transactional
    public OrderResponse startDelivery(UUID orderId) {
        DeliveryPartner currentPartner = getAuthenticatedDeliveryPartner();

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (order.getDeliveryPartnerId() == null || !order.getDeliveryPartnerId().equals(currentPartner.getId())) {
            throw new AccessDeniedException("You are not authorized to deliver this order");
        }

        if (order.getStatus() != OrderStatus.ASSIGNED) {
            throw new IllegalStateException(
                    String.format("Cannot transition order from %s to %s", order.getStatus(), OrderStatus.OUT_FOR_DELIVERY));
        }

        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        Order updated = orderRepository.save(order);
        return OrderResponse.from(updated);
    }

    @Transactional
    public OrderResponse markDelivered(UUID orderId) {
        DeliveryPartner currentPartner = getAuthenticatedDeliveryPartner();

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (order.getDeliveryPartnerId() == null || !order.getDeliveryPartnerId().equals(currentPartner.getId())) {
            throw new AccessDeniedException("You are not authorized to deliver this order");
        }

        if (order.getStatus() != OrderStatus.OUT_FOR_DELIVERY) {
            throw new IllegalStateException(
                    String.format("Cannot transition order from %s to %s", order.getStatus(), OrderStatus.DELIVERED));
        }

        order.setStatus(OrderStatus.DELIVERED);
        Order updated = orderRepository.save(order);
        return OrderResponse.from(updated);
    }

    public DeliveryPartner getAuthenticatedDeliveryPartner() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("Authentication required for delivery operations");
        }

        String email = auth.getName();
        return deliveryPartnerRepository.findByUserEmail(email)
                .orElseThrow(() -> new AccessDeniedException("Delivery partner profile not found for user: " + email));
    }

    private List<OrderResponse> getOrdersForPartner(UUID partnerId) {
        List<OrderStatus> activeStatuses = List.of(
                OrderStatus.ASSIGNED,
                OrderStatus.OUT_FOR_DELIVERY
        );

        return orderRepository
                .findByDeliveryPartnerIdAndStatusInOrderByDeadlineAsc(partnerId, activeStatuses)
                .stream()
                .map(OrderResponse::from)
                .toList();
    }
}
