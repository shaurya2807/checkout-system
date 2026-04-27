package com.checkout.checkout_system.controller;

import com.checkout.checkout_system.dto.CheckoutRequest;
import com.checkout.checkout_system.dto.CheckoutResponse;
import com.checkout.checkout_system.service.CheckoutService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(
            @Valid @RequestBody CheckoutRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        CheckoutRequest requestWithKey = new CheckoutRequest(
                request.orderId(),
                request.customerId(),
                request.amount(),
                request.currency(),
                idempotencyKey
        );
        CheckoutResponse response = checkoutService.checkout(requestWithKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
