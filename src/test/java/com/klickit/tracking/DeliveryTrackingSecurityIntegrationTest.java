package com.klickit.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klickit.config.JwtService;
import com.klickit.delivery.entity.DeliveryPartner;
import com.klickit.delivery.repository.DeliveryPartnerRepository;
import com.klickit.order.entity.Order;
import com.klickit.order.entity.OrderStatus;
import com.klickit.order.repository.OrderRepository;
import com.klickit.tracking.dto.LocationUpdateRequest;
import com.klickit.tracking.entity.DeliveryLocation;
import com.klickit.tracking.repository.DeliveryLocationRepository;
import com.klickit.user.entity.Role;
import com.klickit.user.entity.User;
import com.klickit.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class DeliveryTrackingSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DeliveryLocationRepository deliveryLocationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Value("${klickit.jwt.secret}")
    private String secretKey;

    private final List<DeliveryLocation> testLocations = new ArrayList<>();
    private final List<Order> testOrders = new ArrayList<>();
    private final List<DeliveryPartner> testPartners = new ArrayList<>();
    private final List<User> testUsers = new ArrayList<>();

    private User customerUserA;
    private User customerUserB;
    private User driverUser1;
    private User driverUser2;
    private User adminUser;
    private User orphanDriverUser;

    private DeliveryPartner driverPartner1;
    private DeliveryPartner driverPartner2;

    private Order orderA;

    @BeforeEach
    void setUp() {
        // Setup distinct users with different roles
        customerUserA = createAndSaveUser("Alice Customer", "alice_track_" + UUID.randomUUID() + "@klickit.test", "+919811100001", Role.CUSTOMER);
        customerUserB = createAndSaveUser("Bob Customer", "bob_track_" + UUID.randomUUID() + "@klickit.test", "+919811100002", Role.CUSTOMER);
        driverUser1 = createAndSaveUser("Driver Alpha", "driver1_track_" + UUID.randomUUID() + "@klickit.test", "+919811100003", Role.DELIVERY_PARTNER);
        driverUser2 = createAndSaveUser("Driver Beta", "driver2_track_" + UUID.randomUUID() + "@klickit.test", "+919811100004", Role.DELIVERY_PARTNER);
        adminUser = createAndSaveUser("Admin Boss", "admin_track_" + UUID.randomUUID() + "@klickit.test", "+919811100005", Role.ADMIN);
        orphanDriverUser = createAndSaveUser("Orphan Driver", "orphan_track_" + UUID.randomUUID() + "@klickit.test", "+919811100006", Role.DELIVERY_PARTNER);

        // Associate driver 1 and driver 2 with DeliveryPartner entities
        driverPartner1 = createAndSavePartner(driverUser1, "Driver Alpha", "+919811100003");
        driverPartner2 = createAndSavePartner(driverUser2, "Driver Beta", "+919811100004");
        // Note: orphanDriverUser has Role.DELIVERY_PARTNER, but NO entry in delivery_partners table

        // Create an order owned by customer A and assigned to driver 1
        orderA = Order.builder()
                .customerName(customerUserA.getName())
                .customerEmail(customerUserA.getEmail())
                .customerPhone(customerUserA.getPhone())
                .customerAddress("Block 6, Room 101")
                .customerLatitude(23.075611)
                .customerLongitude(76.850082)
                .customerLandmark("Near Hostel 1 Gate")
                .meetAtGate(true)
                .status(OrderStatus.ASSIGNED)
                .deliveryPartnerId(driverPartner1.getId())
                .deliveryPartnerName(driverPartner1.getName())
                .deliveryPartnerPhone(driverPartner1.getPhone())
                .totalAmount(new BigDecimal("180.00"))
                .deliveryFee(new BigDecimal("25.00"))
                .deadline(Instant.now().plusSeconds(3600))
                .build();
        orderA = orderRepository.save(orderA);
        testOrders.add(orderA);
    }

    @AfterEach
    void tearDown() {
        for (DeliveryLocation location : testLocations) {
            deliveryLocationRepository.delete(location);
        }
        testLocations.clear();

        // Also clean up any location created directly in test methods
        if (orderA != null && orderA.getId() != null) {
            deliveryLocationRepository.findByOrderId(orderA.getId())
                    .ifPresent(deliveryLocationRepository::delete);
        }

        for (Order order : testOrders) {
            orderRepository.delete(order);
        }
        testOrders.clear();

        for (DeliveryPartner partner : testPartners) {
            deliveryPartnerRepository.delete(partner);
        }
        testPartners.clear();

        for (User user : testUsers) {
            userRepository.delete(user);
        }
        testUsers.clear();
    }

    private User createAndSaveUser(String name, String email, String phone, Role role) {
        User user = User.builder()
                .name(name)
                .email(email)
                .phone(phone)
                .password(passwordEncoder.encode("SecurePass123!"))
                .role(role)
                .build();
        User saved = userRepository.save(user);
        testUsers.add(saved);
        return saved;
    }

    private DeliveryPartner createAndSavePartner(User user, String name, String phone) {
        DeliveryPartner partner = DeliveryPartner.builder()
                .user(user)
                .name(name)
                .phone(phone)
                .build();
        DeliveryPartner saved = deliveryPartnerRepository.save(partner);
        testPartners.add(saved);
        return saved;
    }

    private String generateExpiredJwt(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date(System.currentTimeMillis() - 100000))
                .expiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey)))
                .compact();
    }

    // -------------------------------------------------------------------------
    // 1. Unauthenticated requests return 401 Unauthorized JSON
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Unauthenticated GET /tracking/{orderId} returns 401 Unauthorized JSON")
    void unauthenticated_getTracking_returns401() throws Exception {
        mockMvc.perform(get("/tracking/" + orderA.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("Unauthorized")));
    }

    @Test
    @DisplayName("Unauthenticated POST /tracking/{orderId}/location returns 401 Unauthorized JSON")
    void unauthenticated_updateLocation_returns401() throws Exception {
        LocationUpdateRequest request = new LocationUpdateRequest(23.0740, 76.8400, 10.0);

        mockMvc.perform(post("/tracking/" + orderA.getId() + "/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("Unauthorized")));
    }

    // -------------------------------------------------------------------------
    // 2. Expired / Malformed JWT returns 401 Unauthorized JSON
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Expired JWT on GET /tracking/{orderId} returns 401 Unauthorized JSON")
    void expiredToken_getTracking_returns401() throws Exception {
        String expiredToken = generateExpiredJwt(customerUserA.getEmail());

        mockMvc.perform(get("/tracking/" + orderA.getId())
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("Unauthorized")));
    }

    @Test
    @DisplayName("Malformed JWT on POST /tracking/{orderId}/location returns 401 Unauthorized JSON")
    void malformedToken_updateLocation_returns401() throws Exception {
        LocationUpdateRequest request = new LocationUpdateRequest(23.0740, 76.8400, 10.0);

        mockMvc.perform(post("/tracking/" + orderA.getId() + "/location")
                        .header("Authorization", "Bearer not.a.valid.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("Unauthorized")));
    }

    // -------------------------------------------------------------------------
    // 3. Role-based enforcement on POST /tracking/{orderId}/location
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("CUSTOMER role JWT attempting to update rider location returns 403 Forbidden JSON")
    void customerToken_updateLocation_returns403() throws Exception {
        String customerToken = jwtService.generateToken(customerUserA);
        LocationUpdateRequest request = new LocationUpdateRequest(23.0740, 76.8400, 10.0);

        mockMvc.perform(post("/tracking/" + orderA.getId() + "/location")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    @DisplayName("ADMIN role JWT attempting to update rider location returns 403 Forbidden JSON")
    void adminToken_updateLocation_returns403() throws Exception {
        String adminToken = jwtService.generateToken(adminUser);
        LocationUpdateRequest request = new LocationUpdateRequest(23.0740, 76.8400, 10.0);

        mockMvc.perform(post("/tracking/" + orderA.getId() + "/location")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    // -------------------------------------------------------------------------
    // 4. Assigned vs Unassigned delivery partner location updates
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Assigned DELIVERY_PARTNER JWT updating location returns 200 OK JSON with updated coordinates")
    void assignedDriver_updateLocation_returns200() throws Exception {
        String driver1Token = jwtService.generateToken(driverUser1);
        LocationUpdateRequest request = new LocationUpdateRequest(23.0735, 76.8350, 6.5);

        mockMvc.perform(post("/tracking/" + orderA.getId() + "/location")
                        .header("Authorization", "Bearer " + driver1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Location updated successfully"))
                .andExpect(jsonPath("$.data.orderId").value(orderA.getId().toString()))
                .andExpect(jsonPath("$.data.deliveryLatitude").value(23.0735))
                .andExpect(jsonPath("$.data.deliveryLongitude").value(76.8350))
                .andExpect(jsonPath("$.data.accuracy").value(6.5))
                .andExpect(jsonPath("$.data.trackingActive").value(true));
    }

    @Test
    @DisplayName("Unassigned DELIVERY_PARTNER JWT updating location returns 403 Forbidden JSON")
    void unassignedDriver_updateLocation_returns403() throws Exception {
        String driver2Token = jwtService.generateToken(driverUser2);
        LocationUpdateRequest request = new LocationUpdateRequest(23.0735, 76.8350, 6.5);

        mockMvc.perform(post("/tracking/" + orderA.getId() + "/location")
                        .header("Authorization", "Bearer " + driver2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    // -------------------------------------------------------------------------
    // 5. Customer order ownership tracking reads
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Customer JWT viewing their own order tracking returns 200 OK JSON")
    void customer_viewOwnOrderTracking_returns200() throws Exception {
        String customerTokenA = jwtService.generateToken(customerUserA);

        mockMvc.perform(get("/tracking/" + orderA.getId())
                        .header("Authorization", "Bearer " + customerTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value(orderA.getId().toString()))
                .andExpect(jsonPath("$.data.customerLatitude").value(23.075611))
                .andExpect(jsonPath("$.data.customerLongitude").value(76.850082))
                .andExpect(jsonPath("$.data.meetAtGate").value(true))
                .andExpect(jsonPath("$.data.deliveryPartnerName").value("Driver Alpha"));
    }

    @Test
    @DisplayName("Customer JWT viewing another customer's order tracking returns 403 Forbidden JSON")
    void customer_viewOtherCustomerOrderTracking_returns403() throws Exception {
        String customerTokenB = jwtService.generateToken(customerUserB);

        mockMvc.perform(get("/tracking/" + orderA.getId())
                        .header("Authorization", "Bearer " + customerTokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    // -------------------------------------------------------------------------
    // 6. Delivery Partner order tracking reads
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Assigned DELIVERY_PARTNER JWT viewing their assigned order tracking returns 200 OK JSON")
    void assignedDriver_viewTracking_returns200() throws Exception {
        String driver1Token = jwtService.generateToken(driverUser1);

        mockMvc.perform(get("/tracking/" + orderA.getId())
                        .header("Authorization", "Bearer " + driver1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value(orderA.getId().toString()))
                .andExpect(jsonPath("$.data.deliveryPartnerId").value(driverPartner1.getId().toString()));
    }

    @Test
    @DisplayName("Unassigned DELIVERY_PARTNER JWT viewing that order tracking returns 403 Forbidden JSON")
    void unassignedDriver_viewTracking_returns403() throws Exception {
        String driver2Token = jwtService.generateToken(driverUser2);

        mockMvc.perform(get("/tracking/" + orderA.getId())
                        .header("Authorization", "Bearer " + driver2Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    // -------------------------------------------------------------------------
    // 7. Admin universal read access
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("ADMIN JWT viewing any order tracking returns 200 OK JSON")
    void admin_viewAnyOrderTracking_returns200() throws Exception {
        String adminToken = jwtService.generateToken(adminUser);

        mockMvc.perform(get("/tracking/" + orderA.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value(orderA.getId().toString()))
                .andExpect(jsonPath("$.data.customerLatitude").value(23.075611));
    }

    // -------------------------------------------------------------------------
    // 8. Input validation for coordinates (400 Bad Request JSON)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Latitude out of range (> 90) returns 400 Bad Request JSON")
    void updateLocation_latitudeTooHigh_returns400() throws Exception {
        String driver1Token = jwtService.generateToken(driverUser1);
        LocationUpdateRequest invalidLat = new LocationUpdateRequest(95.0, 76.8, 5.0);

        mockMvc.perform(post("/tracking/" + orderA.getId() + "/location")
                        .header("Authorization", "Bearer " + driver1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidLat)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Latitude out of range (< -90) returns 400 Bad Request JSON")
    void updateLocation_latitudeTooLow_returns400() throws Exception {
        String driver1Token = jwtService.generateToken(driverUser1);
        LocationUpdateRequest invalidLat = new LocationUpdateRequest(-95.0, 76.8, 5.0);

        mockMvc.perform(post("/tracking/" + orderA.getId() + "/location")
                        .header("Authorization", "Bearer " + driver1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidLat)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Longitude out of range (> 180) returns 400 Bad Request JSON")
    void updateLocation_longitudeTooHigh_returns400() throws Exception {
        String driver1Token = jwtService.generateToken(driverUser1);
        LocationUpdateRequest invalidLon = new LocationUpdateRequest(23.0, 185.0, 5.0);

        mockMvc.perform(post("/tracking/" + orderA.getId() + "/location")
                        .header("Authorization", "Bearer " + driver1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidLon)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Negative accuracy returns 400 Bad Request JSON")
    void updateLocation_negativeAccuracy_returns400() throws Exception {
        String driver1Token = jwtService.generateToken(driverUser1);
        LocationUpdateRequest negativeAcc = new LocationUpdateRequest(23.0, 76.8, -10.0);

        mockMvc.perform(post("/tracking/" + orderA.getId() + "/location")
                        .header("Authorization", "Bearer " + driver1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negativeAcc)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Null latitude returns 400 Bad Request JSON")
    void updateLocation_nullLatitude_returns400() throws Exception {
        String driver1Token = jwtService.generateToken(driverUser1);
        LocationUpdateRequest nullLat = new LocationUpdateRequest(null, 76.8, 5.0);

        mockMvc.perform(post("/tracking/" + orderA.getId() + "/location")
                        .header("Authorization", "Bearer " + driver1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullLat)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // -------------------------------------------------------------------------
    // 9. Edge Cases: Orphan driver user & Non-trackable order status
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("DELIVERY_PARTNER JWT with no partner profile in DB returns 403 Forbidden JSON")
    void orphanDriver_updateLocation_returns403() throws Exception {
        String orphanToken = jwtService.generateToken(orphanDriverUser);
        LocationUpdateRequest request = new LocationUpdateRequest(23.0740, 76.8400, 10.0);

        mockMvc.perform(post("/tracking/" + orderA.getId() + "/location")
                        .header("Authorization", "Bearer " + orphanToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    @DisplayName("Delivered order rejects location updates with 400 Bad Request JSON")
    void deliveredOrder_rejectsLocationUpdate_returns400() throws Exception {
        orderA.setStatus(OrderStatus.DELIVERED);
        orderRepository.save(orderA);

        String driver1Token = jwtService.generateToken(driverUser1);
        LocationUpdateRequest request = new LocationUpdateRequest(23.0740, 76.8400, 10.0);

        mockMvc.perform(post("/tracking/" + orderA.getId() + "/location")
                        .header("Authorization", "Bearer " + driver1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("Live location updates are not accepted")));
    }
}
