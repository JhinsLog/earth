package com.earth.ski;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkiSeasonTest {

    private static SkiSeason read(String json) throws IOException {
        return SkiSeason.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("저장소의 season.json은 읽히고, 날짜가 있는 스키장은 모두 출처가 있다")
    void bundledFileIsValid() throws IOException {
        try (InputStream in = new ClassPathResource("ski/season.json").getInputStream()) {
            SkiSeason season = SkiSeason.read(in);
            assertThat(season.resorts()).isNotEmpty();
            assertThat(season.resorts()).filteredOn(r -> r.opening() != null)
                    .allSatisfy(r -> assertThat(r.source()).isNotNull());
        }
    }

    @Test
    @DisplayName("날짜와 출처를 읽는다")
    void readsDatesAndSource() throws IOException {
        SkiSeason season = read("""
                {"season":"2026-27","validUntil":"2027-04-30","resorts":[
                  {"id":"a","name":"가","latitude":37.5,"longitude":128.3,"opening":"2026-11-27","closing":null,
                   "dateStatus":"target","source":{"name":"공지","url":"https://example.com","checkedOn":"2026-11-20"}}]}""");
        SkiSeason.Resort resort = season.resorts().getFirst();
        assertThat(resort.opening()).isEqualTo(LocalDate.of(2026, 11, 27));
        assertThat(resort.closing()).isNull();
        assertThat(resort.confirmed()).isFalse();
        assertThat(resort.source().url()).isEqualTo("https://example.com");
    }

    @Test
    @DisplayName("날짜만 있고 출처가 없으면 읽기에서 실패한다")
    void dateWithoutSourceFails() {
        assertThatThrownBy(() -> read("""
                {"season":"2026-27","validUntil":"2027-04-30","resorts":[
                  {"id":"a","name":"가","latitude":37.5,"longitude":128.3,"opening":"2026-11-27"}]}"""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("출처");
    }

    @Test
    @DisplayName("날짜가 있는데 확정·목표 구분이 없으면 실패한다")
    void dateWithoutStatusFails() {
        assertThatThrownBy(() -> read("""
                {"season":"2026-27","validUntil":"2027-04-30","resorts":[
                  {"id":"a","name":"가","latitude":37.5,"longitude":128.3,"opening":"2026-11-27",
                   "source":{"name":"공지","url":"https://example.com","checkedOn":"2026-11-20"}}]}"""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dateStatus");
    }

    @Test
    @DisplayName("국내 범위 밖 좌표(위경도 뒤바뀜 등)와 겹치는 id는 실패한다")
    void rejectsBadCoordinatesAndDuplicateIds() {
        assertThatThrownBy(() -> read("""
                {"season":"2026-27","validUntil":"2027-04-30","resorts":[
                  {"id":"a","name":"가","latitude":128.3,"longitude":37.5}]}"""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("국내 범위");
        assertThatThrownBy(() -> read("""
                {"season":"2026-27","validUntil":"2027-04-30","resorts":[
                  {"id":"a","name":"가","latitude":37.5,"longitude":128.3},
                  {"id":"a","name":"나","latitude":37.6,"longitude":128.4}]}"""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("겹칩니다");
    }
}
