package com.webevaluator.uiactions;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
public class NetworkError {
    private final String url;
    private final String method;
    private final String failureText;
    private final OffsetDateTime timestamp;

    public NetworkError(String url, String method, String failureText) {
        this.url = url;
        this.method = method;
        this.failureText = failureText;
        this.timestamp = OffsetDateTime.now();
    }
}