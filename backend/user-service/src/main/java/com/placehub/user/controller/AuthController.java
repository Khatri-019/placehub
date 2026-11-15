package com.placehub.user.controller;

import com.placehub.user.dto.LoginRequest;
import com.placehub.user.dto.LoginResponse;
import com.placehub.user.dto.RecruiterRegistrationRequest;
import com.placehub.user.dto.StudentRegistrationRequest;
import com.placehub.user.dto.UserResponse;
import com.placehub.user.service.AuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "auth")
@SecurityRequirements
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register/student")
    public ResponseEntity<UserResponse> registerStudent(@Valid @RequestBody StudentRegistrationRequest request) {
        UserResponse created = authService.registerStudent(request);
        return ResponseEntity.created(URI.create("/api/users/me")).body(created);
    }

    @PostMapping("/register/recruiter")
    public ResponseEntity<UserResponse> registerRecruiter(@Valid @RequestBody RecruiterRegistrationRequest request) {
        UserResponse created = authService.registerRecruiter(request);
        return ResponseEntity.created(URI.create("/api/users/me")).body(created);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
