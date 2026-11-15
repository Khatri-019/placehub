package com.placehub.user.controller;

import com.placehub.user.dto.AcademicProfileResponse;
import com.placehub.user.dto.InternalUserResponse;
import com.placehub.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Service-to-service endpoints; protected by X-Internal-Token and never routed by the gateway. */
@RestController
@RequestMapping("/internal/users")
@Tag(name = "internal")
public class InternalUserController {

    private final UserService userService;

    public InternalUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public InternalUserResponse user(@PathVariable UUID id) {
        return userService.internalUser(id);
    }

    @GetMapping("/{id}/academic-profile")
    public AcademicProfileResponse academicProfile(@PathVariable UUID id) {
        return userService.academicProfile(id);
    }
}
