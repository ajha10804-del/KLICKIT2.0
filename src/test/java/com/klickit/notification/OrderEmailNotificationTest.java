package com.klickit.notification;

import com.klickit.cart.entity.Cart;
import com.klickit.cart.entity.CartItem;
import com.klickit.cart.repository.CartRepository;
import com.klickit.delivery.repository.DeliveryPartnerRepository;
import com.klickit.notification.client.HttpsTransactionalEmailClient;
import com.klickit.notification.client.TransactionalEmailClient;
import com.klickit.notification.listener.OrderNotificationListener;
import com.klickit.notification.service.EmailService;
import com.klickit.order.dto.CheckoutRequest;
import com.klickit.order.dto.OrderResponse;
import com.klickit.order.entity.Order;
import com.klickit.order.entity.OrderItem;
import com.klickit.order.entity.OrderStatus;
import com.klickit.order.event.OrderCreatedEvent;
import com.klickit.order.repository.OrderRepository;
import com.klickit.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
class OrderEmailNotificationTest {

    @Mock
    private TransactionalEmailClient emailClient;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private com.klickit.product.repository.ProductRepository productRepository;

    private EmailService emailService;
    private OrderService orderService;
    private OrderNotificationListener orderNotificationListener;

    private final UUID testOrderId = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private final String testCustomerName = "Rahul Sharma";
    private final String testPhone = "9876543210";
    private final String testAddress = "Block B, Room 101, Campus Hostel";
    private final Instant fixedNow = Instant.parse("2026-10-01T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(fixedNow, ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        emailService = new EmailService(emailClient, "admin@klickit.com", "Asia/Kolkata");
        orderService = new OrderService(orderRepository, cartRepository, productRepository, deliveryPartnerRepository, eventPublisher, fixedClock);
        orderNotificationListener = new OrderNotificationListener(emailService, orderService);
    }

    private Order buildSampleOrder() {
        Order order = Order.builder()
                .customerName(testCustomerName)
                .customerPhone(testPhone)
                .customerAddress(testAddress)
                .customerEmail("rahul@student.edu")
                .deadline(fixedNow.plus(15, ChronoUnit.MINUTES))
                .totalAmount(new BigDecimal("68.00"))
                .status(OrderStatus.PLACED)
                .notificationSent(false)
                .items(new ArrayList<>())
                .build();
        order.setId(testOrderId);

        OrderItem item1 = OrderItem.builder()
                .productName("Maggi 2-Minute Noodles")
                .quantity(2)
                .price(new BigDecimal("14.00"))
                .build();
        order.addItem(item1);

        OrderItem item2 = OrderItem.builder()
                .productName("Coca Cola 750ml")
                .quantity(1)
                .price(new BigDecimal("40.00"))
                .build();
        order.addItem(item2);

        return order;
    }

    // =========================================================================
    // 1. Synchronous EmailService & Async Listener Integration
    // =========================================================================

    @Test
    @DisplayName("Successful email delivery calls atomic update with sent=true")
    void handleOrderCreated_success_updatesNotificationStatusTrue() {
        Order order = buildSampleOrder();
        when(emailClient.sendEmail(eq("admin@klickit.com"), anyString(), anyString())).thenReturn(true);

        OrderCreatedEvent event = new OrderCreatedEvent(order, order.getItems());
        orderNotificationListener.handleOrderCreated(event);

        verify(orderRepository).updateNotificationStatus(eq(testOrderId), eq(true));
    }

    @Test
    @DisplayName("Provider failure calls atomic update with sent=false and does not throw")
    void handleOrderCreated_failure_updatesNotificationStatusFalse() {
        Order order = buildSampleOrder();
        when(emailClient.sendEmail(eq("admin@klickit.com"), anyString(), anyString())).thenReturn(false);

        OrderCreatedEvent event = new OrderCreatedEvent(order, order.getItems());
        assertThatCode(() -> orderNotificationListener.handleOrderCreated(event))
                .doesNotThrowAnyException();

        verify(orderRepository).updateNotificationStatus(eq(testOrderId), eq(false));
    }

