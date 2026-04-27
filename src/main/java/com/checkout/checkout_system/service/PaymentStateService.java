package com.checkout.checkout_system.service;

import com.checkout.checkout_system.enums.PaymentStatus;
import com.checkout.checkout_system.exception.InvalidStateTransitionException;
import com.checkout.checkout_system.model.Payment;
import com.checkout.checkout_system.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Service
public class PaymentStateService {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = Map.of(
            PaymentStatus.PENDING,    Set.of(PaymentStatus.AUTHORIZED, PaymentStatus.FAILED),
            PaymentStatus.AUTHORIZED, Set.of(PaymentStatus.CAPTURED, PaymentStatus.FAILED),
            PaymentStatus.CAPTURED,   Set.of(PaymentStatus.SETTLED),
            PaymentStatus.SETTLED,    Set.of(PaymentStatus.REFUNDED),
            PaymentStatus.FAILED,     Set.of(),
            PaymentStatus.REFUNDED,   Set.of()
    );

    private final PaymentRepository paymentRepository;

    public PaymentStateService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Payment transition(Payment payment, PaymentStatus newStatus) {
        PaymentStatus current = payment.getStatus();
        Set<PaymentStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(newStatus)) {
            throw new InvalidStateTransitionException(current, newStatus);
        }
        payment.setStatus(newStatus);
        return paymentRepository.save(payment);
    }
}
