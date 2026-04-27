package com.checkout.checkout_system.job;

import com.checkout.checkout_system.enums.PaymentStatus;
import com.checkout.checkout_system.model.Payment;
import com.checkout.checkout_system.repository.PaymentRepository;
import com.checkout.checkout_system.service.PaymentStateService;
import io.micrometer.core.instrument.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CaptureJob {

    private static final Logger log = LoggerFactory.getLogger(CaptureJob.class);

    private final PaymentRepository paymentRepository;
    private final PaymentStateService paymentStateService;
    private final Counter paymentSettledTotal;

    public CaptureJob(
            PaymentRepository paymentRepository,
            PaymentStateService paymentStateService,
            @Qualifier("paymentSettledTotal") Counter paymentSettledTotal) {
        this.paymentRepository = paymentRepository;
        this.paymentStateService = paymentStateService;
        this.paymentSettledTotal = paymentSettledTotal;
    }

    @Scheduled(fixedDelay = 30000)
    public void captureAndSettle() {
        List<Payment> authorized = paymentRepository.findByStatus(PaymentStatus.AUTHORIZED);
        for (Payment payment : authorized) {
            try {
                Payment captured = paymentStateService.transition(payment, PaymentStatus.CAPTURED);
                log.info("Payment {} transitioned: AUTHORIZED -> CAPTURED", captured.getId());

                Payment settled = paymentStateService.transition(captured, PaymentStatus.SETTLED);
                log.info("Payment {} transitioned: CAPTURED -> SETTLED", settled.getId());
                paymentSettledTotal.increment();
            } catch (Exception e) {
                log.error("Failed to capture/settle payment {}: {}", payment.getId(), e.getMessage());
            }
        }
    }
}
