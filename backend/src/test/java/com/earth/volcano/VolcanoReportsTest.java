package com.earth.volcano;

import com.earth.domain.flare.FlareCategory;
import com.earth.service.ExternalFlareSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VolcanoReportsTest {

    private static final Duration MAX_AGE = Duration.ofDays(14);
    // 2026-10-15T07:11:08Z 발행. 실제 피드(2026-10-15)의 모양을 줄여 옮겼다.
    private static final Instant PUBLISHED = Instant.parse("2026-10-15T07:11:08Z");

    private static final String FEED = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <rss version="2.0" xmlns:georss="http://www.georss.org/georss">
            <channel>
            <title>Smithsonian / USGS Weekly Volcanic Activity Report</title>
            <item>
            <title>Krakatau (Indonesia) - Report for 8 October-14 October 2026 - New Eruptive Activity</title>
            <description>&lt;p&gt;The alert level remained at 2 (on a scale of 1-4)...&lt;/p&gt;</description>
            <guid isPermaLink="true">https://volcano.si.edu/reports_weekly.cfm#vn_262000</guid>
            <pubDate>Thu, 15 Oct 2026 03:11:08 -0400</pubDate>
            <georss:point>-6.1009 105.4233</georss:point>
            </item>
            <item>
            <title>Etna (Italy) - Report for 8 October-14 October 2026 - Continuing Eruptive Activity</title>
            <guid isPermaLink="true">https://volcano.si.edu/reports_weekly.cfm#vn_211060</guid>
            <pubDate>Thu, 15 Oct 2026 03:11:08 -0400</pubDate>
            <georss:point>37.748 14.999</georss:point>
            </item>
            <item>
            <title>Somewhere (Chile) - Report for 8 October-14 October 2026 - New Unrest</title>
            <guid isPermaLink="true">https://volcano.si.edu/reports_weekly.cfm#vn_999999</guid>
            <pubDate>Thu, 15 Oct 2026 03:11:08 -0400</pubDate>
            <georss:point>-35.0 -70.0</georss:point>
            </item>
            <item>
            <title>Aso (Japan) - Report for 8 October-14 October 2026 - Continuing Eruptive Activity</title>
            <guid isPermaLink="true">https://volcano.si.edu/reports_weekly.cfm#vn_282110</guid>
            <pubDate>Thu, 15 Oct 2026 03:11:08 -0400</pubDate>
            <georss:point>32.884 131.104</georss:point>
            </item>
            </channel>
            </rss>
            """;

    private static List<VolcanoReport> parse(String xml) {
        return VolcanoReportClient.parse(xml.strip().getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    @DisplayName("분화 항목만 고르고, 이름·나라·좌표·새 분화 여부·발행 시각을 읽는다")
    void parsesEruptionsOnly() {
        List<VolcanoReport> reports = parse(FEED);

        assertThat(reports).extracting(VolcanoReport::name).containsExactly("Krakatau", "Etna", "Aso");
        VolcanoReport krakatau = reports.getFirst();
        assertThat(krakatau.volcanoNumber()).isEqualTo("262000");
        assertThat(krakatau.country()).isEqualTo("Indonesia");
        assertThat(krakatau.newActivity()).isTrue();
        assertThat(krakatau.latitude()).isEqualTo(-6.1009);
        assertThat(krakatau.longitude()).isEqualTo(105.4233);
        assertThat(krakatau.publishedAt()).isEqualTo(PUBLISHED);
        assertThat(reports.get(1).newActivity()).isFalse();
    }

    @Test
    @DisplayName("한반도 인근(기상청 우선 구역) 화산은 넣지 않고, 본문에는 경보 문구 없이 사실과 출처만 담는다")
    void skipsNearKoreaAndKeepsOnlyFacts() {
        List<ExternalFlareSyncService.Draft> drafts =
                VolcanoReports.toDrafts(parse(FEED), PUBLISHED.plus(Duration.ofDays(1)), MAX_AGE);

        assertThat(drafts).extracting(ExternalFlareSyncService.Draft::externalId).containsExactly("262000", "211060");
        ExternalFlareSyncService.Draft krakatau = drafts.getFirst();
        assertThat(krakatau.title()).isEqualTo("화산 분화 · Krakatau (Indonesia)");
        assertThat(krakatau.content()).isEqualTo("새 분화 활동\n주간 보고 2026-10-15 발행\n\n출처: Smithsonian·USGS 주간 화산 활동 보고");
        assertThat(krakatau.content()).doesNotContainIgnoringCase("alert");
        assertThat(krakatau.category()).isEqualTo(FlareCategory.VOLCANO);
        assertThat(krakatau.expectedEnd()).isEqualTo(PUBLISHED.plus(MAX_AGE));
    }

    @Test
    @DisplayName("발행 뒤 보관 기간이 지난 보고는 넣지 않는다 — 보고가 끊겨도 오래된 '분화 중'이 남지 않는다")
    void dropsStaleReports() {
        assertThat(VolcanoReports.toDrafts(parse(FEED), PUBLISHED.plus(MAX_AGE), MAX_AGE)).isEmpty();
        assertThat(VolcanoReports.toDrafts(parse(FEED), PUBLISHED.plus(MAX_AGE).minusSeconds(1), MAX_AGE)).hasSize(2);
    }

    @Test
    @DisplayName("DOCTYPE이 있는 응답은 읽지 않는다(외부 엔티티 공격 차단)")
    void rejectsDoctype() {
        String evil = """
                <?xml version="1.0"?>
                <!DOCTYPE rss [<!ENTITY x SYSTEM "file:///etc/passwd">]>
                <rss><channel><item><title>&x;</title></item></channel></rss>
                """;
        assertThatThrownBy(() -> parse(evil)).isInstanceOf(IllegalStateException.class);
    }
}
