package com.earth.service;

import com.earth.domain.flare.FlareOrigin;
import com.earth.domain.flare.ConfirmationKind;
import com.earth.domain.flare.Flare;
import com.earth.domain.flare.FlareConfirmationEvent;
import com.earth.domain.flare.FlareConfirmationEventRepository;
import com.earth.domain.flare.FlareCategory;
import com.earth.domain.flare.FlareConfirmationRepository;
import com.earth.domain.flare.FlareRepository;
import com.earth.domain.user.AuthProvider;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.dto.FlareCreateRequest;
import com.earth.dto.FlareResponse;
import com.earth.exception.EarthApiException;
import com.earth.exception.ErrorCode;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 사실 확인 세 종류(나도 봤어요·끝났어요·사실과 달라요, D-20). 한 사람 한 표, 종류를 바꾸면 이전 것을 정확히 되돌리는지,
 * 동시에 눌러도 종류별 수가 어긋나지 않는지를 실제 DB로 본다.
 */
class FlareConfirmationKindsTest extends IntegrationTest {

    @Autowired
    private FlareService flareService;

    @Autowired
    private ExternalFlareSyncService syncService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FlareRepository flareRepository;

    @Autowired
    private FlareConfirmationRepository confirmationRepository;

    @Autowired
    private FlareConfirmationEventRepository eventRepository;

