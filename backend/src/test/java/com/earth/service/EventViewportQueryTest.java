package com.earth.service;

import com.earth.domain.event.Event;
import com.earth.domain.event.EventCategory;
import com.earth.domain.event.EventRepository;
import com.earth.domain.user.AuthProvider;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.dto.EventResponse;
import com.earth.exception.EarthApiException;
import com.earth.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 화면 범위 조회가 날짜변경선을 넘는 화면과 넓은 범위에서 올바른지 검증한다.
 *
 * <p>예전 쿼리는 {@code longitude between west and east} 하나였고 LIMIT이 없었다. 날짜변경선을 넘는
 * 화면(피지, 알래스카 서쪽 끝 등)에서는 동쪽 절반이 빠지거나 아예 빈 결과가 났고, 넓은 범위에서는
 * 결과가 무한정 커졌다.
 */
class EventViewportQueryTest extends IntegrationTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    // 정렬 기준인 created_at을 결정적으로 만들기 위해 SQL로 직접 맞춘다(운영 코드에 setter를 두지 않는다).
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void tearDown() {
        eventRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("날짜변경선을 넘는 화면은 양쪽 별을 모두 돌려주고 범위 밖은 뺀다")
    void antimeridianViewportReturnsBothSides() {
        User author = userRepository.save(user());
        Event west = eventRepository.save(event(author, -17.7, 178.0));   // 피지 서쪽
        Event east = eventRepository.save(event(author, -16.0, -179.9));  // 날짜변경선 동쪽
        eventRepository.save(event(author, -17.0, 0.0));                  // 범위 밖

        // MapLibre는 경도를 감지 않고 170~190으로 보낸다.
        List<Long> ids = idsOf(eventService.findVisible(null, null, -20.0, -10.0, 170.0, 190.0));

        assertThat(ids).containsExactlyInAnyOrder(west.getId(), east.getId());
    }

    @Test
    @DisplayName("범위 안의 별이 많아도 최신 500개만 돌려준다")
    void wideViewportIsCappedAtNewest500() {
        User author = userRepository.save(user());
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < 510; i++) {
            events.add(event(author, 37.0 + i * 0.001, 127.0));
        }
        eventRepository.saveAll(events);
        jdbc.update("update events set created_at = timestamp with time zone '2026-01-01 00:00:00+00'"
                + " + id * interval '1 second'");

        List<EventResponse> result = eventService.findVisible(null, null, 30.0, 45.0, 120.0, 135.0);

        assertThat(result).hasSize(500);
        long oldestReturned = result.stream().mapToLong(EventResponse::id).min().orElseThrow();
        long minId = events.stream().mapToLong(Event::getId).min().orElseThrow();
        assertThat(oldestReturned).as("가장 오래된 10개가 빠져야 한다").isEqualTo(minId + 10);
    }

    @Test
    @DisplayName("범위 좌표를 일부만 보내면 전 세계 조회로 넘기지 않고 거부한다")
    void partialBoundsAreRejected() {
        assertThatThrownBy(() -> eventService.findVisible(null, null, 30.0, 45.0, null, 135.0))
                .isInstanceOf(EarthApiException.class);
    }

    @Autowired
    private TransactionTemplate tx;

    /**
     * 같은 범위 쿼리가 한 연결에서 반복되면 PostgreSQL은 파라미터를 보지 않는 범용 계획으로 바꾼다.
     * 범용 계획은 범위를 아주 좁다고 가정해 위경도 인덱스에 고정되고, 별이 빽빽한 범위(한국)에서
     * 14만 행을 다 읽는다(측정용 DB에서 1~2ms → 80ms, 기존 인덱스로는 436ms).
     * 연결 하나에서 확인하려고 바깥 트랜잭션으로 묶는다. {@code pg_prepared_statements}는 세션별이다.
     */
    @Test
    @DisplayName("범위 조회는 반복돼도 범용 계획으로 바뀌지 않고 매번 값에 맞는 계획을 세운다")
    void viewportQueryNeverUsesGenericPlan() {
        User author = userRepository.save(user());
        eventRepository.save(event(author, 37.5, 127.0));

        List<Integer> plans = tx.execute(status -> {
            for (int i = 0; i < 15; i++) {
                eventService.findVisible(null, 33.0, 39.0, 124.0, 132.0);
            }
            return jdbc.query("select generic_plans, custom_plans from pg_prepared_statements"
                            + " where statement like '%latitude between%'",
                    (rs, n) -> List.of(rs.getInt(1), rs.getInt(2))).stream()
                    .reduce((a, b) -> List.of(a.get(0) + b.get(0), a.get(1) + b.get(1)))
                    .orElse(List.of(0, 0));
        });

        assertThat(plans.get(1)).as("서버 측 준비문으로 실행돼야 이 검증이 의미가 있다").isPositive();
        assertThat(plans.get(0)).as("범용 계획 사용 횟수").isZero();
    }

    private static List<Long> idsOf(List<EventResponse> responses) {
        return responses.stream().map(EventResponse::id).toList();
    }

    private static User user() {
        return new User(AuthProvider.GOOGLE, "viewport", "viewport@test.local", "viewport", null);
    }

    private static Event event(User author, double lat, double lng) {
        return new Event(author, "범위 조회", "내용", EventCategory.ETC, lat, lng, null, Duration.ofHours(1));
    }
}
