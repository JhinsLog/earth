package com.earth.service;

import com.earth.domain.flare.Flare;
import com.earth.domain.flare.FlareCategory;
import com.earth.domain.flare.FlareOrigin;
import com.earth.domain.flare.FlareReportRepository;
import com.earth.domain.flare.FlareRepository;
import com.earth.domain.flare.FlareStatus;
import com.earth.domain.flare.ModerationAction;
import com.earth.domain.flare.ModerationActionRepository;
import com.earth.domain.flare.ReportReason;
import com.earth.domain.user.AuthProvider;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.dto.FlareCreateRequest;
import com.earth.exception.EarthApiException;
import com.earth.exception.ErrorCode;
import com.earth.exp.ExpLedgerRepository;
import com.earth.exp.ExpSettlementService;
import com.earth.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 신고가 쌓이면 가려지고, 가려진 플레어는 작성자 말고는 볼 수 없으며, 운영자가 복구·삭제할 수 있는지 본다(D-24).
 * 기준(hide-threshold)은 기본값 3이다.
 */
class FlareReportTest extends IntegrationTest {

    @Autowired private FlareService flareService;
    @Autowired private ModerationService moderationService;
    @Autowired private ChatService chatService;
    @Autowired private ExternalFlareSyncService syncService;
    @Autowired private ExpSettlementService settlementService;
    @Autowired private FlareRepository flareRepository;
    @Autowired private FlareReportRepository reportRepository;
    @Autowired private ModerationActionRepository actionRepository;
    @Autowired private ExpLedgerRepository ledgerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void tearDown() {
        reportRepository.deleteAll();
        actionRepository.deleteAll();
        ledgerRepository.deleteAll();
        jdbc.update("delete from flare_confirmation_events");
        jdbc.update("delete from flare_confirmations");
        flareRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User user(String id) {
        return userRepository.save(new User(AuthProvider.GOOGLE, id, id));
    }

    private Flare flareBy(User author) {
        long id = flareService.create(author,
                new FlareCreateRequest("역삼동 다툼", "경찰이 왔어요", FlareCategory.ETC, 37.5, 127.03)).id();
        return flareRepository.findById(id).orElseThrow();
    }

    private Flare reload(Flare flare) {
        return flareRepository.findById(flare.getId()).orElseThrow();
    }

    private static ErrorCode codeOf(Throwable e) {
        return ((EarthApiException) e).getErrorCode();
    }

    @Test
    @DisplayName("서로 다른 3명이 신고하면 가려지고, 작성자 말고는 상세·목록·채팅·신고 모두 막힌다")
    void hiddenAfterThreshold() {
        User author = user("author");
        User viewer = user("viewer");
        Flare flare = flareBy(author);

        assertThat(moderationService.report(user("r1"), flare.getId(), ReportReason.LOCATION_EXPOSURE)).isFalse();
        assertThat(moderationService.report(user("r2"), flare.getId(), ReportReason.LOCATION_EXPOSURE)).isFalse();
        assertThat(reload(flare).getStatus()).isEqualTo(FlareStatus.ACTIVE);
        assertThat(moderationService.report(user("r3"), flare.getId(), ReportReason.PERSONAL_INFO)).isTrue();

        Flare hidden = reload(flare);
        assertThat(hidden.getStatus()).isEqualTo(FlareStatus.HIDDEN);
        assertThat(hidden.getHiddenAt()).isNotNull();
        assertThat(flareService.findVisible(viewer, "KR", null, null, null, null)).isEmpty();
        assertThatThrownBy(() -> flareService.findById(viewer, "KR", flare.getId()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.FLARE_NOT_FOUND));
        assertThatThrownBy(() -> flareService.findById(null, "KR", flare.getId()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.FLARE_NOT_FOUND));
        // 작성자는 자기 플레어가 가려졌음을 볼 수 있다.
        assertThat(flareService.findById(author, "KR", flare.getId()).status()).isEqualTo(FlareStatus.HIDDEN);
        assertThatThrownBy(() -> chatService.postMessage(flare.getId(), viewer, "안녕하세요"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.FLARE_NOT_FOUND));
        assertThatThrownBy(() -> moderationService.report(user("r4"), flare.getId(), ReportReason.ABUSE))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.FLARE_NOT_FOUND));

