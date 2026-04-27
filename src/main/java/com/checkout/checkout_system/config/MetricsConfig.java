package com.checkout.checkout_system.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean("paymentRequestsTotal")
    public Counter paymentRequestsTotal(MeterRegistry registry) {
        return Counter.builder("payment_requests_total")
                .description("Total checkout attempts")
                .register(registry);
    }

    @Bean("paymentSuccessTotal")
    public Counter paymentSuccessTotal(MeterRegistry registry) {
        return Counter.builder("payment_success_total")
                .description("Successful authorizations")
                .register(registry);
    }

    @Bean("paymentFailureTotal")
    public Counter paymentFailureTotal(MeterRegistry registry) {
        return Counter.builder("payment_failure_total")
                .description("Failed payments")
                .register(registry);
    }

    @Bean("paymentSettledTotal")
    public Counter paymentSettledTotal(MeterRegistry registry) {
        return Counter.builder("payment_settled_total")
                .description("Settled payments")
                .register(registry);
    }
}
