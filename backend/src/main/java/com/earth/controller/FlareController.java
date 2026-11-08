package com.earth.controller;

import com.earth.domain.user.User;
import com.earth.dto.FlareCreateRequest;
import com.earth.dto.FlareResponse;
import com.earth.dto.FlareUpdateRequest;
import com.earth.security.geo.ViewerCountryResolver;
import com.earth.service.FlareService;
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

    public FlareController(FlareService flareService, ViewerCountryResolver viewerCountryResolver) {
        this.flareService = flareService;
        this.viewerCountryResolver = viewerCountryResolver;
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
     * "나도 봤다" — 목격 확인. 쌓일수록 플레어의 수명이 늘고 라스에서 더 밝게 보인다.
     */
    @PostMapping("/{flareId}/confirm")
    public FlareResponse confirm(@AuthenticationPrincipal User user, @PathVariable Long flareId) {
        return flareService.confirm(user, flareId);
    }

    @DeleteMapping("/{flareId}/confirm")
    public FlareResponse withdrawConfirmation(@AuthenticationPrincipal User user,
                                               @PathVariable Long flareId) {
        return flareService.withdrawConfirmation(user, flareId);
    }

    /** 플레어 삭제. 작성자 본인만 가능하다. 채팅 이력이 이 플레어를 참조하므로 소프트 삭제한다. */
    @DeleteMapping("/{flareId}")
    public FlareResponse delete(@AuthenticationPrincipal User actor, @PathVariable Long flareId) {
        return flareService.delete(actor, flareId);
    }
}
