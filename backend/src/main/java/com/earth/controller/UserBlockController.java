package com.earth.controller;

import com.earth.domain.user.User;
import com.earth.dto.BlockedUserResponse;
import com.earth.service.UserBlockService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 내 차단 목록. 차단은 PUT(몇 번 보내도 같은 결과), 해제는 DELETE. */
@RestController
@RequestMapping("/api/users/me/blocks")
public class UserBlockController {

    private final UserBlockService userBlockService;

    public UserBlockController(UserBlockService userBlockService) {
        this.userBlockService = userBlockService;
    }

    @GetMapping
    public List<BlockedUserResponse> mine(@AuthenticationPrincipal User user) {
        return userBlockService.findMine(user);
    }

    @PutMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@AuthenticationPrincipal User user, @PathVariable Long userId) {
        userBlockService.block(user, userId);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@AuthenticationPrincipal User user, @PathVariable Long userId) {
        userBlockService.unblock(user, userId);
    }
}
