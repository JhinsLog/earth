package com.earth.controller;

import com.earth.domain.user.User;
import com.earth.dto.NicknameChangeRequest;
import com.earth.dto.UserResponse;
import com.earth.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal User user) {
        return UserResponse.from(user);
    }

    @PatchMapping("/me/nickname")
    public UserResponse changeNickname(@AuthenticationPrincipal User user,
                                       @Valid @RequestBody NicknameChangeRequest request) {
        return userService.changeNickname(user.getId(), request.nickname());
    }
}
