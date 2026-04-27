package com.checkout.checkout_system.service;

import com.checkout.checkout_system.dto.CheckoutRequest;
import com.checkout.checkout_system.dto.CheckoutResponse;
import com.checkout.checkout_system.enums.PaymentStatus;
import com.checkout.checkout_system.exception.DuplicatePaymentException;
import com.checkout.checkout_system.model.Payment;
import com.checkout.checkout_system.repository.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Map;
import java.util.Optional;

@Service
public class CheckoutService {

    private final PaymentRepository paymentRepository;
    private final PaymentStateService paymentStateService;
    private final IdempotencyService idempotencyService;
    private final SqsAsyncClient sqsAsyncClient;
    private final ObjectMapper objectMapper;
    private final String paymentEventQueue;

    public CheckoutService(
            PaymentRepository paymentRepository,
            PaymentStateService paymentStateService,
            IdempotencyService idempotencyService,
            SqsAsyncClient sqsAsyncClient,
            ObjectMapper objectMapper,
            @Qualifier("paymentEventQueue") String paymentEventQueue) {
        this.paymentRepository = paymentRepository;
        this.paymentStateService = paymentStateService;
        this.idempotencyService = idempotencyService;
        this.sqsAsyncClient = sqsAsyncClient;
        this.objectMapper = objectMapper;
        this.paymentEventQueue = paymentEventQueue;
    }

    @Transactional
    public CheckoutResponse checkout(CheckoutRequest request) {
        // 1. Check idempotency cache
        Optional<String> cached = idempotencyService.getResponse(request.idempotencyKey());
        if (cached.isPresent()) {
            try {
                return objectMapper.readValue(cached.get(), CheckoutResponse.class);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to deserialize cached response", e);
            }
        }

        // 2. Guard against duplicate orderId
        if (paymentRepository.findByOrderId(request.orderId()).isPresent()) {
            throw new DuplicatePaymentException("Payment already exists for orderId: " + request.orderId());
        }

        // 3. Persist payment in PENDING state
        Payment payment = Payment.builder()
                .orderId(request.orderId())
                .customerId(request.customerId())
                .amount(request.amount())
                .currency(request.currency())
                .status(PaymentStatus.PENDING)
                .idempotencyKey(request.idempotencyKey())
                .build();
        payment = paymentRepository.save(payment);

        // 4. Simulate authorization
        payment = paymentStateService.transition(payment, PaymentStatus.AUTHORIZED);

        // 5. Publish event to SQS
        try {
            String eventJson = objectMapper.writeValueAsString(Map.of(
                    "paymentId", payment.getId().toString(),
                    "orderId", payment.getOrderId(),
                    "amount", payment.getAmount().toString(),
                    "currency", payment.getCurrency()
            ));
            sqsAsyncClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(paymentEventQueue)
                    .messageBody(eventJson)
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize SQS event", e);
        }

        // 6. Build and cache response
        CheckoutResponse response = new CheckoutResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getCreatedAt()
        );

        try {
            String responseJson = objectMapper.writeValueAsString(response);
            idempotencyService.checkAndStore(request.idempotencyKey(), responseJson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize checkout response for caching", e);
        }

        return response;
    }
}
