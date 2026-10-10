package com.klickit.order;

import com.klickit.common.dto.PagedResponse;
import com.klickit.order.dto.OrderResponse;
import com.klickit.order.entity.Order;
import com.klickit.order.entity.OrderItem;
import com.klickit.order.entity.OrderStatus;
import com.klickit.order.repository.OrderRepository;
import com.klickit.order.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AdminOrderPaginationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
    }

    private void seedOrders(int count) {
        List<Order> orders = new ArrayList<>(count);
        Instant now = Instant.now();
        for (int i = 1; i <= count; i++) {
            OrderStatus status = (i % 3 == 0)
                    ? OrderStatus.READY_TO_ASSIGN
                    : (i % 3 == 1) ? OrderStatus.PLACED : OrderStatus.DELIVERED;

            Order order = Order.builder()
                    .customerName(String.format("Customer %03d", i))
                    .customerPhone("+919876543210")
                    .customerAddress("Hostel Block A, Room " + i)
                    .customerEmail(String.format("customer%03d@example.com", i))
                    .deadline(now.plus(15, ChronoUnit.MINUTES))
                    .status(status)
                    .deliveryFee(new BigDecimal("25.00"))
                    .totalAmount(new BigDecimal("150.00"))
                    .build();

            OrderItem item = OrderItem.builder()
                    .productName("Sample Product " + i)
                    .quantity(2)
                    .price(new BigDecimal("62.50"))
                    .build();
            order.addItem(item);

            orders.add(order);
        }
        List<Order> saved = orderRepository.saveAll(orders);

        // Update createdAt via JDBC so JPA Auditing doesn't overwrite our distinct timestamps
        for (int i = 0; i < saved.size(); i++) {
            Order o = saved.get(i);
            Instant timestamp = now.minus(count - (i + 1), ChronoUnit.MINUTES);
            jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE id = ?",
                    java.sql.Timestamp.from(timestamp), o.getId());
        }
    }

    @Test
    @WithMockUser(username = "admin@klickit.com", roles = {"ADMIN"})
    @DisplayName("Default Pagination: GET /admin/orders returns page 0, size 20 with full metadata")
    void defaultPagination_returnsPageZeroAndSizeTwenty() throws Exception {
        seedOrders(35);

        mockMvc.perform(get("/admin/orders")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(20)))
                .andExpect(jsonPath("$.data.page", is(0)))
                .andExpect(jsonPath("$.data.size", is(20)))
                .andExpect(jsonPath("$.data.totalElements", is(35)))
                .andExpect(jsonPath("$.data.totalPages", is(2)))
                .andExpect(jsonPath("$.data.last", is(false)))
                // Verify ordering is createdAt DESC (Customer 035 is newest)
                .andExpect(jsonPath("$.data.content[0].customerName", is("Customer 035")));
    }

    @Test
    @WithMockUser(username = "admin@klickit.com", roles = {"ADMIN"})
    @DisplayName("Custom Page and Size: page=1&size=10 returns next slice with last=false")
    void customPageAndSize_returnsRequestedSlice() throws Exception {
        seedOrders(35);

        mockMvc.perform(get("/admin/orders")
                        .param("page", "1")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(10)))
                .andExpect(jsonPath("$.data.page", is(1)))
                .andExpect(jsonPath("$.data.size", is(10)))
                .andExpect(jsonPath("$.data.totalElements", is(35)))
                .andExpect(jsonPath("$.data.totalPages", is(4)))
                .andExpect(jsonPath("$.data.last", is(false)))
                // Offset 10 in descending 35..1 list is Customer 025
                .andExpect(jsonPath("$.data.content[0].customerName", is("Customer 025")));
    }

    @Test
    @WithMockUser(username = "admin@klickit.com", roles = {"ADMIN"})
    @DisplayName("Parameter Clamping: size > 100 clamped to 100, negative page clamped to 0, size < 1 clamped to 1")
    void parameterClamping_normalizesOutOfBoundsParameters() throws Exception {
        seedOrders(120);

        // size=250 clamped to 100
        mockMvc.perform(get("/admin/orders")
                        .param("size", "250")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(100)))
                .andExpect(jsonPath("$.data.size", is(100)))
                .andExpect(jsonPath("$.data.totalElements", is(120)))
                .andExpect(jsonPath("$.data.totalPages", is(2)));

        // negative page clamped to 0
        mockMvc.perform(get("/admin/orders")
                        .param("page", "-5")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page", is(0)))
                .andExpect(jsonPath("$.data.content", hasSize(10)));

        // size < 1 clamped to 1
        mockMvc.perform(get("/admin/orders")
                        .param("page", "0")
                        .param("size", "0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size", is(1)))
                .andExpect(jsonPath("$.data.content", hasSize(1)));
    }

    @Test
    @WithMockUser(username = "admin@klickit.com", roles = {"ADMIN"})
    @DisplayName("Status Filtering with Pagination: filters matching orders and calculates accurate page count")
    void statusFilteringWithPagination_returnsMatchingSubsetOnly() throws Exception {
        seedOrders(30);
        // (i % 3 == 0) -> READY_TO_ASSIGN (exactly 10 orders: 3, 6, 9, ..., 30)

        mockMvc.perform(get("/admin/orders")
                        .param("status", "READY_TO_ASSIGN")
                        .param("page", "0")
                        .param("size", "5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(5)))
                .andExpect(jsonPath("$.data.totalElements", is(10)))
                .andExpect(jsonPath("$.data.totalPages", is(2)))
                .andExpect(jsonPath("$.data.content[0].status", is("READY_TO_ASSIGN")))
                .andExpect(jsonPath("$.data.content[0].customerName", is("Customer 030")));
    }

    @Test
    @WithMockUser(username = "admin@klickit.com", roles = {"ADMIN"})
    @DisplayName("Out-of-Range Page: page=999 returns empty content array with last=true and correct totalElements")
    void outOfRangePage_returnsEmptyContentWithValidMetadata() throws Exception {
        seedOrders(15);

        mockMvc.perform(get("/admin/orders")
                        .param("page", "999")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)))
                .andExpect(jsonPath("$.data.page", is(999)))
                .andExpect(jsonPath("$.data.totalElements", is(15)))
                .andExpect(jsonPath("$.data.totalPages", is(2)))
                .andExpect(jsonPath("$.data.last", is(true)));
    }

    @Test
    @WithMockUser(username = "admin@klickit.com", roles = {"ADMIN"})
    @DisplayName("Deterministic Ordering: identical createdAt timestamps sort deterministically by id DESC")
    void identicalTimestamps_sortDeterministicallyByIdDesc() {
        Instant fixedTime = Instant.now();
        Order order1 = Order.builder()
                .customerName("Tie Order 1")
                .customerPhone("+919876543210")
                .customerAddress("Gate")
                .deadline(fixedTime.plus(15, ChronoUnit.MINUTES))
                .status(OrderStatus.PLACED)
                .totalAmount(new BigDecimal("100.00"))
                .build();
        order1.setCreatedAt(fixedTime);

        Order order2 = Order.builder()
                .customerName("Tie Order 2")
                .customerPhone("+919876543210")
                .customerAddress("Gate")
                .deadline(fixedTime.plus(15, ChronoUnit.MINUTES))
                .status(OrderStatus.PLACED)
                .totalAmount(new BigDecimal("100.00"))
                .build();
        order2.setCreatedAt(fixedTime);

        Order saved1 = orderRepository.save(order1);
        Order saved2 = orderRepository.save(order2);

        // Guarantee identical timestamp in database
        jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE id IN (?, ?)",
                java.sql.Timestamp.from(fixedTime), saved1.getId(), saved2.getId());
        PagedResponse<OrderResponse> response = orderService.getAdminOrdersPaged(null, 0, 10);
        assertThat(response.getContent()).hasSize(2);

        // id DESC tie-breaker: results must be sorted with first ID > second ID
        UUID firstId = response.getContent().get(0).getId();
        UUID secondId = response.getContent().get(1).getId();

        // In PostgreSQL UUID sorting (or lexicographical), firstId > secondId
        assertThat(firstId.toString().compareTo(secondId.toString())).isGreaterThan(0);
    }

    @Test
    @DisplayName("RBAC Security: Unauthenticated caller receives 401 Unauthorized")
    void unauthenticatedCaller_returns401() throws Exception {
        mockMvc.perform(get("/admin/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@example.com", roles = {"CUSTOMER"})
    @DisplayName("RBAC Security: Ordinary CUSTOMER caller receives 403 Forbidden")
    void customerCaller_returns403() throws Exception {
        mockMvc.perform(get("/admin/orders"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "delivery@example.com", roles = {"DELIVERY_PARTNER"})
    @DisplayName("RBAC Security: DELIVERY_PARTNER caller receives 403 Forbidden")
    void deliveryPartnerCaller_returns403() throws Exception {
        mockMvc.perform(get("/admin/orders"))
                .andExpect(status().isForbidden());
    }
}
