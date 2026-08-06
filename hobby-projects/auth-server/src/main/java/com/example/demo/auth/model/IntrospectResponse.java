package com.example.demo.auth.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntrospectResponse(
        boolean active,
        String sub,
        @JsonProperty("client_id") String clientId,
        Long exp
) {
    public static IntrospectResponse inactive() {
        return new IntrospectResponse(false, null, null, null);
    }
}
