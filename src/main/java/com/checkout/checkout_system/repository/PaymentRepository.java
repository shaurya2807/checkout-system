package com.checkout.checkout_system.repository;

import com.checkout.checkout_system.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByIdempotencyKey(String key);
    Optional<Payment> findByOrderId(String orderId);
}
