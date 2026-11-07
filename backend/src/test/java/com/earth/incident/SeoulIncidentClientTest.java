package com.earth.incident;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 응답(2026-11-07 샘플키) 모양 그대로 읽는다. */
class SeoulIncidentClientTest {

    private static final String SAMPLE = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?><AccInfo><list_total_count>2</list_total_count>\
            <RESULT><CODE>INFO-000</CODE><MESSAGE>정상 처리되었습니다</MESSAGE></RESULT>\
            <row><acc_id>1108981</acc_id><occr_date>20261107</occr_date><occr_time>0500</occr_time>\
            <exp_clr_date>20261107</exp_clr_date><exp_clr_time>200000</exp_clr_time><acc_type>A10</acc_type>\
            <acc_dtype>10B01</acc_dtype><link_id>1000003000</link_id><grs80tm_x>198912.138883</grs80tm_x>\
            <grs80tm_y>452082.387013</grs80tm_y><acc_info>[행사/전면통제]청계관광특구2026 육의전 축제&#13;통제구간: 청계북로</acc_info>\
            <acc_road_code>009</acc_road_code></row>\
            <row><acc_id>1109093</acc_id><occr_date>20261107</occr_date><occr_time>140000</occr_time>\
            <exp_clr_date>20261107</exp_clr_date><exp_clr_time>180000</exp_clr_time><acc_type>A04</acc_type>\
            <acc_dtype>04B01</acc_dtype><link_id>1050008200</link_id><grs80tm_x>203479.350316</grs80tm_x>\
            <grs80tm_y>452689.940457</grs80tm_y><acc_info> 천호대로 1차로 시설물보수</acc_info><acc_road_code>010</acc_road_code></row>\
            </AccInfo>""";

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("총 건수와 행을 읽고, 시각은 4자리(HHmm)와 6자리(HHmmss)를 모두 받는다")
    void parsesRealShape() {
        SeoulIncidentClient.Page page = SeoulIncidentClient.parse(bytes(SAMPLE));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.rows()).isEqualTo(2);
        SeoulIncident festival = page.incidents().getFirst();
        assertThat(festival.accId()).isEqualTo("1108981");
        assertThat(festival.detailType()).isEqualTo("10B01");
        assertThat(festival.occurredAt()).isEqualTo(Instant.parse("2026-11-06T20:00:00Z"));      // 05:00 KST
        assertThat(festival.expectedClearAt()).isEqualTo(Instant.parse("2026-11-07T11:00:00Z")); // 20:00:00 KST
        assertThat(festival.info()).contains("\r");
        assertThat(page.incidents().get(1).occurredAt()).isEqualTo(Instant.parse("2026-11-07T05:00:00Z"));
    }

    @Test
    @DisplayName("INFO-200(진행 중인 돌발 없음)은 빈 목록, 그 밖의 코드는 오류")
    void resultCodes() {
        SeoulIncidentClient.Page empty = SeoulIncidentClient.parse(bytes(
                "<RESULT><CODE>INFO-200</CODE><MESSAGE>해당하는 데이터가 없습니다.</MESSAGE></RESULT>"));
        assertThat(empty.total()).isZero();
        assertThat(empty.incidents()).isEmpty();

        assertThatThrownBy(() -> SeoulIncidentClient.parse(bytes(
                "<RESULT><CODE>INFO-100</CODE><MESSAGE>인증키가 유효하지 않습니다.</MESSAGE></RESULT>")))
                .hasMessageContaining("INFO-100");
    }

    @Test
    @DisplayName("깨진 시각은 null로 두고 행은 살린다")
    void malformedTime() {
        assertThat(SeoulIncidentClient.dateTime("20261107", "99")).isNull();
        assertThat(SeoulIncidentClient.dateTime("", "1000")).isNull();
    }

    @Test
    @DisplayName("DOCTYPE이 든 응답은 읽지 않는다(외부 엔티티로 서버 파일을 읽는 XXE 차단)")
    void rejectsDoctype() {
        String xxe = """
                <?xml version="1.0"?><!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]>\
                <AccInfo><list_total_count>1</list_total_count><RESULT><CODE>INFO-000</CODE></RESULT>\
                <row><acc_info>&x;</acc_info></row></AccInfo>""";
        assertThatThrownBy(() -> SeoulIncidentClient.parse(bytes(xxe))).isInstanceOf(IllegalStateException.class);
    }
}
