package com.earth.service;

import com.earth.domain.event.Event;
import com.earth.domain.event.EventCategory;
import com.earth.domain.event.EventChanged;
import com.earth.domain.event.EventRepository;
import com.earth.domain.event.EventStatus;
import com.earth.domain.geo.CountryBoundaryRepository;
import com.earth.domain.user.AuthProvider;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.dto.EventResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 외부 데이터의 "지금 진행 중인 목록"을 별과 맞춘다.
 *
 * <p>출처는 매번 목록 전체(스냅샷)를 준다. 그래서 한 번의 동기화가 세 가지를 한다.
 * <ol>
 *   <li>처음 보는 사건 → 별 생성</li>
 *   <li>이미 있는 사건 → 내용·위치·수명 갱신. 끝났던 별이 목록에 다시 나타나면 되살린다</li>
 *   <li>목록에서 빠진 사건 → 끝남. <b>목록을 끝까지 받았을 때만</b> 한다. 일부만 받고 비교하면 진행 중인 사건이
 *       무더기로 꺼진다</li>
 * </ol>
 *
 * <p><b>중복 방지.</b> "있으면 갱신, 없으면 생성"은 조회와 저장 사이에 틈이 있다. 수집은 스케줄러 하나가 돌고 같은 예약
 * 작업은 겹치지 않지만, 백엔드를 여러 대로 늘리면 두 대가 같은 사건을 동시에 새로 볼 수 있다. 마지막 방어선은
 * DB 유니크 제약 {@code (source, external_id)}(V8)이다. 두 번째 INSERT는 제약 위반으로 트랜잭션째 실패하고, 다음 주기에
 * 이미 생긴 별을 갱신한다. 실패한 쪽의 전파는 커밋 후에만 나가므로 유령 별도 생기지 않는다.
 *
 * <p><b>수명.</b> 출처가 알려 준 종료 예정 시각을 쓰되, 목록에 있는 동안은 적어도 {@code listedGrace} 뒤까지 살린다.
 * 예정 시각이 지났는데 아직 처리 중인 사고가 만료 스케줄러에 먼저 꺼졌다가 다음 동기화에 되살아나며 깜빡이지 않게 하려는
 * 것이다. 수집이 계속 실패하면 그 유예가 지나 스스로 사라진다.
 *
 * <p>공감으로 수명이 늘지 않는다({@link EventService#confirm}). 현실의 상황이 끝나면 별도 끝나야 한다.
 */
@Service
@Transactional(readOnly = true)
public class ExternalEventSyncService {

    /** 출처 하나의 작성자 계정. 로그인 경로가 없다({@link AuthProvider#SYSTEM}). */
    public record SystemAccount(String id, String nickname) {
    }

    /** 출처의 사건 하나를 별 모양으로 옮긴 것. @param expectedEnd 종료 예정 시각. 모르면 null */
    public record Draft(String externalId, String title, String content, EventCategory category,
                        double latitude, double longitude, Instant expectedEnd) {
    }

    public record Result(int created, int updated, int ended, int skipped) {
    }

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CountryBoundaryRepository countryBoundaryRepository;
    private final ApplicationEventPublisher events;

    public ExternalEventSyncService(EventRepository eventRepository, UserRepository userRepository,
                                    CountryBoundaryRepository countryBoundaryRepository,
                                    ApplicationEventPublisher events) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.countryBoundaryRepository = countryBoundaryRepository;
        this.events = events;
    }

    /**
     * @param complete    목록 전체를 받았는지. false면 빠진 별을 끝내지 않는다
     * @param listedGrace 목록에 있는 별의 최소 남은 수명. 수집 주기의 두 배 정도
     * @param skipped     넣지 않기로 한 건수(결과 보고용)
     */
    @Transactional
    public Result sync(String source, SystemAccount account, List<Draft> drafts, boolean complete,
                       Duration listedGrace, int skipped) {
        Instant now = Instant.now();
        User author = systemUser(account);
        // 빈 목록으로 IN 조회를 하지 않는다(빈 IN 절은 DB마다 다르게 다뤄진다).
        Map<String, Event> existing = drafts.isEmpty() ? Map.of() : eventRepository
                .findBySourceAndExternalIdIn(source, drafts.stream().map(Draft::externalId).toList())
                .stream().collect(Collectors.toMap(Event::getExternalId, Function.identity()));

        int created = 0;
        int updated = 0;
        Set<String> listed = new HashSet<>();
        for (Draft draft : drafts) {
            if (!listed.add(draft.externalId())) {
                continue; // 같은 응답에 같은 ID가 두 번 오면 앞의 것만 쓴다.
            }
            Instant expiresAt = expiry(draft.expectedEnd(), now, listedGrace);
            Event event = existing.get(draft.externalId());
            if (event == null) {
                String country = countryBoundaryRepository.countryCodeAt(draft.latitude(), draft.longitude());
                event = Event.external(author, source, draft.externalId(), draft.title(), draft.content(),
                        draft.category(), draft.latitude(), draft.longitude(), country, expiresAt);
                eventRepository.save(event);
                events.publishEvent(EventChanged.created(EventResponse.from(event)));
                created++;
            } else if (event.syncExternal(draft.title(), draft.content(), draft.category(),
                    draft.latitude(), draft.longitude(), expiresAt)) {
                events.publishEvent(EventChanged.updated(EventResponse.from(event)));
                updated++;
            }
        }

        int ended = 0;
        if (complete) {
            for (Event event : eventRepository.findBySourceAndStatus(source, EventStatus.ACTIVE)) {
                if (!listed.contains(event.getExternalId())) {
                    event.markEndedAtSource();
                    events.publishEvent(EventChanged.updated(EventResponse.from(event)));
                    ended++;
                }
            }
        }
        return new Result(created, updated, ended, skipped);
    }

    static Instant expiry(Instant expectedEnd, Instant now, Duration listedGrace) {
        Instant floor = now.plus(listedGrace);
        return expectedEnd != null && expectedEnd.isAfter(floor) ? expectedEnd : floor;
    }

    /**
     * 출처의 작성자 계정을 찾고, 없으면 만든다. 마이그레이션으로 미리 넣지 않는 이유: 계정이 지워져도(테스트 정리 등)
     * 다음 동기화가 다시 만든다. 동시에 두 곳이 만들면 (provider, provider_id) 유니크 제약이 하나만 남긴다.
     */
    private User systemUser(SystemAccount account) {
        return userRepository.findByProviderAndProviderId(AuthProvider.SYSTEM, account.id())
                .orElseGet(() -> userRepository.save(new User(AuthProvider.SYSTEM, account.id(),
                        account.id() + "@system.invalid", account.nickname(), null)));
    }
}
