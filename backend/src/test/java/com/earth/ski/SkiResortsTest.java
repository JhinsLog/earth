package com.earth.ski;

import com.earth.domain.flare.FlareCategory;
import com.earth.service.ExternalFlareSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkiResortsTest {

    private static final LocalDate OPENING = LocalDate.of(2026, 11, 27);
    private static final SkiSeason.Source SOURCE =
            new SkiSeason.Source("휘닉스 평창 공지", "https://example.com/notice", LocalDate.of(2026, 11, 20));

    private static SkiSeason season(LocalDate opening, LocalDate closing) {
        return season(opening, closing, true);
    }

    private static SkiSeason season(LocalDate opening, LocalDate closing, boolean confirmed) {
        return new SkiSeason("2026-27", LocalDate.of(2027, 4, 30), List.of(
                new SkiSeason.Resort("phoenix", "휘닉스 평창", 37.57864, 128.31635, opening, closing, confirmed,
                        opening == null ? null : SOURCE)));
    }

    @Test
    @DisplayName("개장일이 발표되지 않은 스키장은 띄우지 않는다")
    void noDateNoFlare() {
        assertThat(SkiResorts.toDrafts(season(null, null), OPENING)).isEmpty();
    }

    @Test
    @DisplayName("개장 30일 전부터 '개장 예정', 개장일부터 '개장'")
    void announceThenOpen() {
        SkiSeason season = season(OPENING, null);
        assertThat(SkiResorts.toDrafts(season, OPENING.minusDays(31))).isEmpty();

        List<ExternalFlareSyncService.Draft> before = SkiResorts.toDrafts(season, OPENING.minusDays(30));
        assertThat(before).singleElement().satisfies(d -> {
            assertThat(d.title()).isEqualTo("휘닉스 평창 개장 예정");
            assertThat(d.category()).isEqualTo(FlareCategory.LEISURE);
            assertThat(d.externalId()).isEqualTo("2026-27:phoenix");
        });

        assertThat(SkiResorts.toDrafts(season, OPENING)).singleElement()
                .extracting(ExternalFlareSyncService.Draft::title).isEqualTo("휘닉스 평창 개장");
    }

    @Test
    @DisplayName("목표일은 지나도 '개장(목표일 기준)' — 실제로 열었는지는 모른다")
    void targetDateIsLabelled() {
        SkiSeason season = season(OPENING, null, false);
        assertThat(SkiResorts.toDrafts(season, OPENING.minusDays(1))).singleElement()
                .extracting(ExternalFlareSyncService.Draft::title).isEqualTo("휘닉스 평창 개장 예정");
        assertThat(SkiResorts.toDrafts(season, OPENING)).singleElement()
                .extracting(ExternalFlareSyncService.Draft::title).isEqualTo("휘닉스 평창 개장(목표일 기준)");
        assertThat(SkiResorts.content(season, season.resorts().getFirst()))
                .contains("개장 목표 11월 27일(금) (언론 보도 기준, 날씨에 따라 바뀔 수 있음)");
    }

    @Test
    @DisplayName("폐장일이 있으면 그날 자정(한국 시각)에 내리고, 다음 날부터는 띄우지 않는다")
    void closing() {
        LocalDate closing = LocalDate.of(2027, 3, 14);
        SkiSeason season = season(OPENING, closing);
        assertThat(SkiResorts.toDrafts(season, closing)).singleElement()
                .extracting(ExternalFlareSyncService.Draft::expectedEnd)
                .isEqualTo(Instant.parse("2027-03-14T15:00:00Z"));
        assertThat(SkiResorts.toDrafts(season, closing.plusDays(1))).isEmpty();
    }

    @Test
    @DisplayName("폐장일이 발표되지 않았으면 파일의 유효 기한까지만 띄운다")
    void validUntilCapsUnknownClosing() {
        SkiSeason season = season(OPENING, null);
        assertThat(SkiResorts.toDrafts(season, LocalDate.of(2027, 4, 30))).hasSize(1);
        assertThat(SkiResorts.toDrafts(season, LocalDate.of(2027, 5, 1))).isEmpty();
    }

    @Test
    @DisplayName("글에는 시설 이름 외 날짜와 출처만 담고, 날짜에는 근거를 붙인다")
    void content() {
        String text = SkiResorts.content(season(OPENING, null), season(OPENING, null).resorts().getFirst());
        assertThat(text).isEqualTo("""
                2026-27 시즌
                개장 11월 27일(금) (리조트 공지 기준)
                폐장 발표 전

                출처: 휘닉스 평창 공지 (2026-11-20 확인)
                https://example.com/notice""");
    }
}
