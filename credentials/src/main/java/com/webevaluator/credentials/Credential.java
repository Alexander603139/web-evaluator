package com.webevaluator.credentials;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Credential {
    private CredentialType type;
    private String username;
    private String password;
    private String token;

    @JsonCreator
    public Credential(
            @JsonProperty("type") CredentialType type,
            @JsonProperty("username") String username,
            @JsonProperty("password") String password,
            @JsonProperty("token") String token) {
        this.type = type;
        this.username = username;
        this.password = password;
        this.token = token;
    }
}
