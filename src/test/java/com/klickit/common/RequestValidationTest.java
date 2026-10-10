package com.klickit.common;

import com.klickit.cart.dto.AddToCartRequest;
import com.klickit.order.dto.CheckoutRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    @DisplayName("Valid CheckoutRequest produces no constraint violations")
    void validCheckoutRequest_passesValidation() {
        CheckoutRequest request = new CheckoutRequest(
                "sess_abc123",
                "Rahul Sharma",
                "+919876543210",
                "Block B, Room 101, Campus Hostel",
                null
        );

        Set<ConstraintViolation<CheckoutRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("CheckoutRequest allows null customer address for map-first checkouts")
    void checkoutRequest_nullAddress_passesValidation() {
        CheckoutRequest request = new CheckoutRequest(
                "sess_abc123",
                "Rahul Sharma",
                "+919876543210",
                null,
                23.0753,
                76.8606,
                "Near Main Gate"
        );

        Set<ConstraintViolation<CheckoutRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("CheckoutRequest rejects customer name exceeding 100 characters")
    void checkoutRequest_nameExceeds100_rejected() {
        String longName = "A".repeat(101);
        CheckoutRequest request = new CheckoutRequest(
                "sess_abc123",
                longName,
                "+919876543210",
                "Campus Hostel",
                null
        );

        Set<ConstraintViolation<CheckoutRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("customerName"));
    }

    @Test
    @DisplayName("CheckoutRequest rejects customer address exceeding 255 characters")
    void checkoutRequest_addressExceeds255_rejected() {
        String longAddress = "B".repeat(256);
        CheckoutRequest request = new CheckoutRequest(
                "sess_abc123",
                "Rahul Sharma",
                "+919876543210",
                longAddress,
                null
        );

        Set<ConstraintViolation<CheckoutRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("customerAddress"));
    }

    @Test
    @DisplayName("CheckoutRequest rejects invalid phone formats")
    void checkoutRequest_invalidPhone_rejected() {
        CheckoutRequest request = new CheckoutRequest(
                "sess_abc123",
                "Rahul Sharma",
                "not-a-phone-number-at-all",
                "Campus Hostel",
                null
        );

        Set<ConstraintViolation<CheckoutRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("customerPhone"));
    }

    @Test
    @DisplayName("CheckoutRequest rejects phone exceeding 20 characters")
    void checkoutRequest_phoneExceeds20_rejected() {
        CheckoutRequest request = new CheckoutRequest(
                "sess_abc123",
                "Rahul Sharma",
                "+9198765432101234567890", // 23 chars
                "Campus Hostel",
                null
        );

        Set<ConstraintViolation<CheckoutRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("customerPhone"));
    }

    @Test
    @DisplayName("AddToCartRequest rejects quantity < 1")
    void addToCartRequest_quantityBelow1_rejected() {
        AddToCartRequest request = new AddToCartRequest("sess_123", UUID.randomUUID(), 0);

        Set<ConstraintViolation<AddToCartRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("quantity"));
    }

    @Test
    @DisplayName("AddToCartRequest rejects quantity > 20")
    void addToCartRequest_quantityAbove20_rejected() {
        AddToCartRequest request = new AddToCartRequest("sess_123", UUID.randomUUID(), 21);

        Set<ConstraintViolation<AddToCartRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("quantity"));
    }

    @Test
    @DisplayName("AddToCartRequest accepts quantity between 1 and 20")
    void addToCartRequest_validQuantity_passes() {
        AddToCartRequest request = new AddToCartRequest("sess_123", UUID.randomUUID(), 5);

        Set<ConstraintViolation<AddToCartRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }
}
