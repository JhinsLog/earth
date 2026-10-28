package com.earth.domain.geo;

import com.earth.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 국가 판정(V6의 country_code_at) — 국경 안 → 12해리(22,224m) 안 최근접 → 없음.
 *
 * <p>실제 마이그레이션 데이터(Natural Earth 한국 관점, 0.01° 단순화)로 검증한다. 규칙만이 아니라
 * 데이터 선택(관점·단순화)이 결과를 바꾸기 때문이다. 일본 관점 데이터라면 독도가 JP로 나온다.
 */
class CountryJudgmentTest extends IntegrationTest {

    @Autowired
    private CountryBoundaryRepository countryBoundaryRepository;

    @ParameterizedTest(name = "{0} → {3}")
    @DisplayName("국경 안이면 그 나라")
    @CsvSource({
            "서울, 37.5665, 126.9780, KR",
            "백령도, 37.9590, 124.6650, KR",
            "대마도, 34.4000, 129.3000, JP",
            "파리(iso_a2가 -99인 나라), 48.8566, 2.3522, FR",
            "프리슈티나(코소보), 42.6629, 21.1655, XK",
            "타이베이, 25.0330, 121.5654, TW",
    })
    void insideBorder(String place, double lat, double lng, String expected) {
        assertThat(countryBoundaryRepository.countryCodeAt(lat, lng)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {3}")
    @DisplayName("국경 밖이어도 12해리 안이면 가장 가까운 나라 — 데이터가 어긋나거나 빠진 섬")
    @CsvSource({
            // 독도 폴리곤은 점 5개짜리이고 실제보다 약 0.5km 서쪽에 있다. 실제 좌표는 국경 밖이라 이 단계에서 판정된다.
            "독도 동도, 37.2414, 131.8697, KR",
            "독도 서도, 37.2425, 131.8644, KR",
            // 데이터에 아예 없는 섬
            "마라도, 33.1170, 126.2670, KR",
            "연평도 남쪽 바다, 37.6380, 125.6990, KR",
    })
    void nearestWithinTerritorialSea(String place, double lat, double lng, String expected) {
        assertThat(countryBoundaryRepository.countryCodeAt(lat, lng)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → 없음")
    @DisplayName("12해리 안에 육지가 없으면 어느 나라도 아니다")
    @CsvSource({
            "대한해협 한가운데, 34.9, 129.2",
            "태평양 한가운데, 0.0, -150.0",
    })
    void highSeas(String place, double lat, double lng) {
        assertThat(countryBoundaryRepository.countryCodeAt(lat, lng)).isNull();
    }
}
