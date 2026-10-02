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

    public CartResponse getCart(String sessionId) {
        return cartRepository.findBySessionId(sessionId)
                .map(CartResponse::from)
                .orElseGet(() -> CartResponse.builder()
                        .sessionId(sessionId)
                        .items(List.of())
                        .itemCount(0)
                        .subtotal(BigDecimal.ZERO)
                        .build());
    }

    @Transactional
    public CartResponse addToCart(AddToCartRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getProductId()));

        if (!product.isActive()) {
            throw new IllegalStateException("Product is not available");
        }

        Cart cart = cartRepository.findBySessionId(request.getSessionId())
                .orElseGet(() -> Cart.builder()
                        .sessionId(request.getSessionId())
                        .build());

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
        Cart cart = cartRepository.findBySessionId(request.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "sessionId", request.getSessionId()));

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(request.getProductId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "productId", request.getProductId()));

        cart.removeItem(item);
        Cart saved = cartRepository.save(cart);
        return CartResponse.from(saved);
    }
}
