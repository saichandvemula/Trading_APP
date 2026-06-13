package com.example.demo.controller;

import com.example.demo.dto.AuthStatusResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /*
     * This controller handles Angel One authentication.
     * Other market APIs need a valid JWT token and feed token before they can
     * fetch option-chain data or connect to the live WebSocket.
     */

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /*
     * Logs in to Angel One.
     *
     * You can send credentials manually:
     * {
     *   "clientCode": "your_client_code",
     *   "password": "your_password",
     *   "totp": "123456"
     * }
     *
     * If "totp" is not sent, the app generates it from ANGEL_ONE_TOTP_SECRET.
     * If clientCode/password are not sent, the app reads them from environment variables.
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody(required = false) LoginRequest request) {
        return authService.login(request == null ? LoginRequest.useConfiguredCredentials() : request);
    }

    /*
     * Logs in using only configured environment variables.
     *
     * Required env variables:
     * - ANGEL_ONE_API_KEY
     * - ANGEL_ONE_CLIENT_CODE
     * - ANGEL_ONE_PASSWORD
     * - ANGEL_ONE_TOTP_SECRET
     *
     * Call this endpoint with no request body when you want automatic TOTP login.
     */
    @PostMapping("/login/configured")
    public LoginResponse loginWithConfiguredCredentials() {
        return authService.login(LoginRequest.useConfiguredCredentials());
    }

    /*
     * Returns whether the app currently has a saved Angel One session.
     * It also shows whether a feed token is available for WebSocket market data.
     */
    @GetMapping("/status")
    public AuthStatusResponse status() {
        return authService.status();
    }
}
