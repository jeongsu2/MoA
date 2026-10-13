package com.moa.auth;

public record CsrfResponse(String headerName, String token) {
}
