package com.earth.service;

import com.earth.config.EventProperties;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class EventService {

    private static final Duration RATE_LIMIT_WINDOW = Duration.ofHours(1);

    private final EventRepository eventRepository;
    private final RedisMessagePublisher redisMessagePublisher;
    private final NotificationService notificationService;
    private final EventProperties eventProperties;

    public EventService(EventRepository eventRepository,
                         RedisMessagePublisher redisMessagePublisher,
                         NotificationService notificationService,
                         EventProperties eventProperties) {
        this.eventRepository = eventRepository;
        this.redisMessagePublisher = redisMessagePublisher;
        this.notificationService = notificationService;
        this.eventProperties = eventProperties;
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
        // 한 사람이 지도를 도배하지 못하도록 최근 1시간 등록 수를 확인한다.
        // 클라이언트에서도 안내하지만 그쪽은 우회 가능하므로 실제 차단은 여기서 한다.
        long recentCount =
                eventRepository.countByAuthorAndCreatedAtAfter(author, Instant.now().minus(RATE_LIMIT_WINDOW));
        if (recentCount >= eventProperties.maxPerHour()) {
            throw new EarthApiException(
                    ErrorCode.EVENT_RATE_LIMIT_EXCEEDED,
                    "1시간에 최대 %d개까지 등록할 수 있습니다. 잠시 후 다시 시도해 주세요."
                            .formatted(eventProperties.maxPerHour()));
        }

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
