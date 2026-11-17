package com.earth.quake;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 공공데이터포털 표준 응답 모양(response.header / response.body.items.item)과 문서의 필드 예시로 읽는다.
 * 숫자 항목이 숫자로도 문자열로도 오는 경우를 함께 넣었다.
 */
class KmaQuakeClientTest {

    private static final String SAMPLE = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
            "body":{"dataType":"JSON","items":{"item":[
            {"cnt":1,"fcTp":3,"img":"http://www.weather.go.kr/repositary/image/eqk/img/eqk_img_3_20171126031345.png",
             "inT":"최대진도 Ⅰ","lat":36.47,"loc":"전북 군산시 어청도 남서쪽 64km 해역","lon":124.83,"mt":"2.9",
             "rem":"지역에 따라 계기에 감지될 수 있음","stnId":"108","tmEqk":20171126031345,"tmFc":201711260317,
             "tmSeq":109,"dep":""},
            {"fcTp":"2","inT":"","lat":"-6.10","loc":"인도네시아 자와섬 남쪽 해역","lon":"105.20","mt":6.4,
             "rem":"우리나라에 영향 없음","tmEqk":"20171126010203","tmFc":"201711260130","dep":"40"},
            {"fcTp":3,"lat":"","lon":"","tmEqk":"20171126000000","tmFc":"201711260001"}
            ]},"pageNo":1,"numOfRows":10,"totalCount":3}}}""";

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("통보문을 읽고, 시각은 한국 시각, 숫자는 숫자·문자열 둘 다 받으며, 좌표 없는 항목은 건너뛴다")
    void parsesDocumentedShape() {
        KmaQuakeClient.Page page = KmaQuakeClient.parse(bytes(SAMPLE));

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.rows()).isEqualTo(3);
        assertThat(page.quakes()).hasSize(2);

        Quake domestic = page.quakes().getFirst();
        assertThat(domestic.occurredAt()).isEqualTo(Instant.parse("2017-11-25T18:13:45Z"));  // 03:13:45 KST
        assertThat(domestic.announcedAt()).isEqualTo(Instant.parse("2017-11-25T18:17:00Z"));
        assertThat(domestic.latitude()).isEqualTo(36.47);
        assertThat(domestic.longitude()).isEqualTo(124.83);
        assertThat(domestic.magnitude()).isEqualTo(2.9);
        assertThat(domestic.depthKm()).isNull();
        assertThat(domestic.intensity()).isEqualTo("최대진도 Ⅰ");
        assertThat(domestic.location()).isEqualTo("전북 군산시 어청도 남서쪽 64km 해역");
        assertThat(domestic.noticeType()).isEqualTo(3);

        Quake overseas = page.quakes().get(1);
        assertThat(overseas.latitude()).isEqualTo(-6.10);
        assertThat(overseas.magnitude()).isEqualTo(6.4);
        assertThat(overseas.depthKm()).isEqualTo(40.0);
        assertThat(overseas.noticeType()).isEqualTo(2);
    }

    @Test
    @DisplayName("항목이 하나면 배열이 아니라 객체로 와도 읽는다")
    void singleItemAsObject() {
        KmaQuakeClient.Page page = KmaQuakeClient.parse(bytes("""
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
                "body":{"items":{"item":{"fcTp":3,"lat":35.77,"lon":129.19,"mt":2.1,"tmEqk":20261116101500,
                "tmFc":202611161020,"loc":"경북 경주시","inT":"","rem":""}},"totalCount":1}}}"""));

        assertThat(page.quakes()).singleElement().extracting(Quake::latitude).isEqualTo(35.77);
    }

    @Test
    @DisplayName("NO_DATA(03)는 빈 목록, 그 밖의 코드와 게이트웨이의 XML 인증 오류는 예외")
    void resultCodes() {
        KmaQuakeClient.Page empty = KmaQuakeClient.parse(bytes(
                "{\"response\":{\"header\":{\"resultCode\":\"03\",\"resultMsg\":\"NO_DATA\"}}}"));
        assertThat(empty.quakes()).isEmpty();
        assertThat(empty.total()).isZero();

        assertThatThrownBy(() -> KmaQuakeClient.parse(bytes(
                "{\"response\":{\"header\":{\"resultCode\":\"10\",\"resultMsg\":\"INVALID_REQUEST_PARAMETER_ERROR\"}}}")))
                .hasMessageContaining("10").hasMessageContaining("INVALID_REQUEST_PARAMETER_ERROR");

        assertThatThrownBy(() -> KmaQuakeClient.parse(bytes("""
                <OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>\
                <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg><returnReasonCode>30</returnReasonCode>\
                </cmmMsgHeader></OpenAPI_ServiceResponse>""")))
                .hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
    }

    @Test
    @DisplayName("같은 지진의 통보 여러 개(조기경보 → 정밀 분석)는 가장 늦게 발표된 것만 남기고, 진앙시 최신순으로 돌려준다")
    void mergesNoticesOfSameQuake() {
        Instant t = Instant.parse("2026-11-16T01:15:00Z");
        Quake early = quake(t, t.plusSeconds(30), 35.80, 129.20, 3.4, 14);
        Quake refined = quake(t.plusSeconds(3), t.plusSeconds(300), 35.77, 129.19, 3.1, 3);
        Quake otherPlace = quake(t.plusSeconds(10), t.plusSeconds(400), 37.5, 124.6, 2.5, 3);   // 같은 시각대, 400km 떨어짐
        Quake later = quake(t.plusSeconds(3600), t.plusSeconds(3700), 35.77, 129.19, 2.0, 3);   // 같은 곳, 1시간 뒤

        List<Quake> merged = KmaQuakeClient.merge(List.of(early, otherPlace, refined, later));

        assertThat(merged).containsExactly(later, otherPlace, refined);
    }

    @Test
    @DisplayName("진앙시가 표시 기간(24시간) 안인 지진만 남긴다 — 날짜가 아니라 시각으로 자른다")
    void keepsOnlyRecent() {
        Instant now = Instant.parse("2026-11-17T03:00:00Z");
        Quake fresh = quake(now.minusSeconds(3600), now.minusSeconds(3000), 35.0, 129.0, 2.0, 3);
        Quake edge = quake(now.minusSeconds(24 * 3600 - 60), now.minusSeconds(24 * 3600 - 120), 36.0, 128.0, 2.1, 3);
        Quake old = quake(now.minusSeconds(24 * 3600 + 60), now.minusSeconds(24 * 3600), 37.0, 127.0, 2.2, 3);

        assertThat(KmaQuakeClient.recent(List.of(fresh, edge, old), now, java.time.Duration.ofHours(24)))
                .containsExactly(fresh, edge);
    }

    @Test
    @DisplayName("관측 정보 통보(2 국외, 3 국내)만 남기고 그 밖의 종류(조기경보 등)는 보여 주지 않는다 — 예보·특보를 하지 않는다")
    void observationsOnly() {
        Instant t = Instant.parse("2026-11-18T01:00:00Z");
        List<Quake> kept = KmaQuakeClient.observationsOnly(List.of(
                quake(t, t, 36.0, 129.0, 3.0, 3),
                quake(t, t, 37.3, 137.0, 5.1, 2),
                quake(t, t, 36.0, 129.0, 3.2, 11),
                quake(t, t, 36.0, 129.0, 3.1, 14)));
        assertThat(kept).extracting(Quake::noticeType).containsExactly(3, 2);
    }

    private static Quake quake(Instant occurredAt, Instant announcedAt, double lat, double lon, double mt, int type) {
        return new Quake("q-" + announcedAt.getEpochSecond(), occurredAt, announcedAt, lat, lon, mt, null, "", "", "", type, Quake.KMA);
    }
}
