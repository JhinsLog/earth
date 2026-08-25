package com.earth.controller;

import com.earth.domain.user.User;
import com.earth.dto.EventCreateRequest;
import com.earth.dto.EventResponse;
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

    /** 지구본 뷰포트(bounding box)를 넘기면 그 안의 활성 이벤트만, 없으면 전체 최신 이벤트를 반환한다. */
    @GetMapping
    public List<EventResponse> list(
            @RequestParam(required = false) Double southLat,
            @RequestParam(required = false) Double northLat,
            @RequestParam(required = false) Double westLng,
            @RequestParam(required = false) Double eastLng) {
        return eventService.findActive(southLat, northLat, westLng, eastLng);
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
}