        assertThat(actionRepository.findByFlareIdOrderByCreatedAt(flare.getId()))
                .extracting(ModerationAction::getAction, ModerationAction::getActorId, ModerationAction::getReportCount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(ModerationAction.Kind.AUTO_HIDE, null, 3));
    }

    @Test
    @DisplayName("같은 사람은 한 번만, 작성자는 자기 플레어를, 공공 정보 플레어는 신고할 수 없다")
    void reportRules() {
        User author = user("author");
        User reporter = user("reporter");
        Flare flare = flareBy(author);

        moderationService.report(reporter, flare.getId(), ReportReason.ABUSE);
        assertThatThrownBy(() -> moderationService.report(reporter, flare.getId(), ReportReason.PERSONAL_INFO))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ALREADY_REPORTED));
        assertThatThrownBy(() -> moderationService.report(author, flare.getId(), ReportReason.ABUSE))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.CANNOT_REPORT_OWN_FLARE));
        assertThat(reload(flare).getReportCount()).isEqualTo(1);

        syncService.sync("TEST_PUBLIC", FlareOrigin.PUBLIC, new ExternalFlareSyncService.SystemAccount("test-public", "테스트"),
                List.of(new ExternalFlareSyncService.Draft("x", "돌발", "내용", FlareCategory.ACCIDENT, 37.5, 127.0,
                        Instant.now().plus(Duration.ofHours(1)))), false, Duration.ofMinutes(20), 0);
        Flare external = flareRepository.findBySourceAndExternalIdIn("TEST_PUBLIC", List.of("x")).getFirst();
        assertThatThrownBy(() -> moderationService.report(reporter, external.getId(), ReportReason.ABUSE))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.REPORT_NOT_ALLOWED));
    }

    @Test
    @DisplayName("동시에 8명이 신고해도 정확히 3번째에 한 번만 가려지고, 늦은 신고는 거절된다")
    void concurrentReportsHideOnce() throws Exception {
        Flare flare = flareBy(user("author"));
        int attempts = 8;
        List<User> reporters = new ArrayList<>();
        for (int i = 0; i < attempts; i++) reporters.add(user("c" + i));

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (User reporter : reporters) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    moderationService.report(reporter, flare.getId(), ReportReason.LOCATION_EXPOSURE);
                    return true;
                } catch (EarthApiException e) {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FLARE_NOT_FOUND);
                    return false;
                }
            }));
        }
        start.countDown();
        int accepted = 0;
        for (Future<Boolean> r : results) if (r.get(30, TimeUnit.SECONDS)) accepted++;
        pool.shutdown();

        Flare hidden = reload(flare);
        assertThat(accepted).isEqualTo(3);
        assertThat(reportRepository.count()).isEqualTo(3);
        assertThat(hidden.getStatus()).isEqualTo(FlareStatus.HIDDEN);
        assertThat(hidden.getReportCount()).isEqualTo(3);
        assertThat(actionRepository.findByFlareIdOrderByCreatedAt(flare.getId())).hasSize(1);
    }

    @Test
    @DisplayName("운영자가 복구하면 다시 보이고 신고 수가 0이 된다. 다른 사람의 신고가 다시 쌓이면 다시 가려진다")
    void restore() {
        User admin = user("admin");
        Flare flare = flareBy(user("author"));
        for (String r : List.of("r1", "r2", "r3")) moderationService.report(user(r), flare.getId(), ReportReason.ABUSE);

        moderationService.restore(admin, flare.getId(), "문제없음");
        Flare restored = reload(flare);
        assertThat(restored.getStatus()).isEqualTo(FlareStatus.ACTIVE);
        assertThat(restored.getReportCount()).isZero();
        assertThat(restored.getHiddenAt()).isNull();
        assertThat(moderationService.hidden(50)).isEmpty();

        for (String r : List.of("r4", "r5", "r6")) moderationService.report(user(r), flare.getId(), ReportReason.ABUSE);
        assertThat(reload(flare).getStatus()).isEqualTo(FlareStatus.HIDDEN);
        assertThat(actionRepository.findByFlareIdOrderByCreatedAt(flare.getId()))
                .extracting(ModerationAction::getAction)
                .containsExactly(ModerationAction.Kind.AUTO_HIDE, ModerationAction.Kind.RESTORE, ModerationAction.Kind.AUTO_HIDE);
    }

    @Test
    @DisplayName("검토 목록은 사유별 신고 수를 주고, 가려진 동안은 정산하지 않으며, 운영자 삭제는 가점 없이 정산한다")
    void reviewAndSettlement() {
        User admin = user("admin");
        User author = user("author");
        Flare flare = flareBy(author);
        // "나도 봤어요" 3명 — 보통이면 가점(+5)이다.
        for (String s : List.of("s1", "s2", "s3")) flareService.confirm(user(s), flare.getId());
        moderationService.report(user("r1"), flare.getId(), ReportReason.LOCATION_EXPOSURE);
        moderationService.report(user("r2"), flare.getId(), ReportReason.LOCATION_EXPOSURE);
        moderationService.report(user("r3"), flare.getId(), ReportReason.PERSONAL_INFO);

        assertThat(moderationService.hidden(50)).singleElement().satisfies(h -> {
            assertThat(h.reportCount()).isEqualTo(3);
            assertThat(h.reasons()).containsEntry(ReportReason.LOCATION_EXPOSURE, 2L).containsEntry(ReportReason.PERSONAL_INFO, 1L);
        });
        assertThat(settlementService.findDue(10)).doesNotContain(flare.getId());
        assertThat(settlementService.settle(flare.getId())).isEmpty();

        moderationService.remove(admin, flare.getId(), "피해자 집 위치");
        assertThat(reload(flare).getStatus()).isEqualTo(FlareStatus.REMOVED);
        assertThat(settlementService.findDue(10)).contains(flare.getId());
        assertThat(settlementService.settle(flare.getId())).hasValueSatisfying(s -> assertThat(s.delta()).isZero());
        assertThat(userRepository.findById(author.getId()).orElseThrow().getExp()).isZero();
        assertThat(moderationService.hidden(50)).isEmpty();
    }
}
