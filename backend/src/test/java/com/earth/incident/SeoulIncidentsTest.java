package com.earth.incident;

import com.earth.domain.event.EventCategory;
import com.earth.service.ExternalEventSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SeoulIncidentsTest {

    private static final Instant T = Instant.parse("2026-11-07T01:00:00Z");

    private static SeoulIncident incident(String type, String detail, Duration length) {
        return new SeoulIncident("1", type, detail, T, length == null ? null : T.plus(length),
                198912.138883, 452082.387013, "내용\r둘째 줄");
    }

    @Test
    @DisplayName("교통사고는 사고 분류, 차량화재는 화재 분류로 만든다")
    void accidents() {
        Optional<ExternalEventSyncService.Draft> crash = SeoulIncidents.toDraft(incident("A01", "01B01", Duration.ofHours(1)));
        assertThat(crash).get().extracting(ExternalEventSyncService.Draft::category).isEqualTo(EventCategory.ACCIDENT);
        assertThat(crash.get().content()).isEqualTo("내용\n둘째 줄");

        assertThat(SeoulIncidents.toDraft(incident("A01", "01B04", Duration.ofHours(1))))
                .get().extracting(ExternalEventSyncService.Draft::category).isEqualTo(EventCategory.FIRE);
    }

    @Test
    @DisplayName("공사는 하루 안에 끝나는 것만, 종료 예정이 없으면 넣지 않는다")
    void construction() {
        assertThat(SeoulIncidents.toDraft(incident("A04", "04B01", Duration.ofHours(4)))).isPresent();
        assertThat(SeoulIncidents.toDraft(incident("A04", "04B01", Duration.ofDays(365)))).isEmpty();
        assertThat(SeoulIncidents.toDraft(incident("A04", "04B01", null))).isEmpty();
    }

    @Test
    @DisplayName("행사 통제는 10B01만, 집회(10B02)와 모르는 유형은 넣지 않는다")
    void eventsAndUnknown() {
        assertThat(SeoulIncidents.toDraft(incident("A10", "10B01", Duration.ofHours(3)))).isPresent();
        assertThat(SeoulIncidents.toDraft(incident("A10", "10B02", Duration.ofHours(3)))).isEmpty();
        assertThat(SeoulIncidents.toDraft(incident("A99", "99B01", Duration.ofHours(3)))).isEmpty();
    }
}
