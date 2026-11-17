package com.earth.controller;

import com.earth.domain.flare.ConfirmationKind;
import com.earth.domain.flare.ReportReason;
import com.earth.domain.user.User;
import com.earth.dto.FlareCreateRequest;
import com.earth.dto.FlareResponse;
import com.earth.dto.FlareUpdateRequest;
import com.earth.security.geo.ViewerCountryResolver;
import com.earth.service.FlareService;
import com.earth.service.ModerationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flares")
public class FlareController {

    private final FlareService flareService;
    private final ViewerCountryResolver viewerCountryResolver;
    private final ModerationService moderationService;

    public FlareController(FlareService flareService, ViewerCountryResolver viewerCountryResolver,
                           ModerationService moderationService) {
        this.flareService = flareService;
        this.viewerCountryResolver = viewerCountryResolver;
        this.moderationService = moderationService;
    }

    private String viewerCountry(HttpServletRequest request) {
        return viewerCountryResolver.resolve(request.getHeader(viewerCountryResolver.headerName())).orElse(null);
    }

    /** 라스 뷰포트(bounding box)를 넘기면 그 안의 플레어만, 없으면 전체 최신 플레어를 반환한다. */
    @GetMapping
    public List<FlareResponse> list(
            @AuthenticationPrincipal User viewer,
            HttpServletRequest request,
            @RequestParam(required = false) Double southLat,
            @RequestParam(required = false) Double northLat,
            @RequestParam(required = false) Double westLng,
            @RequestParam(required = false) Double eastLng) {
        // 비로그인도 조회할 수 있으므로 viewer가 null일 수 있다. 그때는 공감 여부가 전부 false이고,
        // 접속 국가 밖의 플레어는 위치와 존재만 받는다.
        return flareService.findVisible(viewer, viewerCountry(request), southLat, northLat, westLng, eastLng);
    }

    @GetMapping("/{flareId}")
    public FlareResponse get(@AuthenticationPrincipal User viewer, HttpServletRequest request,
                             @PathVariable Long flareId) {
        return flareService.findById(viewer, viewerCountry(request), flareId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FlareResponse create(@AuthenticationPrincipal User author,
                                 @Valid @RequestBody FlareCreateRequest request) {
        return flareService.create(author, request);
    }

    /** 플레어 수정. 작성자 본인만 가능하며 위치는 바꿀 수 없다. */
    @PutMapping("/{flareId}")
    public FlareResponse update(@AuthenticationPrincipal User actor,
                                 @PathVariable Long flareId,
                                 @Valid @RequestBody FlareUpdateRequest request) {
        return flareService.update(actor, flareId, request);
    }

    /**
     * 사실 확인 — 나도 봤어요(SEEN, 기본값)·끝났어요(ENDED)·사실과 달라요(DISPUTED). 한 사람은 하나만 남기고,
     * 다른 종류를 보내면 바뀐다. "나도 봤어요"가 쌓일수록 플레어의 수명이 늘고 라스에서 더 밝게 보인다.
     * kind가 없으면 SEEN이다 — 종류가 생기기 전의 화면이 그대로 동작한다.
     */
    @PostMapping("/{flareId}/confirm")
    public FlareResponse confirm(@AuthenticationPrincipal User user, @PathVariable Long flareId,
                                 @RequestParam(defaultValue = "SEEN") ConfirmationKind kind) {
        return flareService.confirm(user, flareId, kind);
    }

    @DeleteMapping("/{flareId}/confirm")
    public FlareResponse withdrawConfirmation(@AuthenticationPrincipal User user,
                                               @PathVariable Long flareId) {
        return flareService.withdrawConfirmation(user, flareId);
    }

    /**
     * 신고(D-24). 한 사람은 한 플레어에 한 번. 서로 다른 사람의 신고가 쌓이면 운영자가 보기 전까지 가려진다. 신고 수는 돌려주지
     * 않는다 — 몇 건 남았는지 보이면 맞춰서 가리기 쉽다.
     */
    @PostMapping("/{flareId}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@AuthenticationPrincipal User user, @PathVariable Long flareId,
                       @RequestParam ReportReason reason) {
        moderationService.report(user, flareId, reason);
    }

    /** 플레어 삭제. 작성자 본인만 가능하다. 채팅 이력이 이 플레어를 참조하므로 소프트 삭제한다. */
    @DeleteMapping("/{flareId}")
    public FlareResponse delete(@AuthenticationPrincipal User actor, @PathVariable Long flareId) {
        return flareService.delete(actor, flareId);
    }
}
