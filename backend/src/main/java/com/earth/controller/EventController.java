package com.earth.controller;

import com.earth.domain.user.User;
import com.earth.dto.EventCreateRequest;
import com.earth.dto.EventResponse;
import com.earth.dto.EventUpdateRequest;
import com.earth.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    /** 지구본 뷰포트(bounding box)를 넘기면 그 안의 별만, 없으면 전체 최신 별을 반환한다. */
    @GetMapping
    public List<EventResponse> list(
            @AuthenticationPrincipal User viewer,
            @RequestParam(required = false) Double southLat,
            @RequestParam(required = false) Double northLat,
            @RequestParam(required = false) Double westLng,
            @RequestParam(required = false) Double eastLng) {
        // 비로그인도 조회할 수 있으므로 viewer가 null일 수 있다. 그때는 공감 여부가 전부 false다.
        return eventService.findVisible(viewer, southLat, northLat, westLng, eastLng);
    }

    @GetMapping("/{eventId}")
    public EventResponse get(@AuthenticationPrincipal User viewer, @PathVariable Long eventId) {
        return eventService.findById(viewer, eventId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@AuthenticationPrincipal User author,
                                 @Valid @RequestBody EventCreateRequest request) {
        return eventService.create(author, request);
    }

    /** 별 수정. 작성자 본인만 가능하며 위치는 바꿀 수 없다. */
    @PutMapping("/{eventId}")
    public EventResponse update(@AuthenticationPrincipal User actor,
                                 @PathVariable Long eventId,
                                 @Valid @RequestBody EventUpdateRequest request) {
        return eventService.update(actor, eventId, request);
    }

    /**
     * "나도 봤다" — 목격 확인. 쌓일수록 별의 수명이 늘고 지구본에서 더 밝게 보인다.
     *
     * <p>등록자와의 거리 검사는 클라이언트가 한다(서버는 사용자 위치를 받지 않는 정책).
     */
    @PostMapping("/{eventId}/confirm")
    public EventResponse confirm(@AuthenticationPrincipal User user, @PathVariable Long eventId) {
        return eventService.confirm(user, eventId);
    }

    @DeleteMapping("/{eventId}/confirm")
    public EventResponse withdrawConfirmation(@AuthenticationPrincipal User user,
                                               @PathVariable Long eventId) {
        return eventService.withdrawConfirmation(user, eventId);
    }

    /** 별 삭제. 작성자 본인만 가능하다. 채팅 이력이 이 별을 참조하므로 소프트 삭제한다. */
    @DeleteMapping("/{eventId}")
    public EventResponse delete(@AuthenticationPrincipal User actor, @PathVariable Long eventId) {
        return eventService.delete(actor, eventId);
    }
}
