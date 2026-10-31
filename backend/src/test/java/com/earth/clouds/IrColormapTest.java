package com.earth.clouds;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** GIBS 색상표 해석과, WMS 응답에서 시각을 읽는 부분. */
class IrColormapTest {

    // 실제 표(Clean_Longwave_Infrared_Window_Band.xml)의 항목 모양을 그대로 줄였다.
    private static String xml() {
        StringBuilder s = new StringBuilder("<ColorMaps><ColorMap><Entries>");
        s.append("<ColorMapEntry rgb=\"255,255,255\" transparent=\"false\" sourceValue=\"(-92.1,-91.1]\" value=\"(-92.1,-91.1]\" ref=\"0\"/>");
        for (int i = 0; i < 20; i++) {
            int gray = 200 - i * 10;
            double lo = -60 + i * 5;
            s.append("<ColorMapEntry rgb=\"").append(gray).append(',').append(gray).append(',').append(gray)
                    .append("\" transparent=\"false\" value=\"(").append(lo).append(',').append(lo + 5).append("]\"/>");
        }
        s.append("<ColorMapEntry rgb=\"1,1,1\" transparent=\"false\" sourceValue=\"(56.9,+INF)\" value=\"(56.9,+INF)\" ref=\"161\"/>");
        s.append("<ColorMapEntry rgb=\"0,0,0\" transparent=\"true\" nodata=\"true\" ref=\"162\"/>");
        return s.append("</Entries></ColorMap></ColorMaps>").toString();
    }

    @Test
    @DisplayName("색마다 구간 가운데 온도를 쓰고, 결측 항목은 빼며, 무한대 끝 구간은 유한한 경계값을 쓴다")
    void parsesIntervals() {
        IrColormap map = IrColormap.parse(xml());

        assertThat(map.size()).isEqualTo(22);
        assertThat(map.celsius(0xFFFFFF)).isCloseTo(-91.6f, within(0.01f));
        assertThat(map.celsius(0xC8C8C8)).isCloseTo(-57.5f, within(0.01f));
        assertThat(map.celsius(0x010101)).isCloseTo(56.9f, within(0.01f));
    }

    @Test
    @DisplayName("표에 없는 색(축소하며 섞인 색)은 가장 가까운 표 색의 온도로 본다")
    void mapsBlendedColorToNearest() {
        IrColormap map = IrColormap.parse(xml());

        // 200과 190 사이에서 200 쪽에 가까운 회색
        assertThat(map.celsius(0xC6C6C6)).isEqualTo(map.celsius(0xC8C8C8));
        assertThat(map.celsius(0xBFBFBF)).isEqualTo(map.celsius(0xBEBEBE));
    }

    @Test
    @DisplayName("GIBS 시각 목록의 구간을 시각들로 펼친다")
    void parsesDomain() {
        String xml = "<Domains><DimensionDomain><Domain>2026-10-31T03:00:00Z/2026-10-31T03:30:00Z/PT10M,"
                + "2026-10-31T04:10:00Z</Domain></DimensionDomain></Domains>";

        assertThat(GeoRingClient.parseDomain(xml)).containsExactly(
                Instant.parse("2026-10-31T03:00:00Z"), Instant.parse("2026-10-31T03:10:00Z"),
                Instant.parse("2026-10-31T03:20:00Z"), Instant.parse("2026-10-31T03:30:00Z"),
                Instant.parse("2026-10-31T04:10:00Z"));
        assertThat(GeoRingClient.parseDomain("<Error/>")).isEmpty();
    }

    @Test
    @DisplayName("EUMETView가 Warning 헤더로 알려 준 실제 관측 시각을 읽는다")
    void readsActualTimeFromWarning() {
        assertThat(GeoRingClient.actualTime("99 Nearest value used: time=2026-10-31T03:40:00.000Z ISO8601 (mtg_fd:ir105_hrfi)"))
                .contains(Instant.parse("2026-10-31T03:40:00Z"));
        assertThat(GeoRingClient.actualTime(null)).isEmpty();
    }
}
