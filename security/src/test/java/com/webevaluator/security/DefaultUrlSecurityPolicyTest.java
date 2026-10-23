package com.webevaluator.security;

import com.webevaluator.core.exception.SecurityPolicyViolationException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultUrlSecurityPolicyTest {

    private final DefaultUrlSecurityPolicy policy = new DefaultUrlSecurityPolicy();

    @Test
    void shouldRejectHttpUrl() {
        SecurityPolicyViolationException ex = assertThrows(SecurityPolicyViolationException.class,
                () -> policy.validate("http://example.com"));
        assertEquals("Only HTTPS URLs are allowed", ex.getMessage());
    }

    @Test
    void shouldAllowValidHttpsUrl() {
        assertDoesNotThrow(() -> policy.validate("https://example.com"));
    }

    @Test
    void shouldRejectLocalhost() {
        SecurityPolicyViolationException ex = assertThrows(SecurityPolicyViolationException.class,
                () -> policy.validate("https://127.0.0.1"));
        assertEquals("Access to private or loopback IP addresses is forbidden", ex.getMessage());
    }

    @Test
    void shouldRejectPrivateIpClassA() {
        assertThrows(SecurityPolicyViolationException.class, () -> policy.validate("https://10.0.0.1"));
    }

    @Test
    void shouldRejectAwsMetadataEndpoint() {
        assertThrows(SecurityPolicyViolationException.class, () -> policy.validate("https://169.254.169.254"));
    }

    @Test
    void shouldRejectPrivateIpClassC() {
        assertThrows(SecurityPolicyViolationException.class, () -> policy.validate("https://192.168.1.1"));
    }
}