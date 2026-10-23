package com.webevaluator.core.exception;

public class SecurityPolicyViolationException extends RuntimeException {
    public SecurityPolicyViolationException(String message) {
        super(message);
    }
}