package com.placehub.notification.controller;

import com.placehub.common.paging.PageResponse;
import com.placehub.common.security.AuthUser;
import com.placehub.notification.dto.NotificationResponse;
import com.placehub.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** The recipient is always the JWT subject; there is no way to read someone else's notifications. */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size) {
        return service.list(AuthUser.from(jwt).id(), unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("count", service.unreadCount(AuthUser.from(jwt).id()));
    }

    @PatchMapping("/read-all")
    public Map<String, Long> readAll(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("updated", service.markAllRead(AuthUser.from(jwt).id()));
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse read(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return service.markRead(AuthUser.from(jwt).id(), id);
    }
}
