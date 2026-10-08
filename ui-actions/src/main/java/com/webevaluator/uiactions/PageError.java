package com.webevaluator.uiactions;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
public class PageError {
    private final String message;
    private final String stack;
    private final OffsetDateTime timestamp;

    public PageError(String message, String stack) {
        this.message = message;
        this.stack = stack;
        this.timestamp = OffsetDateTime.now();
    }
}