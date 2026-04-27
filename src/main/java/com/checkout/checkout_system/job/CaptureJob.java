package com.checkout.checkout_system.job;

import com.checkout.checkout_system.enums.PaymentStatus;
import com.checkout.checkout_system.model.Payment;
import com.checkout.checkout_system.repository.PaymentRepository;
import com.checkout.checkout_system.service.PaymentStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CaptureJob {

    private static final Logger log = LoggerFactory.getLogger(CaptureJob.class);

    private final PaymentRepository paymentRepository;
    private final PaymentStateService paymentStateService;

    public CaptureJob(PaymentRepository paymentRepository, PaymentStateService paymentStateService) {
        this.paymentRepository = paymentRepository;
        this.paymentStateService = paymentStateService;
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
            } catch (Exception e) {
                log.error("Failed to capture/settle payment {}: {}", payment.getId(), e.getMessage());
            }
        }
    }
}
