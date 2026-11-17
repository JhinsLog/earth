package com.earth.controller;

import com.earth.domain.user.User;
import com.earth.dto.FlareResponse;
import com.earth.dto.HiddenFlareResponse;
import com.earth.service.ModerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 운영자 검열(D-24). {@code /api/admin/**}는 ADMIN 역할만 들어온다(SecurityConfig). 화면은 아직 없고 API로 다룬다.
 * 모든 조치는 moderation_actions에 운영자 ID와 메모와 함께 남는다.
 */
@RestController
@RequestMapping("/api/admin/flares")
public class AdminFlareController {

    /** 운영자 메모. 왜 그렇게 판단했는지 남긴다. */
    public record Note(@Size(max = 200) String note) {
    }

    private final ModerationService moderationService;

    public AdminFlareController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    /** 검토 목록: 가려진 플레어(오래된 것부터)와 사유별 신고 수. */
    @GetMapping("/hidden")
    public List<HiddenFlareResponse> hidden(@RequestParam(defaultValue = "50") int limit) {
        return moderationService.hidden(limit);
    }

    @PostMapping("/{flareId}/hide")
    public FlareResponse hide(@AuthenticationPrincipal User admin, @PathVariable Long flareId,
                              @Valid @RequestBody(required = false) Note note) {
        return moderationService.hide(admin, flareId, note == null ? null : note.note());
    }

    @PostMapping("/{flareId}/restore")
    public FlareResponse restore(@AuthenticationPrincipal User admin, @PathVariable Long flareId,
                                 @Valid @RequestBody(required = false) Note note) {
        return moderationService.restore(admin, flareId, note == null ? null : note.note());
    }

    @PostMapping("/{flareId}/remove")
    public FlareResponse remove(@AuthenticationPrincipal User admin, @PathVariable Long flareId,
                                @Valid @RequestBody(required = false) Note note) {
        return moderationService.remove(admin, flareId, note == null ? null : note.note());
    }
}
