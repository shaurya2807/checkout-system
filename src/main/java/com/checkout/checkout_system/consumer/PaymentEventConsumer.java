package com.checkout.checkout_system.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.List;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final SqsAsyncClient sqsAsyncClient;
    private final String paymentEventQueue;

    public PaymentEventConsumer(
            SqsAsyncClient sqsAsyncClient,
            @Qualifier("paymentEventQueue") String paymentEventQueue) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.paymentEventQueue = paymentEventQueue;
    }

    @Scheduled(fixedDelay = 5000)
    public void poll() {
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(paymentEventQueue)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(1)
                .build();

        ReceiveMessageResponse response;
        try {
            response = sqsAsyncClient.receiveMessage(receiveRequest).join();
        } catch (Exception e) {
            log.warn("Failed to receive messages from SQS: {}", e.getMessage());
            return;
        }

        List<Message> messages = response.messages();
        if (messages.isEmpty()) {
            return;
        }

        for (Message message : messages) {
            try {
                log.info("Received payment event: {}", message.body());
                sqsAsyncClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(paymentEventQueue)
                        .receiptHandle(message.receiptHandle())
                        .build()).join();
                log.debug("Deleted message with receipt handle: {}", message.receiptHandle());
            } catch (Exception e) {
                log.error("Failed to process/delete message {}: {}", message.messageId(), e.getMessage());
            }
        }
    }
}
