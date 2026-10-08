package com.klickit.order;

import com.klickit.cart.entity.Cart;
import com.klickit.cart.entity.CartItem;
import com.klickit.cart.repository.CartRepository;
import com.klickit.delivery.repository.DeliveryPartnerRepository;
import com.klickit.order.dto.CheckoutRequest;
import com.klickit.order.dto.OrderResponse;
import com.klickit.order.entity.Order;
import com.klickit.order.repository.OrderRepository;
import com.klickit.order.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderDeliveryFeeTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private com.klickit.product.repository.ProductRepository productRepository;

    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                cartRepository,
                productRepository,
                deliveryPartnerRepository,
                eventPublisher
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "customer@test.com",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Cart createCart(String sessionId, List<CartItemSpec> items) {
        Cart cart = Cart.builder()
                .sessionId(sessionId)
                .items(new ArrayList<>())
                .build();

        for (CartItemSpec spec : items) {
            UUID pId = UUID.randomUUID();
            CartItem item = CartItem.builder()
                    .cart(cart)
                    .productId(pId)
                    .productName(spec.name())
                    .unitPrice(spec.unitPrice())
                    .quantity(spec.quantity())
                    .build();
            cart.addItem(item);
            com.klickit.product.entity.Product p = com.klickit.product.entity.Product.builder().name(spec.name()).price(spec.unitPrice()).active(true).build();
            p.setId(pId);
            when(productRepository.findById(pId)).thenReturn(Optional.of(p));
        }
        return cart;
    }

    private void mockSaveOrder() {
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(UUID.randomUUID());
            }
            return o;
        });
    }

    private record CartItemSpec(String name, BigDecimal unitPrice, int quantity) {}

    @Test
    @DisplayName("Subtotal under ₹199 charges ₹25 delivery fee (subtotal ₹100 -> fee ₹25, total ₹125)")
    void checkout_subtotalUnder199_charges25Fee() {
        String sessionId = "sess_under_199";
        Cart cart = createCart(sessionId, List.of(
                new CartItemSpec("Item 1", new BigDecimal("50.00"), 2) // subtotal 100.00
        ));
        when(cartRepository.findBySessionId(sessionId)).thenReturn(Optional.of(cart));
        mockSaveOrder();

        CheckoutRequest request = new CheckoutRequest(sessionId, "Alice", "9876543210", "Hostel 1", null);
        OrderResponse response = orderService.checkout(request);

        assertThat(response).isNotNull();
        assertThat(response.getDeliveryFee()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("125.00"));
    }

    @Test
    @DisplayName("Subtotal of exactly ₹199 qualifies for free delivery (subtotal ₹199 -> fee ₹0, total ₹199)")
    void checkout_subtotalExactly199_freeDelivery() {
        String sessionId = "sess_exactly_199";
        Cart cart = createCart(sessionId, List.of(
                new CartItemSpec("Item 199", new BigDecimal("199.00"), 1) // subtotal 199.00
        ));
        when(cartRepository.findBySessionId(sessionId)).thenReturn(Optional.of(cart));
        mockSaveOrder();

        CheckoutRequest request = new CheckoutRequest(sessionId, "Bob", "9876543210", "Hostel 2", null);
        OrderResponse response = orderService.checkout(request);

        assertThat(response).isNotNull();
        assertThat(response.getDeliveryFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("199.00"));
    }

    @Test
    @DisplayName("Subtotal over ₹199 qualifies for free delivery (subtotal ₹250 -> fee ₹0, total ₹250)")
    void checkout_subtotalOver199_freeDelivery() {
        String sessionId = "sess_over_199";
        Cart cart = createCart(sessionId, List.of(
                new CartItemSpec("Item A", new BigDecimal("150.00"), 1),
                new CartItemSpec("Item B", new BigDecimal("100.00"), 1) // subtotal 250.00
        ));
        when(cartRepository.findBySessionId(sessionId)).thenReturn(Optional.of(cart));
        mockSaveOrder();

        CheckoutRequest request = new CheckoutRequest(sessionId, "Charlie", "9876543210", "Hostel 3", null);
        OrderResponse response = orderService.checkout(request);

        assertThat(response).isNotNull();
        assertThat(response.getDeliveryFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
    }

    @Test
    @DisplayName("Delivery fee is stored on the Order entity and returned in OrderResponse")
    void checkout_deliveryFeeStoredAndReturned() {
        String sessionId = "sess_stored";
        Cart cart = createCart(sessionId, List.of(
                new CartItemSpec("Snack", new BigDecimal("20.00"), 2) // subtotal 40.00
        ));
        when(cartRepository.findBySessionId(sessionId)).thenReturn(Optional.of(cart));
        mockSaveOrder();

        CheckoutRequest request = new CheckoutRequest(sessionId, "Dave", "9876543210", "Hostel 4", null);
        OrderResponse response = orderService.checkout(request);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order savedEntity = orderCaptor.getValue();

        assertThat(savedEntity.getDeliveryFee()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(savedEntity.getTotalAmount()).isEqualByComparingTo(new BigDecimal("65.00"));
        assertThat(response.getDeliveryFee()).isEqualByComparingTo(savedEntity.getDeliveryFee());
        assertThat(response.getTotalAmount()).isEqualByComparingTo(savedEntity.getTotalAmount());
    }

    @Test
    @DisplayName("Total amount strictly equals sum of item line totals plus delivery fee")
    void checkout_totalEqualsItemsPlusFee() {
        String sessionId = "sess_item_sum";
        BigDecimal price1 = new BigDecimal("14.50");
        int qty1 = 3; // 43.50
        BigDecimal price2 = new BigDecimal("22.00");
        int qty2 = 2; // 44.00
        BigDecimal expectedSubtotal = price1.multiply(BigDecimal.valueOf(qty1))
                .add(price2.multiply(BigDecimal.valueOf(qty2))); // 87.50

        Cart cart = createCart(sessionId, List.of(
                new CartItemSpec("Item 1", price1, qty1),
                new CartItemSpec("Item 2", price2, qty2)
        ));
        when(cartRepository.findBySessionId(sessionId)).thenReturn(Optional.of(cart));
        mockSaveOrder();

        CheckoutRequest request = new CheckoutRequest(sessionId, "Eve", "9876543210", "Hostel 5", null);
        OrderResponse response = orderService.checkout(request);

        BigDecimal expectedFee = new BigDecimal("25.00");
        BigDecimal expectedTotal = expectedSubtotal.add(expectedFee); // 112.50

        assertThat(response.getDeliveryFee()).isEqualByComparingTo(expectedFee);
        assertThat(response.getTotalAmount()).isEqualByComparingTo(expectedTotal);
    }

    @Test
    @DisplayName("Configurable override: custom fee and threshold properties override defaults")
    void checkout_configurableOverride_works() {
        BigDecimal customFee = new BigDecimal("40.00");
        BigDecimal customThreshold = new BigDecimal("300.00");

        OrderService customOrderService = new OrderService(
                orderRepository,
                cartRepository,
                productRepository,
                deliveryPartnerRepository,
                eventPublisher,
                Clock.systemUTC(),
                customFee,
                customThreshold
        );

        String sessionIdUnder = "sess_custom_under";
        Cart cartUnder = createCart(sessionIdUnder, List.of(
                new CartItemSpec("Item Under", new BigDecimal("250.00"), 1) // 250 < 300
        ));
        when(cartRepository.findBySessionId(sessionIdUnder)).thenReturn(Optional.of(cartUnder));
        mockSaveOrder();

        CheckoutRequest req1 = new CheckoutRequest(sessionIdUnder, "Frank", "9876543210", "Hostel 6", null);
        OrderResponse res1 = customOrderService.checkout(req1);

        assertThat(res1.getDeliveryFee()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(res1.getTotalAmount()).isEqualByComparingTo(new BigDecimal("290.00"));

        String sessionIdOver = "sess_custom_over";
        Cart cartOver = createCart(sessionIdOver, List.of(
                new CartItemSpec("Item Over", new BigDecimal("300.00"), 1) // 300 == 300
        ));
        when(cartRepository.findBySessionId(sessionIdOver)).thenReturn(Optional.of(cartOver));

        CheckoutRequest req2 = new CheckoutRequest(sessionIdOver, "Grace", "9876543210", "Hostel 7", null);
        OrderResponse res2 = customOrderService.checkout(req2);

        assertThat(res2.getDeliveryFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(res2.getTotalAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    @DisplayName("Regression test: single-item order checks out cleanly with delivery fee")
    void checkout_singleItemRegression_stillWorks() {
        String sessionId = "sess_single_item";
        Cart cart = createCart(sessionId, List.of(
                new CartItemSpec("Single Pen", new BigDecimal("10.00"), 1) // subtotal 10.00
        ));
        when(cartRepository.findBySessionId(sessionId)).thenReturn(Optional.of(cart));
        mockSaveOrder();

        CheckoutRequest request = new CheckoutRequest(sessionId, "Heidi", "9876543210", "Hostel 8", null);
        OrderResponse response = orderService.checkout(request);

        assertThat(response).isNotNull();
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getProductName()).isEqualTo("Single Pen");
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(1);
        assertThat(response.getItems().get(0).getPrice()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(response.getDeliveryFee()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("35.00"));
    }
}
