package com.checkout.checkout_system.exception;

import com.checkout.checkout_system.enums.PaymentStatus;

public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(PaymentStatus from, PaymentStatus to) {
        super("Invalid transition from " + from + " to " + to);
    }
}
