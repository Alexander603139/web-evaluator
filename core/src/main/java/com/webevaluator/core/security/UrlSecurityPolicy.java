package com.webevaluator.core.security;

public interface UrlSecurityPolicy {
    void validate(String url);
}