package com.klickit.cart.service;

import com.klickit.cart.dto.AddToCartRequest;
import com.klickit.cart.dto.CartResponse;
import com.klickit.cart.dto.RemoveFromCartRequest;
import com.klickit.cart.entity.Cart;
import com.klickit.cart.entity.CartItem;
import com.klickit.cart.repository.CartRepository;
import com.klickit.common.exception.ResourceNotFoundException;
import com.klickit.product.entity.Product;
import com.klickit.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    @Transactional
    public CartResponse getCart(String sessionId) {
        String currentUserEmail = getCurrentUserEmail();
        Optional<Cart> cartOpt = cartRepository.findBySessionId(sessionId);
        if (cartOpt.isEmpty()) {
            return CartResponse.builder()
                    .sessionId(sessionId)
                    .items(List.of())
                    .itemCount(0)
                    .subtotal(BigDecimal.ZERO)
                    .build();
        }

        Cart cart = cartOpt.get();
        validateAndClaimOwnership(cart, currentUserEmail);
        return CartResponse.from(cart);
    }

    @Transactional
    public CartResponse addToCart(AddToCartRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getProductId()));

        if (!product.isActive()) {
            throw new IllegalStateException("Product is not available");
        }

        String currentUserEmail = getCurrentUserEmail();
        Cart cart = cartRepository.findBySessionId(request.getSessionId())
                .orElseGet(() -> Cart.builder()
                        .sessionId(request.getSessionId())
                        .customerEmail(currentUserEmail)
                        .build());

        validateAndClaimOwnership(cart, currentUserEmail);

        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getProductId().equals(request.getProductId()))
                .findFirst();

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + request.getQuantity());
            item.setUnitPrice(product.getPrice());
        } else {
            CartItem newItem = CartItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .unitPrice(product.getPrice())
                    .quantity(request.getQuantity())
                    .build();
            cart.addItem(newItem);
        }

        Cart saved = cartRepository.save(cart);
        return CartResponse.from(saved);
    }

    @Transactional
    public CartResponse removeFromCart(RemoveFromCartRequest request) {
        String currentUserEmail = getCurrentUserEmail();
        Cart cart = cartRepository.findBySessionId(request.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "sessionId", request.getSessionId()));

        validateAndClaimOwnership(cart, currentUserEmail);

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(request.getProductId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "productId", request.getProductId()));

        cart.removeItem(item);
        Cart saved = cartRepository.save(cart);
        return CartResponse.from(saved);
    }

    /**
     * Enforces explicit cart ownership rules:
     * 1. Unowned guest cart + Anonymous guest -> Allow
     * 2. Unowned guest cart + Authenticated customer -> Claim it atomically for that customer
     * 3. Customer A's cart + Customer A -> Allow
     * 4. Customer A's cart + Customer B -> Reject with 403 Forbidden (AccessDeniedException)
     * 5. Customer A's cart + Anonymous guest -> Reject with 403 Forbidden (AccessDeniedException)
     */
    public void validateAndClaimOwnership(Cart cart, String currentUserEmail) {
        String owner = cart.getCustomerEmail();
        if (owner == null || owner.isBlank()) {
            // Unowned guest cart: if authenticated, claim it atomically
            if (currentUserEmail != null && !currentUserEmail.isBlank()) {
                String normalizedEmail = currentUserEmail.trim().toLowerCase();
                int rowsUpdated = cartRepository.claimCartForCustomer(cart.getSessionId(), normalizedEmail);
                if (rowsUpdated > 0) {
                    cart.setCustomerEmail(normalizedEmail);
                } else {
                    // Concurrent transaction claimed it first or set an owner; reload to verify
                    Cart reloaded = cartRepository.findBySessionId(cart.getSessionId()).orElse(cart);
                    String updatedOwner = reloaded.getCustomerEmail();
                    if (updatedOwner != null && !updatedOwner.equalsIgnoreCase(normalizedEmail)) {
                        throw new org.springframework.security.access.AccessDeniedException("Access denied to cart");
                    }
                    cart.setCustomerEmail(normalizedEmail);
                }
            }
            return;
        }

        // Customer-owned cart: must match authenticated caller exactly (case-insensitive)
        if (currentUserEmail == null || !owner.equalsIgnoreCase(currentUserEmail.trim())) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied to cart");
        }
    }

    private String getCurrentUserEmail() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getName();
    }
}
