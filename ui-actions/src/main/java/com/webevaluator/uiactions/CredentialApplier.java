package com.webevaluator.uiactions;

import com.microsoft.playwright.Page;
import com.webevaluator.credentials.Credential;
import com.webevaluator.credentials.CredentialType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class CredentialApplier {
    private final PlaywrightActions playwright;

    /** Вызывать ДО navigate() */
    public void applyHeaders(Credential credential) {
        if (credential == null) return;
        if (credential.getType() == CredentialType.BEARER_TOKEN) {
            playwright.setExtraHeader("Authorization", "Bearer " + credential.getToken());
        } else if (credential.getType() == CredentialType.BASIC_AUTH) {
            String basic = Base64.getEncoder().encodeToString(
                    (credential.getUsername() + ":" + credential.getPassword()).getBytes());
            playwright.setExtraHeader("Authorization", "Basic " + basic);
        }
    }

    /** Вызывать ПОСЛЕ navigate() */
    public void applyForm(Credential credential) {
        if (credential == null || credential.getType() != CredentialType.FORM_LOGIN) return;
        try {
            Page page = playwright.getPage();
            String passSel = "input[type='password']";
            if (page.isVisible(passSel)) {
                page.fill(passSel, credential.getPassword());
                log.info("Filled password field");
                page.fill("input[type='text'], input[type='email'] >> nth=0", credential.getUsername());
                log.info("Filled username field");
                String btn = "input[type='submit'], button[type='submit'], button";
                if (page.isVisible(btn + " >> nth=0")) page.click(btn + " >> nth=0");
                playwright.waitForLoadState("networkidle");
            } else {
                log.warn("Login form not found");
            }
        } catch (Exception e) {
            log.error("Form login failed: {}", e.getMessage());
        }
    }
}