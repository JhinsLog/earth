package com.earth.exp;

import com.earth.domain.flare.FlareOrigin;
import com.earth.domain.flare.ConfirmationKind;
import com.earth.domain.flare.FlareCategory;
import com.earth.domain.flare.FlareConfirmationRepository;
import com.earth.domain.flare.FlareRepository;
import com.earth.domain.user.AuthProvider;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.dto.FlareCreateRequest;
import com.earth.service.ExternalFlareSyncService;
import com.earth.service.FlareService;
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
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** 플레어가 끝나면 사실 확인 수로 작성자 경험치를 한 번 정산하는지(D-21) 실제 DB로 본다. */
class ExpSettlementTest extends IntegrationTest {

    @Autowired
    private ExpSettlementService settlementService;

    @Autowired
    private FlareService flareService;

    @Autowired
    private ExternalFlareSyncService syncService;

    @Autowired
    private FlareRepository flareRepository;

    @Autowired
    private FlareConfirmationRepository confirmationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpLedgerRepository ledgerRepository;

    // 만료 시각을 과거로 옮기거나 시작 경험치를 정하는 것은 운영 코드에 테스트용 setter를 두지 않고 SQL로 한다.
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void tearDown() {
        confirmationRepository.deleteAll();
        flareRepository.deleteAll();
        userRepository.deleteAll();
    }

    private long flareWith(User author, int seen, int ended, int disputed) {
        long flareId = flareService.create(author, new FlareCreateRequest("정산 테스트", "내용", FlareCategory.ETC,
                37.5665, 126.9780)).id();
        int n = 0;
        for (int i = 0; i < seen; i++) flareService.confirm(newUser("s" + flareId + "-" + n++), flareId, ConfirmationKind.SEEN);
        for (int i = 0; i < ended; i++) flareService.confirm(newUser("e" + flareId + "-" + n++), flareId, ConfirmationKind.ENDED);
        for (int i = 0; i < disputed; i++) flareService.confirm(newUser("d" + flareId + "-" + n++), flareId, ConfirmationKind.DISPUTED);
        return flareId;
    }

    private void expire(long flareId) {
        jdbc.update("update flares set expires_at = now() - interval '1 minute' where id = ?", flareId);
    }

    private int expOf(User user) {
        return userRepository.findById(user.getId()).orElseThrow().getExp();
    }

    @Test
    @DisplayName("만료된 플레어는 봤어요 수로 작성자 경험치를 올리고, 원장에 수와 규칙을 남긴다")
    void settlesOnExpiry() {
        User author = newUser("author");
        long flareId = flareWith(author, 7, 2, 1);

        assertThat(settlementService.settle(flareId)).as("아직 보이는 플레어는 정산하지 않는다").isEmpty();
        expire(flareId);
        assertThat(settlementService.findDue(100)).contains(flareId);

        ExpSettlementService.Settlement s = settlementService.settle(flareId).orElseThrow();
        assertThat(s.delta()).as("7명 → 2단계 × 5점").isEqualTo(10);
        assertThat(expOf(author)).isEqualTo(10);

        List<ExpLedgerEntry> ledger = ledgerRepository.findByFlareId(flareId);
        assertThat(ledger).singleElement().satisfies(e -> {
            assertThat(e.getUserId()).isEqualTo(author.getId());
            assertThat(e.getSeenCount()).isEqualTo(7);
            assertThat(e.getEndedCount()).isEqualTo(2);
            assertThat(e.getDisputedCount()).isEqualTo(1);
            assertThat(e.getExpBefore()).isZero();
            assertThat(e.getExpAfter()).isEqualTo(10);
            assertThat(e.getRule()).contains("seen/3*5");
        });
    }

    @Test
    @DisplayName("한 번 정산한 플레어는 다시 정산하지 않는다")
    void settlesOnce() {
        User author = newUser("author");
        long flareId = flareWith(author, 3, 0, 0);
        expire(flareId);

        assertThat(settlementService.settle(flareId)).isPresent();
        assertThat(settlementService.settle(flareId)).isEmpty();
        assertThat(settlementService.findDue(100)).doesNotContain(flareId);
        assertThat(expOf(author)).isEqualTo(5);
        assertThat(ledgerRepository.findByFlareId(flareId)).hasSize(1);
    }

    @Test
    @DisplayName("사실과 달라요가 다수면 깎고, 0 아래로는 내려가지 않는다")
    void penaltyFloorsAtZero() {
        User author = newUser("author");
        jdbc.update("update users set exp = 7 where id = ?", author.getId());
        long flareId = flareWith(author, 1, 0, 6);
        expire(flareId);

        ExpSettlementService.Settlement s = settlementService.settle(flareId).orElseThrow();
        assertThat(s.delta()).isEqualTo(-10);
        assertThat(s.expBefore()).isEqualTo(7);
        assertThat(s.expAfter()).isZero();
    }

    @Test
    @DisplayName("작성자가 지운 플레어도 정산한다 — 감점을 피하려고 지우는 것을 막는다")
    void deletedFlareIsSettled() {
        User author = newUser("author");
        jdbc.update("update users set exp = 20 where id = ?", author.getId());
        long flareId = flareWith(author, 0, 0, 3);
        flareService.delete(author, flareId);

        assertThat(settlementService.settle(flareId)).get()
                .extracting(ExpSettlementService.Settlement::delta).isEqualTo(-5);
        assertThat(expOf(author)).isEqualTo(15);
    }

    @Test
    @DisplayName("외부 데이터 플레어(시스템 계정)는 정산하지 않는다")
    void externalFlaresAreSkipped() {
        syncService.sync("TEST_EXP", FlareOrigin.PUBLIC, new ExternalFlareSyncService.SystemAccount("test-exp", "테스트"),
                List.of(new ExternalFlareSyncService.Draft("x", "돌발", "내용", FlareCategory.ACCIDENT, 37.5, 127.0,
                        Instant.now().plus(Duration.ofHours(1)))), false, Duration.ofMinutes(20), 0);
        long flareId = flareRepository.findBySourceAndExternalIdIn("TEST_EXP", List.of("x")).getFirst().getId();
        expire(flareId);

        assertThat(settlementService.findDue(100)).doesNotContain(flareId);
        assertThat(settlementService.settle(flareId)).isEmpty();
    }

    @Test
    @DisplayName("같은 플레어를 여러 곳이 동시에 정산해도 경험치는 한 번만 오른다")
    void concurrentSettlementIsOnce() throws Exception {
        User author = newUser("author");
        long flareId = flareWith(author, 6, 0, 0);
        expire(flareId);

        int workers = 6;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Optional<ExpSettlementService.Settlement>>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(workers)) {
            for (int i = 0; i < workers; i++) {
                Callable<Optional<ExpSettlementService.Settlement>> task = () -> {
                    start.await();
                    return settlementService.settle(flareId);
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            int settled = 0;
            for (Future<Optional<ExpSettlementService.Settlement>> f : results) {
                if (f.get().isPresent()) settled++;
            }
            assertThat(settled).isEqualTo(1);
        }
        assertThat(expOf(author)).isEqualTo(10);
        assertThat(ledgerRepository.findByFlareId(flareId)).hasSize(1);
    }

    private User newUser(String key) {
        return userRepository.save(new User(AuthProvider.GOOGLE, key, key));
    }
}
