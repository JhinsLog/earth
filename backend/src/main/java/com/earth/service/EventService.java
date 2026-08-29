package com.earth.service;

import com.earth.config.EventProperties;
import com.earth.domain.event.Event;
import com.earth.domain.event.EventRepository;
import com.earth.domain.user.User;
import com.earth.dto.EventCreateRequest;
import com.earth.dto.EventResponse;
import com.earth.dto.EventUpdateRequest;
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

    public List<EventResponse> findVisible(Double southLat, Double northLat, Double westLng, Double eastLng) {
        Instant now = Instant.now();
        List<Event> events;
        if (southLat != null && northLat != null && westLng != null && eastLng != null) {
            events = eventRepository.findVisibleWithinBoundingBox(now, southLat, northLat, westLng, eastLng);
        } else {
            events = eventRepository.findVisibleLatest(now);
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

        Event event = new Event(author, request.title(), request.content(), request.category(),
                request.latitude(), request.longitude(),
                Duration.ofMinutes(eventProperties.ttlMinutes()));
        eventRepository.save(event);

        EventResponse response = EventResponse.from(event);
        redisMessagePublisher.publishNewEvent(response);
        notificationService.notifySubscribers(event);
        return response;
    }

    @Transactional
    public EventResponse update(User actor, Long eventId, EventUpdateRequest request) {
        Event event = getEditableOrThrow(actor, eventId);
        event.update(request.title(), request.content(), request.category());

        EventResponse response = EventResponse.from(event);
        // 다른 사용자 화면에도 수정 내용이 즉시 반영되도록 같은 채널로 전파한다.
        redisMessagePublisher.publishNewEvent(response);
        return response;
    }

    @Transactional
    public EventResponse delete(User actor, Long eventId) {
        Event event = getEditableOrThrow(actor, eventId);
        event.markDeleted();

        EventResponse response = EventResponse.from(event);
        // status가 ACTIVE가 아니므로 구독자 화면에서는 목록에서 제거된다.
        redisMessagePublisher.publishNewEvent(response);
        return response;
    }

    /**
     * TTL이 지난 별을 EXPIRED로 전환하고 전파한다.
     *
     * <p>조회는 이미 expiresAt으로 걸러지므로 이 작업이 늦어도 사용자에게 만료된 별이
     * 보이지는 않는다. 여기서는 상태 정리와, 이미 화면을 켜 두고 있는 클라이언트에게
     * 사라졌음을 알리는 실시간 전파를 담당한다.
     *
     * @return 이번에 만료 처리된 개수
     */
    @Transactional
    public int expireDue() {
        List<Event> due = eventRepository.findDueForExpiration(Instant.now());
        for (Event event : due) {
            event.markExpired();
            redisMessagePublisher.publishNewEvent(EventResponse.from(event));
        }
        return due.size();
    }

    private Event getEditableOrThrow(User actor, Long eventId) {
        Event event = getEventOrThrow(eventId);
        if (!event.isAuthor(actor)) {
            throw new EarthApiException(ErrorCode.FORBIDDEN);
        }
        if (!event.isVisible()) {
            // 이미 만료·삭제된 별은 수정도 삭제도 할 수 없다.
            throw new EarthApiException(ErrorCode.EVENT_NOT_FOUND);
        }
        return event;
    }

    Event getEventOrThrow(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new EarthApiException(ErrorCode.EVENT_NOT_FOUND));
    }
}
