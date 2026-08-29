package com.earth.service;

import com.earth.config.EventProperties;
import com.earth.domain.event.Event;
import com.earth.domain.event.EventConfirmation;
import com.earth.domain.event.EventConfirmationRepository;
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
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class EventService {

    private static final Duration RATE_LIMIT_WINDOW = Duration.ofHours(1);

    private final EventRepository eventRepository;
    private final EventConfirmationRepository confirmationRepository;
    private final RedisMessagePublisher redisMessagePublisher;
    private final NotificationService notificationService;
    private final EventProperties eventProperties;

    public EventService(EventRepository eventRepository,
                         EventConfirmationRepository confirmationRepository,
                         RedisMessagePublisher redisMessagePublisher,
                         NotificationService notificationService,
                         EventProperties eventProperties) {
        this.eventRepository = eventRepository;
        this.confirmationRepository = confirmationRepository;
        this.redisMessagePublisher = redisMessagePublisher;
        this.notificationService = notificationService;
        this.eventProperties = eventProperties;
    }

    public List<EventResponse> findVisible(User viewer, Double southLat, Double northLat,
                                            Double westLng, Double eastLng) {
        Instant now = Instant.now();
        List<Event> events;
        if (southLat != null && northLat != null && westLng != null && eastLng != null) {
            events = eventRepository.findVisibleWithinBoundingBox(now, southLat, northLat, westLng, eastLng);
        } else {
            events = eventRepository.findVisibleLatest(now);
        }
        // 별마다 "내가 공감했나"를 묻으면 별 수만큼 쿼리가 나간다(최대 500개). 한 번에 가져온다.
        Set<Long> confirmedIds = confirmedEventIds(viewer, events);
        return events.stream()
                .map(event -> EventResponse.from(event, confirmedIds.contains(event.getId())))
                .toList();
    }

    private Set<Long> confirmedEventIds(User viewer, List<Event> events) {
        if (viewer == null || events.isEmpty()) return Set.of();
        return confirmationRepository.findByUserAndEventIn(viewer, events).stream()
                .map(confirmation -> confirmation.getEvent().getId())
                .collect(Collectors.toSet());
    }

    public EventResponse findById(User viewer, Long eventId) {
        Event event = getEventOrThrow(eventId);
        boolean confirmed = viewer != null && confirmationRepository.existsByEventAndUser(event, viewer);
        return EventResponse.from(event, confirmed);
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

    /**
     * "나도 봤다" — 목격 확인을 남긴다.
     *
     * <p>이 서비스의 전제는 사건을 직접 보거나 겪은 사람이 별을 등록한다는 것이고, 공감은
     * 그 전제를 다른 목격자가 뒷받침하는 장치다. 쌓일수록 별이 오래 남고 더 밝게 보인다.
     *
     * <p>등록자와의 거리 검사는 클라이언트가 한다. 서버는 사용자의 위치를 받지도 저장하지도
     * 않기로 한 정책이라, 여기서 검증할 수 있는 좌표 자체가 없다. 이 거리 규칙은 보안 통제가
     * 아니라 제품 규범이며, 대량 남용은 별도의 등록 횟수 제한이 막는다.
     */
    @Transactional
    public EventResponse confirm(User user, Long eventId) {
        Event event = getVisibleOrThrow(eventId);
        if (event.isAuthor(user)) {
            throw new EarthApiException(ErrorCode.CANNOT_CONFIRM_OWN_EVENT);
        }
        if (confirmationRepository.existsByEventAndUser(event, user)) {
            throw new EarthApiException(ErrorCode.ALREADY_CONFIRMED);
        }

        confirmationRepository.save(new EventConfirmation(event, user));
        event.applyConfirmation(
                Duration.ofMinutes(eventProperties.confirmExtensionMinutes()),
                Duration.ofHours(eventProperties.maxLifetimeHours()));

        EventResponse response = EventResponse.from(event, true);
        // 다른 사람 화면에서도 별이 밝아지고 수명이 늘어난 것이 즉시 보이도록 전파한다.
        redisMessagePublisher.publishNewEvent(EventResponse.from(event));
        return response;
    }

    /** 공감 취소. 이미 늘어난 수명은 되돌리지 않는다 — 되돌리면 취소로 남의 별을 죽일 수 있다. */
    @Transactional
    public EventResponse withdrawConfirmation(User user, Long eventId) {
        Event event = getVisibleOrThrow(eventId);
        confirmationRepository.findByEventAndUser(event, user).ifPresent(confirmation -> {
            confirmationRepository.delete(confirmation);
            event.withdrawConfirmation();
        });

        EventResponse response = EventResponse.from(event, false);
        redisMessagePublisher.publishNewEvent(EventResponse.from(event));
        return response;
    }

    private Event getVisibleOrThrow(Long eventId) {
        Event event = getEventOrThrow(eventId);
        if (!event.isVisible()) {
            throw new EarthApiException(ErrorCode.EVENT_NOT_FOUND);
        }
        return event;
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
