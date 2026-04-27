package com.checkout.checkout_system.dto;

import com.checkout.checkout_system.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CheckoutResponse(
        UUID paymentId,
        String orderId,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        LocalDateTime createdAt
) {}
