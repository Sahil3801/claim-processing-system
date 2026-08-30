package com.claim.demo.controller;

import com.claim.demo.dto.AuthTokenResponse;
import com.claim.demo.dto.RegisterUserRequest;
import com.claim.demo.dto.UserCredentialsDTO;
import com.claim.demo.dto.UserDTO;
import com.claim.demo.service.UserService;
import com.claim.demo.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserDTO> register(@Valid @RequestBody RegisterUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.registerUser(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody UserCredentialsDTO credentials) {
        try {
            UserDTO user = userService.loginUser(credentials.getUsername(), credentials.getPassword());
            String token = jwtService.createToken(user);
            return ResponseEntity.ok(new AuthTokenResponse(user.getUsername(), token));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
