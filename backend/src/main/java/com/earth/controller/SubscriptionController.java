package com.earth.controller;

import com.earth.domain.user.User;
import com.earth.dto.SubscriptionRequest;
import com.earth.dto.SubscriptionResponse;
import com.earth.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    public List<SubscriptionResponse> mine(@AuthenticationPrincipal User user) {
        return subscriptionService.findMine(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse create(@AuthenticationPrincipal User user,
                                        @Valid @RequestBody SubscriptionRequest request) {
        return subscriptionService.create(user, request);
    }

    @DeleteMapping("/{subscriptionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable Long subscriptionId) {
        subscriptionService.delete(user, subscriptionId);
    }
}
