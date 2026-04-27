package com.checkout.checkout_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CheckoutRequest(
        @NotBlank String orderId,
        @NotBlank String customerId,
        @NotNull BigDecimal amount,
        @NotBlank String currency,
        @NotBlank String idempotencyKey
) {}
