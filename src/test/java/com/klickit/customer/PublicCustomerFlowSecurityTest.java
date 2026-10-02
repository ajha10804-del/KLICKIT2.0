package com.klickit.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klickit.cart.controller.CartController;
import com.klickit.cart.dto.AddToCartRequest;
import com.klickit.cart.dto.CartItemResponse;
import com.klickit.cart.dto.CartResponse;
import com.klickit.cart.dto.RemoveFromCartRequest;
import com.klickit.cart.service.CartService;
import com.klickit.config.JwtAuthenticationEntryPoint;
import com.klickit.config.JwtAuthenticationFilter;
import com.klickit.config.JwtService;
import com.klickit.config.SecurityConfig;
import com.klickit.order.controller.OrderController;
import com.klickit.order.dto.CheckoutRequest;
import com.klickit.order.dto.OrderResponse;
import com.klickit.order.entity.OrderStatus;
import com.klickit.order.service.OrderService;
import com.klickit.product.controller.ProductController;
import com.klickit.product.dto.ProductResponse;
import com.klickit.product.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ProductController.class, CartController.class, OrderController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class PublicCustomerFlowSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @MockBean
    private CartService cartService;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("GET /products - anonymous customer can browse products (200 OK)")
    void anonymous_canBrowseProducts() throws Exception {
        ProductResponse product = ProductResponse.builder()
                .id(UUID.randomUUID())
                .name("Maggi 2-Minute Noodles")
                .description("Instant noodles")
                .price(new BigDecimal("14.00"))
                .category("Groceries")
                .active(true)
                .build();

        when(productService.getAllProducts()).thenReturn(List.of(product));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Maggi 2-Minute Noodles"));
    }

    @Test
    @DisplayName("GET /products/{id} - anonymous customer can view single valid product (200 OK)")
    void anonymous_canViewSingleProduct() throws Exception {
        UUID productId = UUID.randomUUID();
        ProductResponse product = ProductResponse.builder()
                .id(productId)
                .name("Coca-Cola 500ml")
                .description("Cold beverage")
                .price(new BigDecimal("40.00"))
                .category("Beverages")
                .active(true)
                .build();

        when(productService.getProductById(productId)).thenReturn(product);

        mockMvc.perform(get("/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Coca-Cola 500ml"))
                .andExpect(jsonPath("$.data.price").value(40.00));
    }

    @Test
    @DisplayName("POST /cart/add - anonymous customer can add item to cart without JWT (200 OK)")
    void anonymous_canAddToCart() throws Exception {
        UUID productId = UUID.randomUUID();
        AddToCartRequest request = new AddToCartRequest("sess-12345", productId, 2);

        CartItemResponse item = CartItemResponse.builder()
                .productId(productId)
                .productName("Maggi 2-Minute Noodles")
                .unitPrice(new BigDecimal("14.00"))
                .quantity(2)
                .lineTotal(new BigDecimal("28.00"))
                .build();

        CartResponse cartResponse = CartResponse.builder()
                .sessionId("sess-12345")
                .items(List.of(item))
                .itemCount(2)
                .subtotal(new BigDecimal("28.00"))
                .build();

        when(cartService.addToCart(any(AddToCartRequest.class))).thenReturn(cartResponse);

        mockMvc.perform(post("/cart/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").value("sess-12345"))
                .andExpect(jsonPath("$.data.itemCount").value(2))
                .andExpect(jsonPath("$.data.subtotal").value(28.00));
    }

    @Test
    @DisplayName("POST /cart/remove - anonymous customer can remove item from cart without JWT (200 OK)")
    void anonymous_canRemoveFromCart() throws Exception {
        UUID productId = UUID.randomUUID();
        RemoveFromCartRequest request = new RemoveFromCartRequest("sess-12345", productId);

        CartResponse cartResponse = CartResponse.builder()
                .sessionId("sess-12345")
                .items(List.of())
                .itemCount(0)
                .subtotal(BigDecimal.ZERO)
                .build();

        when(cartService.removeFromCart(any(RemoveFromCartRequest.class))).thenReturn(cartResponse);

        mockMvc.perform(post("/cart/remove")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").value("sess-12345"))
                .andExpect(jsonPath("$.data.itemCount").value(0));
    }

    @Test
    @DisplayName("GET /cart/{sessionId} - anonymous customer can retrieve cart without JWT (200 OK)")
    void anonymous_canRetrieveCart() throws Exception {
        CartResponse emptyCart = CartResponse.builder()
                .sessionId("sess-12345")
                .items(List.of())
                .itemCount(0)
                .subtotal(BigDecimal.ZERO)
                .build();

        when(cartService.getCart("sess-12345")).thenReturn(emptyCart);

        mockMvc.perform(get("/cart/sess-12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.itemCount").value(0))
                .andExpect(jsonPath("$.data.subtotal").value(0));
    }

    @Test
    @DisplayName("POST /orders/checkout - anonymous customer cannot checkout without JWT (401 Unauthorized)")
    void anonymous_checkoutWithoutJwt_returns401() throws Exception {
        CheckoutRequest request = new CheckoutRequest(
                "sess-12345",
                "Rahul Sharma",
                "+919876543210",
                "Hostel 4, Room 204, Campus",
                Instant.now().plusSeconds(1800)
        );

        mockMvc.perform(post("/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Sensitive order management endpoints remain protected (401 Unauthorized without JWT)")
    void nonPublicEndpoints_remainProtected() throws Exception {
        // Customer cannot view all orders without JWT
        mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized());

        // Anonymous user cannot update order status
        mockMvc.perform(patch("/orders/" + UUID.randomUUID() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isUnauthorized());

        // Anonymous user cannot create products
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"price\":10,\"category\":\"Test\"}"))
                .andExpect(status().isUnauthorized());

        // Anonymous user cannot delete products
        mockMvc.perform(delete("/products/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
