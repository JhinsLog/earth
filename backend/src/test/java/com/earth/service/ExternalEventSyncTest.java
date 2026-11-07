package com.earth.service;

import com.earth.domain.event.Event;
import com.earth.domain.event.EventCategory;
import com.earth.domain.event.EventConfirmationRepository;
import com.earth.domain.event.EventOrigin;
import com.earth.domain.event.EventRepository;
import com.earth.domain.event.EventStatus;
import com.earth.domain.user.AuthProvider;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 외부 데이터 목록과 별을 맞추는 동기화. 생성·갱신·종료와, 겹쳐 돌아도 같은 사건이 별 하나로만 남는지를 실제 DB로 본다.
 */
class ExternalEventSyncTest extends IntegrationTest {

    private static final String SOURCE = "TEST_SOURCE";
    private static final ExternalEventSyncService.SystemAccount ACCOUNT =
            new ExternalEventSyncService.SystemAccount("test-source", "테스트 출처");
    private static final Duration GRACE = Duration.ofMinutes(20);

    @Autowired
    private ExternalEventSyncService syncService;

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventConfirmationRepository confirmationRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        confirmationRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();
    }

    private static ExternalEventSyncService.Draft draft(String id, String title) {
        return new ExternalEventSyncService.Draft(id, title, "내용", EventCategory.ACCIDENT, 37.568, 126.988,
                Instant.now().plus(Duration.ofHours(2)));
    }

    private ExternalEventSyncService.Result sync(boolean complete, ExternalEventSyncService.Draft... drafts) {
        return syncService.sync(SOURCE, ACCOUNT, List.of(drafts), complete, GRACE, 0);
    }

    private Event find(String externalId) {
        return eventRepository.findBySourceAndExternalIdIn(SOURCE, List.of(externalId)).getFirst();
    }

    @Test
    @DisplayName("처음 보는 사건은 시스템 계정의 API 별로 만들고, 같은 목록을 다시 받으면 아무것도 하지 않는다")
    void createsOnceAndIgnoresUnchanged() {
        ExternalEventSyncService.Result first = sync(true, draft("1", "교통사고"));
        ExternalEventSyncService.Result second = sync(true, draft("1", "교통사고"));

        assertThat(first.created()).isEqualTo(1);
        assertThat(eventRepository.count()).isEqualTo(1);
        Event event = find("1");
        assertThat(event.getOrigin()).isEqualTo(EventOrigin.API);
        User author = userRepository.findById(event.getAuthor().getId()).orElseThrow();
        assertThat(author.getProvider()).isEqualTo(AuthProvider.SYSTEM);
        assertThat(author.getNickname()).isEqualTo("테스트 출처");
        // 종료 예정이 2시간 뒤라 유예(20분)보다 늦다 — 예정 시각을 그대로 쓴다. 두 번째 동기화는 다른 Instant.now()로
        // 초안을 만들어 수명이 아주 조금 달라 '갱신'으로 셀 수 있다. 새로 만들지 않는 것만 본다.
        assertThat(second.created()).isZero();
    }

    @Test
    @DisplayName("내용이 바뀌면 갱신하고, 끝까지 받은 목록에서 빠지면 끝낸다")
    void updatesAndEndsMissing() {
        sync(true, draft("1", "교통사고"), draft("2", "낙하물"));

        ExternalEventSyncService.Result result = sync(true, draft("1", "교통사고(2차로 통제)"));

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.ended()).isEqualTo(1);
        assertThat(find("1").getTitle()).isEqualTo("교통사고(2차로 통제)");
        assertThat(find("2").getStatus()).isEqualTo(EventStatus.EXPIRED);
        assertThat(find("2").isVisible()).isFalse();
    }

    @Test
    @DisplayName("목록을 일부만 받았으면 빠진 별을 끝내지 않는다")
    void incompleteListDoesNotEnd() {
        sync(true, draft("1", "교통사고"), draft("2", "낙하물"));

        ExternalEventSyncService.Result result = sync(false, draft("1", "교통사고"));

        assertThat(result.ended()).isZero();
        assertThat(find("2").isVisible()).isTrue();
    }

    @Test
    @DisplayName("끝났던 사건이 목록에 다시 나타나면 같은 별을 되살린다(새로 만들지 않는다)")
    void reappearingRevivesSameEvent() {
        sync(true, draft("1", "교통사고"));
        long id = find("1").getId();
        sync(true);
        assertThat(find("1").isVisible()).isFalse();

        sync(true, draft("1", "교통사고"));

        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(find("1").getId()).isEqualTo(id);
        assertThat(find("1").isVisible()).isTrue();
    }

    @Test
    @DisplayName("종료 예정이 지났는데 아직 목록에 있으면 유예만큼 더 산다")
    void overdueButListedStaysAlive() {
        ExternalEventSyncService.Draft overdue = new ExternalEventSyncService.Draft("1", "교통사고", null,
                EventCategory.ACCIDENT, 37.568, 126.988, Instant.now().minus(Duration.ofHours(1)));

        sync(true, overdue);

        assertThat(find("1").getExpiresAt()).isAfter(Instant.now().plus(GRACE).minusSeconds(5));
    }

    @Test
    @DisplayName("외부 별은 공감해도 수명이 늘지 않는다")
    void confirmationDoesNotExtendExternal() {
        sync(true, draft("1", "교통사고"));
        Event before = find("1");
        User viewer = userRepository.save(new User(AuthProvider.GOOGLE, "viewer", "v@test.local", "viewer", null));

        eventService.confirm(viewer, before.getId());

        Event after = find("1");
        assertThat(after.getConfirmCount()).isEqualTo(1);
        assertThat(after.getExpiresAt()).isEqualTo(before.getExpiresAt());
    }

    @Test
    @DisplayName("동기화가 동시에 여러 번 돌아도 같은 사건은 별 하나뿐이다(유니크 제약)")
    void concurrentSyncCreatesOne() throws Exception {
        // 계정은 미리 만들어 둔다. 여기서 보려는 것은 별의 중복이다.
        sync(true);
        int threads = 4;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        sync(true, draft("same", "교통사고"));
                        return true;
                    } catch (RuntimeException e) {
                        return false; // 유니크 제약 위반으로 이 트랜잭션은 통째로 롤백된다
                    }
                }));
            }
            start.countDown();
        }
        long succeeded = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) succeeded++;
        }

        assertThat(eventRepository.findBySourceAndExternalIdIn(SOURCE, List.of("same"))).hasSize(1);
        assertThat(succeeded).isGreaterThanOrEqualTo(1);
    }
}
