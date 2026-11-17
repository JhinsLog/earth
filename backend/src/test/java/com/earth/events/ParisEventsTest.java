package com.earth.events;

import com.earth.domain.flare.FlareCategory;
import com.earth.service.ExternalFlareSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParisEventsTest {

    // 2026-11-18(수) 14:00 파리 시각
    private static final Instant NOW = Instant.parse("2026-11-18T13:00:00Z");

    private static final String RESPONSE = """
            [
              {"id":"1","address_name":"Musée d'Orsay","lat_lon":{"lat":48.86,"lon":2.3266},"qfap_tags":"Expo;Peinture",
               "occurrences":"2026-11-18T09:30:00+01:00_2026-11-18T18:00:00+01:00;2026-11-19T09:30:00+01:00_2026-11-19T18:00:00+01:00"},
              {"id":"2","address_name":"Musée d'Orsay","lat_lon":{"lat":48.86,"lon":2.3266},"qfap_tags":"Expo;Photo",
               "occurrences":"2026-11-18T09:30:00+01:00_2026-11-18T21:45:00+01:00"},
              {"id":"3","address_name":"Théâtre du Châtelet","lat_lon":{"lat":48.857614,"lon":2.346758},"qfap_tags":"Spectacle musical;Théâtre",
               "occurrences":"2026-11-18T20:00:00+01:00_2026-11-19T00:30:00+01:00"},
              {"id":"4","address_name":"Gymnase","lat_lon":{"lat":48.83,"lon":2.27},"qfap_tags":"Sport",
               "occurrences":"2026-11-17T19:00:00+01:00_2026-11-17T21:00:00+01:00;2026-11-24T19:00:00+01:00_2026-11-24T21:00:00+01:00"},
              {"id":"5","address_name":"Parc","lat_lon":{"lat":48.84,"lon":2.35},"qfap_tags":"Nature",
               "occurrences":"2026-11-18T08:00:00+01:00_2026-11-18T12:00:00+01:00"},
              {"id":"6","address_name":"","lat_lon":{"lat":48.84,"lon":2.35},"qfap_tags":"Expo","occurrences":""},
              {"id":"7","address_name":"좌표 없음","lat_lon":null,"qfap_tags":"Expo","occurrences":""}
            ]""";

    private static List<ParisEvent> events() {
        return ParisEventsClient.parse(RESPONSE.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("장소나 좌표가 없는 행사는 버리고, 회차를 시각으로 읽는다")
    void parses() {
        List<ParisEvent> events = events();
        assertThat(events).extracting(ParisEvent::id).containsExactly("1", "2", "3", "4", "5");
        assertThat(events.getFirst().occurrences()).hasSize(2);
        assertThat(events.getFirst().tags()).containsExactly("Expo", "Peinture");
    }

    @Test
    @DisplayName("오늘 아직 끝나지 않은 회차가 있는 장소만, 같은 장소는 하나로 묶어 행사 수가 많은 순으로")
    void groupsTodayByVenue() {
        List<ExternalFlareSyncService.Draft> drafts = ParisEvents.toDrafts(events(), NOW, 60);

        // 체육관은 오늘 회차가 없고(어제·다음 주), 공원은 오늘 회차가 이미 끝났다.
        assertThat(drafts).extracting(ExternalFlareSyncService.Draft::title)
                .containsExactly("Musée d'Orsay 오늘의 행사", "Théâtre du Châtelet 오늘의 행사");
        ExternalFlareSyncService.Draft orsay = drafts.getFirst();
        assertThat(orsay.category()).isEqualTo(FlareCategory.LEISURE);
        assertThat(orsay.content()).startsWith("오늘 열리는 행사 2건 · 전시, ")
                .contains("11월 18일(수) 현지 시각 09:30 ~ 21:45")
                .endsWith("출처: 파리시 Que faire à Paris(ODbL)");
        assertThat(orsay.expectedEnd()).as("오늘 마지막 회차가 끝나면 내린다").isEqualTo(Instant.parse("2026-11-18T20:45:00Z"));
    }

    @Test
    @DisplayName("자정을 넘겨 끝나는 회차는 끝 시각에 날짜를 붙인다")
    void overnightEnd() {
        ExternalFlareSyncService.Draft chatelet = ParisEvents.toDrafts(events(), NOW, 60).get(1);
        assertThat(chatelet.content()).contains("현지 시각 20:00 ~ 11월 19일 00:30");
    }

    @Test
    @DisplayName("장소 수 상한을 넘으면 행사가 많은 곳부터 남긴다")
    void capped() {
        assertThat(ParisEvents.toDrafts(events(), NOW, 1)).extracting(ExternalFlareSyncService.Draft::title)
                .containsExactly("Musée d'Orsay 오늘의 행사");
    }

    @Test
    @DisplayName("같은 장소는 날이 바뀌어도 같은 외부 ID — 플레어가 이어진다. ID는 64자 안")
    void stableExternalId() {
        String a = ParisEvents.toDrafts(events(), NOW, 60).getFirst().externalId();
        String b = ParisEvents.toDrafts(events(), NOW.plusSeconds(3600), 60).getFirst().externalId();
        assertThat(a).isEqualTo(b).startsWith("venue:").hasSizeLessThanOrEqualTo(64);
    }
}
