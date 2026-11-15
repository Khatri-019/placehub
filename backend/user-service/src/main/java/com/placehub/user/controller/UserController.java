package com.placehub.user.controller;

import com.placehub.common.paging.PageResponse;
import com.placehub.common.security.AuthUser;
import com.placehub.user.domain.AccountStatus;
import com.placehub.user.domain.Role;
import com.placehub.user.dto.UpdateStatusRequest;
import com.placehub.user.dto.UserResponse;
import com.placehub.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/api/users/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.me(AuthUser.from(jwt).id());
    }

    @GetMapping("/api/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserResponse> list(@RequestParam(required = false) Role role,
                                           @RequestParam(required = false) AccountStatus status,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size,
                                           @RequestParam(required = false) String sort) {
        return userService.list(role, status, q, page, size, sort);
    }

    @PatchMapping("/api/admin/users/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @Valid @RequestBody UpdateStatusRequest request) {
        return userService.updateStatus(AuthUser.from(jwt).id(), id, request.status());
    }
}
