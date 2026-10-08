package com.klickit.order;

import com.klickit.cart.entity.Cart;
import com.klickit.cart.entity.CartItem;
import com.klickit.cart.repository.CartRepository;
import com.klickit.order.dto.CheckoutRequest;
import com.klickit.order.dto.OrderResponse;
import com.klickit.order.entity.Order;
import com.klickit.order.repository.OrderRepository;
import com.klickit.order.service.OrderService;
import com.klickit.product.entity.Product;
import com.klickit.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OrderCheckoutPriceIntegrityTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderService orderService;

    private UUID productId;
    private Cart cart;
    private CheckoutRequest checkoutRequest;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test@user.com", null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
        );

        productId = UUID.randomUUID();
        cart = Cart.builder()
                .sessionId("sess_1")
                .items(new ArrayList<>())
                .build();
        
        CartItem cartItem = CartItem.builder()
                .cart(cart)
                .productId(productId)
                .productName("Milk")
                .unitPrice(new BigDecimal("50.00")) // Stale price
                .quantity(1)
                .build();
        cart.addItem(cartItem);

        checkoutRequest = new CheckoutRequest("sess_1", "Test", "123", "Addr", null);
    }

    @Test
    @DisplayName("Checkout uses authoritative price from database, not stale cart price")
    void checkout_usesDatabasePrice() {
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });
        when(cartRepository.findBySessionId("sess_1")).thenReturn(Optional.of(cart));
        Product liveProduct = Product.builder()
                
                .name("Milk")
                .price(new BigDecimal("60.00")) // New live price
                .active(true)
                .build();
        liveProduct.setId(productId);
        when(productRepository.findById(productId)).thenReturn(Optional.of(liveProduct));

        OrderResponse response = orderService.checkout(checkoutRequest);

        // 1 * 60 = 60 + 25 (delivery) = 85
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("85.00"));
        assertThat(response.getItems().get(0).getPrice()).isEqualByComparingTo(new BigDecimal("60.00"));
    }

    @Test
    @DisplayName("Checkout fails if product is missing")
    void checkout_failsIfProductMissing() {
        when(cartRepository.findBySessionId("sess_1")).thenReturn(Optional.of(cart));
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.checkout(checkoutRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    @DisplayName("Checkout fails if product is inactive")
    void checkout_failsIfProductInactive() {
        when(cartRepository.findBySessionId("sess_1")).thenReturn(Optional.of(cart));
        Product inactiveProduct = Product.builder()
                
                .name("Milk")
                .price(new BigDecimal("60.00"))
                .active(false) // Inactive
                .build();
        inactiveProduct.setId(productId);
        when(productRepository.findById(productId)).thenReturn(Optional.of(inactiveProduct));

        assertThatThrownBy(() -> orderService.checkout(checkoutRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Product is no longer available");
    }

    @Test
    @DisplayName("Normal successful checkout uses correct price")
    void checkout_normalSuccess() {
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });
        when(cartRepository.findBySessionId("sess_1")).thenReturn(Optional.of(cart));
        Product liveProduct = Product.builder()
                
                .name("Milk")
                .price(new BigDecimal("50.00")) // Price unchanged
                .active(true)
                .build();
        liveProduct.setId(productId);
        when(productRepository.findById(productId)).thenReturn(Optional.of(liveProduct));

        OrderResponse response = orderService.checkout(checkoutRequest);

        // 1 * 50 = 50 + 25 = 75
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(response.getItems().get(0).getPrice()).isEqualByComparingTo(new BigDecimal("50.00"));
    }
}
