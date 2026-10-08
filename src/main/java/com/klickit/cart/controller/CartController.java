package com.klickit.cart.controller;

import com.klickit.cart.dto.AddToCartRequest;
import com.klickit.cart.dto.CartResponse;
import com.klickit.cart.dto.RemoveFromCartRequest;
import com.klickit.cart.service.CartService;
import com.klickit.common.dto.ApiResponse;
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

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Shopping cart operations for anonymous and customer sessions")
public class CartController {

    private final CartService cartService;

    @Operation(summary = "Get cart by session ID", description = "Public endpoint to retrieve the current cart contents, item count, and subtotal for a given session")
    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<CartResponse>> getCart(@PathVariable String sessionId) {
        CartResponse cart = cartService.getCart(sessionId);
        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    @Operation(summary = "Add item to cart", description = "Public endpoint to add a product or increment its quantity in the specified session cart")
    @PostMapping("/add")
    public ResponseEntity<ApiResponse<CartResponse>> addToCart(
            @Valid @RequestBody AddToCartRequest request) {
        CartResponse cart = cartService.addToCart(request);
        return ResponseEntity.ok(ApiResponse.success("Item added to cart", cart));
    }

    @Operation(summary = "Remove item from cart", description = "Public endpoint to remove a product or reduce its quantity in the specified session cart")
    @PostMapping("/remove")
    public ResponseEntity<ApiResponse<CartResponse>> removeFromCart(
            @Valid @RequestBody RemoveFromCartRequest request) {
        CartResponse cart = cartService.removeFromCart(request);
        return ResponseEntity.ok(ApiResponse.success("Item removed from cart", cart));
    }
}
