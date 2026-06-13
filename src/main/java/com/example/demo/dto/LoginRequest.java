package com.example.demo.dto;

/*
 * Request body for Angel One login.
 *
 * All fields are optional because the app can read them from environment variables:
 * - ANGEL_ONE_CLIENT_CODE
 * - ANGEL_ONE_PASSWORD
 * - ANGEL_ONE_TOTP_SECRET
 *
 * If "totp" is empty, AuthService generates the current 6-digit TOTP
 * using ANGEL_ONE_TOTP_SECRET.
 */
public record LoginRequest(
        String clientCode,
        String password,
        String totp
) {
    public static LoginRequest useConfiguredCredentials() {
        return new LoginRequest(null, null, null);
    }
}
