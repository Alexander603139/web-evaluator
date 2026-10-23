package com.webevaluator.security;

import com.webevaluator.core.exception.SecurityPolicyViolationException;
import com.webevaluator.core.security.UrlSecurityPolicy;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;

@Component
public class DefaultUrlSecurityPolicy implements UrlSecurityPolicy {

    @Override
    public void validate(String url) {
        try {
            URI uri = new URI(url);

            // 1. Проверка схемы
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new SecurityPolicyViolationException("Only HTTPS URLs are allowed");
            }

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new SecurityPolicyViolationException("Invalid URL: missing host");
            }

            // 2. Резолв домена и проверка IP
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (isPrivateOrLoopback(address)) {
                    throw new SecurityPolicyViolationException("Access to private or loopback IP addresses is forbidden");
                }
            }
        } catch (SecurityPolicyViolationException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityPolicyViolationException("Invalid URL or DNS resolution failed: " + e.getMessage());
        }
    }

    private boolean isPrivateOrLoopback(InetAddress address) {
        byte[] ip = address.getAddress();

        // Проверка IPv4 диапазонов
        if (ip.length == 4) {
            int b1 = ip[0] & 0xFF;
            int b2 = ip[1] & 0xFF;

            if (b1 == 127) return true; // 127.0.0.0/8
            if (b1 == 10) return true;  // 10.0.0.0/8
            if (b1 == 192 && b2 == 168) return true; // 192.168.0.0/16
            if (b1 == 172 && b2 >= 16 && b2 <= 31) return true; // 172.16.0.0/12
            if (b1 == 169 && b2 == 254) return true; // 169.254.0.0/16
        }

        // Fallback для IPv6 и других специфичных адресов
        return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress();
    }
}