    @AfterEach
    void tearDown() {
        confirmationRepository.deleteAll();
        flareRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("봤어요 → 끝났어요로 바꾸면 봤어요 수와 늘려준 수명이 되돌아가고 끝났어요 수가 오른다")
    void switchingRevertsPrevious() {
        User author = userRepository.save(user("author"));
        User viewer = userRepository.save(user("viewer"));
        long flareId = flareService.create(author, request()).id();
        Duration base = lifetimeOf(flareId);

        FlareResponse seen = flareService.confirm(viewer, flareId, ConfirmationKind.SEEN);
        assertThat(seen.confirmCount()).isEqualTo(1);
        assertThat(seen.myConfirmation()).isEqualTo(ConfirmationKind.SEEN);
        assertThat(lifetimeOf(flareId)).isGreaterThan(base);

        FlareResponse ended = flareService.confirm(viewer, flareId, ConfirmationKind.ENDED);
        assertThat(ended.confirmCount()).isZero();
        assertThat(ended.endedCount()).isEqualTo(1);
        assertThat(ended.myConfirmation()).isEqualTo(ConfirmationKind.ENDED);
        assertThat(ended.confirmedByMe()).isFalse();
        assertThat(lifetimeOf(flareId)).as("봤어요가 늘려준 수명을 되돌린다").isEqualTo(base);
        assertThat(confirmationRepository.count()).as("한 사람 한 행").isEqualTo(1);
    }

    @Test
    @DisplayName("같은 종류를 다시 누르면 거절하고, 취소하면 그 종류의 수만 내려간다")
    void sameKindRejectedAndWithdrawDecrements() {
        User author = userRepository.save(user("author"));
        User viewer = userRepository.save(user("viewer"));
        long flareId = flareService.create(author, request()).id();

        flareService.confirm(viewer, flareId, ConfirmationKind.DISPUTED);
        assertThatThrownBy(() -> flareService.confirm(viewer, flareId, ConfirmationKind.DISPUTED))
                .isInstanceOf(EarthApiException.class)
                .extracting(e -> ((EarthApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_CONFIRMED);

        FlareResponse withdrawn = flareService.withdrawConfirmation(viewer, flareId);
        assertThat(withdrawn.disputedCount()).isZero();
        assertThat(withdrawn.myConfirmation()).isNull();
        assertThat(confirmationRepository.count()).isZero();
    }

    @Test
    @DisplayName("봤어요·끝났어요를 오가며 반복해도 한 사람의 순 기여는 수명 1회분을 넘지 않는다")
    void flipFloppingCannotFarmLifetime() {
        User author = userRepository.save(user("author"));
        User viewer = userRepository.save(user("viewer"));
        long flareId = flareService.create(author, request()).id();
        Duration base = lifetimeOf(flareId);

        for (int i = 0; i < 5; i++) {
            flareService.confirm(viewer, flareId, ConfirmationKind.SEEN);
            flareService.confirm(viewer, flareId, ConfirmationKind.ENDED);
        }
        assertThat(lifetimeOf(flareId)).isEqualTo(base);
        flareService.confirm(viewer, flareId, ConfirmationKind.SEEN);
        Flare reloaded = flareRepository.findById(flareId).orElseThrow();
        assertThat(reloaded.getConfirmCount()).isEqualTo(1);
        assertThat(reloaded.getEndedCount()).isZero();
    }

    @Test
    @DisplayName("여러 사람이 동시에 종류를 바꿔도 종류별 수가 정확하다")
    void concurrentSwitchesKeepCountsExact() throws InterruptedException {
        User author = userRepository.save(user("author"));
        long flareId = flareService.create(author, request()).id();
        int people = 12;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < people; i++) {
            User u = userRepository.save(user("p-" + i));
            users.add(u);
            flareService.confirm(u, flareId, ConfirmationKind.SEEN);
        }

        // 절반은 끝났어요, 절반은 사실과 달라요로 동시에 바꾼다.
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(people);
        try (ExecutorService pool = Executors.newFixedThreadPool(people)) {
            for (int i = 0; i < people; i++) {
                User u = users.get(i);
                ConfirmationKind kind = i % 2 == 0 ? ConfirmationKind.ENDED : ConfirmationKind.DISPUTED;
                pool.execute(() -> {
                    try {
                        start.await();
                        flareService.confirm(u, flareId, kind);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        Flare reloaded = flareRepository.findById(flareId).orElseThrow();
        assertThat(reloaded.getConfirmCount()).isZero();
        assertThat(reloaded.getEndedCount()).isEqualTo(people / 2);
        assertThat(reloaded.getDisputedCount()).isEqualTo(people / 2);
        assertThat(confirmationRepository.count()).isEqualTo(people);
    }

    @Test
    @DisplayName("누르기·바꾸기·취소가 이력에 순서대로 남는다(지금 상태 테이블은 덮어쓰거나 지우므로)")
    void historyIsRecorded() {
        User author = userRepository.save(user("author"));
        User viewer = userRepository.save(user("viewer"));
        long flareId = flareService.create(author, request()).id();

        flareService.confirm(viewer, flareId, ConfirmationKind.SEEN);
        flareService.confirm(viewer, flareId, ConfirmationKind.ENDED);
        flareService.withdrawConfirmation(viewer, flareId);

        assertThat(eventRepository.findByFlareIdOrderByIdAsc(flareId))
                .extracting(FlareConfirmationEvent::getAction, FlareConfirmationEvent::getFromKind,
                        FlareConfirmationEvent::getToKind, FlareConfirmationEvent::getUserId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(FlareConfirmationEvent.Action.ADD, null, ConfirmationKind.SEEN, viewer.getId()),
                        org.assertj.core.groups.Tuple.tuple(FlareConfirmationEvent.Action.CHANGE, ConfirmationKind.SEEN, ConfirmationKind.ENDED, viewer.getId()),
                        org.assertj.core.groups.Tuple.tuple(FlareConfirmationEvent.Action.WITHDRAW, ConfirmationKind.ENDED, null, viewer.getId()));
    }

    @Test
    @DisplayName("공식 안내를 옮긴 외부 레저/행사 플레어는 사실 확인을 받지 않는다")
    void officialNoticeRejectsConfirmations() {
        User viewer = userRepository.save(user("viewer"));
        syncService.sync("TEST_LEISURE", FlareOrigin.FESTIVAL, new ExternalFlareSyncService.SystemAccount("test-leisure", "테스트 레저"),
                List.of(new ExternalFlareSyncService.Draft("ski", "스키장 개장", "내용", FlareCategory.LEISURE,
                        37.578, 128.316, Instant.now().plus(Duration.ofDays(3)))), false, Duration.ofHours(2), 0);
        long flareId = flareRepository.findBySourceAndExternalIdIn("TEST_LEISURE", List.of("ski")).getFirst().getId();

        assertThatThrownBy(() -> flareService.confirm(viewer, flareId, ConfirmationKind.SEEN))
                .isInstanceOf(EarthApiException.class)
                .extracting(e -> ((EarthApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONFIRMATION_CLOSED);
    }

    private Duration lifetimeOf(long flareId) {
        Flare flare = flareRepository.findById(flareId).orElseThrow();
        return Duration.between(flare.getCreatedAt(), flare.getExpiresAt());
    }

    private User user(String key) {
        return new User(AuthProvider.GOOGLE, key, key);
    }

    private FlareCreateRequest request() {
        return new FlareCreateRequest("사실 확인 테스트", "내용", FlareCategory.ETC, 37.5665, 126.9780);
    }
}
