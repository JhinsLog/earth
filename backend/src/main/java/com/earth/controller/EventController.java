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
            @RequestParam(required = false) Double southLat,
            @RequestParam(required = false) Double northLat,
            @RequestParam(required = false) Double westLng,
            @RequestParam(required = false) Double eastLng) {
        return eventService.findVisible(southLat, northLat, westLng, eastLng);
    }

    @GetMapping("/{eventId}")
    public EventResponse get(@PathVariable Long eventId) {
        return eventService.findById(eventId);
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

    /** 별 삭제. 작성자 본인만 가능하다. 채팅 이력이 이 별을 참조하므로 소프트 삭제한다. */
    @DeleteMapping("/{eventId}")
    public EventResponse delete(@AuthenticationPrincipal User actor, @PathVariable Long eventId) {
        return eventService.delete(actor, eventId);
    }
}
