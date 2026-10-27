package com.webevaluator.credentials;
import lombok.Builder;
import lombok.Data;
@Data
@Builder
public class Credential {
    private CredentialType type;
    private String username;
    private String password;
    private String token;
}