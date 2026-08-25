package com.earth.service;

import com.earth.domain.event.Event;
import com.earth.domain.event.EventRepository;
import com.earth.domain.event.EventStatus;
import com.earth.domain.user.User;
import com.earth.dto.EventCreateRequest;
import com.earth.dto.EventResponse;
import com.earth.exception.EarthApiException;
import com.earth.exception.ErrorCode;
import com.earth.realtime.RedisMessagePublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final RedisMessagePublisher redisMessagePublisher;
    private final NotificationService notificationService;

    public EventService(EventRepository eventRepository,
                         RedisMessagePublisher redisMessagePublisher,
                         NotificationService notificationService) {
        this.eventRepository = eventRepository;
        this.redisMessagePublisher = redisMessagePublisher;
        this.notificationService = notificationService;
    }

    public List<EventResponse> findActive(Double southLat, Double northLat, Double westLng, Double eastLng) {
        List<Event> events;
        if (southLat != null && northLat != null && westLng != null && eastLng != null) {
            events = eventRepository.findActiveWithinBoundingBox(southLat, northLat, westLng, eastLng);
        } else {
            events = eventRepository.findTop500ByStatusOrderByCreatedAtDesc(EventStatus.ACTIVE);
        }
        return events.stream().map(EventResponse::from).toList();
    }

    public EventResponse findById(Long eventId) {
        return EventResponse.from(getEventOrThrow(eventId));
    }

    @Transactional
    public EventResponse create(User author, EventCreateRequest request) {
        Event event = new Event(author, request.title(), request.content(),
                request.category(), request.latitude(), request.longitude());
        eventRepository.save(event);

        EventResponse response = EventResponse.from(event);
        redisMessagePublisher.publishNewEvent(response);
        notificationService.notifySubscribers(event);
        return response;
    }

    Event getEventOrThrow(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new EarthApiException(ErrorCode.EVENT_NOT_FOUND));
    }
}