    @Test
    @DisplayName("Order persists and survives even if notification delivery fails")
    void checkout_survivesNotificationFailure() {
        Cart cart = Cart.builder()
                .sessionId("sess_12345")
                .items(new ArrayList<>())
                .build();
        UUID maggiId = UUID.randomUUID();
        CartItem cartItem = CartItem.builder()
                .cart(cart)
                .productId(maggiId)
                .productName("Maggi")
                .unitPrice(new BigDecimal("14.00"))
                .quantity(2)
                .build();
        cart.addItem(cartItem);
        com.klickit.product.entity.Product maggi = com.klickit.product.entity.Product.builder().name("Maggi").price(new BigDecimal("14.00")).active(true).build();
        maggi.setId(maggiId);
        when(productRepository.findById(maggiId)).thenReturn(Optional.of(maggi));

        when(cartRepository.findBySessionId("sess_12345")).thenReturn(Optional.of(cart));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(testOrderId);
            return o;
        });

        CheckoutRequest request = new CheckoutRequest("sess_12345", "Pooja", "9876543210", "Hostel Room 1", null);
        OrderResponse response = orderService.checkout(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(testOrderId);
        verify(orderRepository).save(any(Order.class));
        verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));
    }

    // =========================================================================
    // 2. Authoritative Server-side Deadline Calculation
    // =========================================================================

    @Test
    @DisplayName("Server calculates authoritative 15-minute deadline, ignoring client-provided value")
    void checkout_calculatesAuthoritativeDeadline_ignoresClientDeadline() {
        Cart cart = Cart.builder().sessionId("sess_test").items(new ArrayList<>()).build();
        UUID penId = UUID.randomUUID();
        cart.addItem(CartItem.builder().cart(cart).productId(penId).productName("Pen").unitPrice(new BigDecimal("10.00")).quantity(1).build());
        com.klickit.product.entity.Product pen = com.klickit.product.entity.Product.builder().name("Pen").price(new BigDecimal("10.00")).active(true).build();
        pen.setId(penId);
        when(productRepository.findById(penId)).thenReturn(Optional.of(pen));

        when(cartRepository.findBySessionId("sess_test")).thenReturn(Optional.of(cart));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(testOrderId);
            return o;
        });

        Instant maliciousClientDeadline = Instant.parse("2099-12-31T23:59:59Z");
        CheckoutRequest request = new CheckoutRequest("sess_test", "Pooja", "9876543210", "Hostel Room 1", maliciousClientDeadline);

        OrderResponse response = orderService.checkout(request);

        // Expected authoritative deadline is fixedNow + 15 minutes = 2026-10-01T12:15:00Z
        Instant expectedDeadline = fixedNow.plus(15, ChronoUnit.MINUTES);
        assertThat(response.getDeadline()).isEqualTo(expectedDeadline);
        assertThat(response.getDeadline()).isNotEqualTo(maliciousClientDeadline);
    }

    // =========================================================================
    // 3. Stored XSS Protection & HTML Escaping
    // =========================================================================

    @Test
    @DisplayName("HTML email escapes customerName, address, phone, and productName to prevent XSS")
    void buildOrderEmailBody_escapesCustomerControlledFields() {
        Order order = Order.builder()
                .customerName("<script>alert('xss-name')</script>")
                .customerPhone("+919876543210<img src=x onerror=alert(1)>")
                .customerAddress("<b>Hostel</b> Room <iframe src=evil.com>")
                .deadline(fixedNow)
                .totalAmount(new BigDecimal("50.00"))
                .items(new ArrayList<>())
                .build();
        order.setId(testOrderId);

        OrderItem maliciousItem = OrderItem.builder()
                .productName("<svg onload=alert('item')>Noodles")
                .quantity(1)
                .price(new BigDecimal("50.00"))
                .build();
        order.addItem(maliciousItem);

        String emailHtml = emailService.buildOrderEmailBody(order);

        assertThat(emailHtml).doesNotContain("<script>alert('xss-name')</script>");
        assertThat(emailHtml).contains("&lt;script&gt;alert(&#39;xss-name&#39;)&lt;/script&gt;");

        assertThat(emailHtml).doesNotContain("<img src=x onerror=alert(1)>");
        assertThat(emailHtml).contains("&lt;img src=x onerror=alert(1)&gt;");

        assertThat(emailHtml).doesNotContain("<iframe src=evil.com>");
        assertThat(emailHtml).contains("&lt;iframe src=evil.com&gt;");

        assertThat(emailHtml).doesNotContain("<svg onload=alert('item')>");
        assertThat(emailHtml).contains("&lt;svg onload=alert(&#39;item&#39;)&gt;");
    }

    // =========================================================================
    // 4. Timezone Formatting (Asia/Kolkata)
    // =========================================================================

    @Test
    @DisplayName("Deadline in email is formatted in Asia/Kolkata timezone")
    void buildOrderEmailBody_formatsDeadlineInIST() {
        // fixedNow is 2026-10-01T12:00:00Z -> In Asia/Kolkata (+05:30), that is 5:30 PM (17:30)
        Order order = buildSampleOrder();
        order.setDeadline(fixedNow);

        String emailHtml = emailService.buildOrderEmailBody(order);

        assertThat(emailHtml).contains("01 Oct 2026, 05:30 pm");
    }

    @Test
    @DisplayName("Deadline in email is formatted in Asia/Kolkata timezone under US English locale (en-US)")
    void buildOrderEmailBody_formatsDeadlineInIST_underUSEnglishLocale() {
        Order order = buildSampleOrder();
        order.setDeadline(fixedNow);

        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            String emailHtml = emailService.buildOrderEmailBody(order);
            assertThat(emailHtml).contains("01 Oct 2026, 05:30 pm");
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    @DisplayName("Deadline in email is formatted in Asia/Kolkata timezone under French locale (fr-FR)")
    void buildOrderEmailBody_formatsDeadlineInIST_underFrenchLocale() {
        Order order = buildSampleOrder();
        order.setDeadline(fixedNow);

        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            String emailHtml = emailService.buildOrderEmailBody(order);
            assertThat(emailHtml).contains("01 Oct 2026, 05:30 pm");
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    // =========================================================================
    // 5. Missing / Invalid API Key Handling
    // =========================================================================

    @Test
    @DisplayName("Missing API key in HttpsTransactionalEmailClient logs error and returns false cleanly")
    void httpsClient_missingApiKey_returnsFalseCleanly() {
        RestClient.Builder builder = RestClient.builder();
        HttpsTransactionalEmailClient client = new HttpsTransactionalEmailClient(
                builder, "", "onboarding@resend.dev", "https://api.resend.com/emails");

        boolean result = client.sendEmail("admin@klickit.com", "Test Subject", "<p>Hello</p>");

        assertThat(result).isFalse();
    }

    // =========================================================================
    // 6. HttpsTransactionalEmailClient HTTP Success & Retry on Error
    // =========================================================================

    @Test
    @DisplayName("HttpsTransactionalEmailClient sends HTTP request with Bearer auth and succeeds on 200")
    void httpsClient_successfulPost_returnsTrue() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        HttpsTransactionalEmailClient client = new HttpsTransactionalEmailClient(
                restClient, "re_valid_api_key", "onboarding@resend.dev", "https://api.resend.com/emails");

        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_valid_api_key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"id\": \"msg_123\"}", MediaType.APPLICATION_JSON));

        boolean result = client.sendEmail("admin@klickit.com", "Subject", "<p>Body</p>");

        assertThat(result).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("HttpsTransactionalEmailClient retries once on 500 server error and returns false if both fail")
    void httpsClient_retryOnError_returnsFalseWhenBothFail() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        HttpsTransactionalEmailClient client = new HttpsTransactionalEmailClient(
                restClient, "re_valid_api_key", "onboarding@resend.dev", "https://api.resend.com/emails");

        // First attempt fails with 500
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        // Retry attempt fails with 500
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        boolean result = client.sendEmail("admin@klickit.com", "Subject", "<p>Body</p>");

        assertThat(result).isFalse();
        server.verify();
    }
}
