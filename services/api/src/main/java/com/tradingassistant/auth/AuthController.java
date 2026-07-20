package com.tradingassistant.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final TokenService tokens;

    public AuthController(AuthService authService, TokenService tokens) {
        this.authService = authService;
        this.tokens = tokens;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    TokenService.AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request.email(), request.displayName(), request.password());
    }

    @PostMapping("/login")
    TokenService.AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    TokenService.AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return tokens.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshRequest request) {
        tokens.revoke(request.refreshToken());
    }

    record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 2, max = 80) String displayName,
            @NotBlank @Size(min = 10, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$") String password) {}
    record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    record RefreshRequest(@NotBlank String refreshToken) {}
}

