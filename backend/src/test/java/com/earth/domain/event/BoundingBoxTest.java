package com.earth.domain.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 화면 범위 정규화. 계산만 하므로 DB 없이 검증한다.
 * 실제 쿼리가 두 구간을 올바르게 쓰는지는 {@code EventViewportQueryTest}가 본다.
 */
class BoundingBoxTest {

    @Test
    @DisplayName("날짜변경선을 넘지 않으면 두 구간이 같다")
    void ordinaryRange() {
        BoundingBox box = BoundingBox.of(33, 39, 124, 132);

        assertThat(box).isEqualTo(new BoundingBox(33, 39, 124, 132, 124, 132));
    }

    @Test
    @DisplayName("MapLibre가 감지 않은 경도(170→190)를 보내면 두 구간으로 나눈다")
    void unwrappedCrossingIsSplit() {
        BoundingBox box = BoundingBox.of(-20, 0, 170, 190);

        assertThat(box).isEqualTo(new BoundingBox(-20, 0, 170, 180, -180, -170));
    }

    @Test
    @DisplayName("이미 감긴 경도(170→-170)도 같은 두 구간이 된다")
    void wrappedCrossingIsSplit() {
        BoundingBox box = BoundingBox.of(-20, 0, 170, -170);

        assertThat(box).isEqualTo(new BoundingBox(-20, 0, 170, 180, -180, -170));
    }

    @Test
    @DisplayName("서쪽으로 한 바퀴 넘어간 값(-190→-170)도 같은 두 구간이 된다")
    void negativeUnwrappedCrossingIsSplit() {
        BoundingBox box = BoundingBox.of(-20, 0, -190, -170);

        assertThat(box).isEqualTo(new BoundingBox(-20, 0, 170, 180, -180, -170));
    }

    @Test
    @DisplayName("한 바퀴 이상 보이면 경도 전체")
    void fullTurnCoversAllLongitudes() {
        BoundingBox box = BoundingBox.of(-10, 10, -200, 200);

        assertThat(box).isEqualTo(new BoundingBox(-10, 10, -180, 180, -180, 180));
    }

    @Test
    @DisplayName("극이 보여 위도가 ±90을 넘으면 ±90으로 자른다")
    void latitudeIsClamped() {
        BoundingBox box = BoundingBox.of(60, 95, 0, 10);

        assertThat(box.southLat()).isEqualTo(60);
        assertThat(box.northLat()).isEqualTo(90);
    }

    @Test
    @DisplayName("남쪽이 북쪽보다 크거나 숫자가 아니면 거부한다")
    void invalidInputIsRejected() {
        assertThatThrownBy(() -> BoundingBox.of(40, 30, 0, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BoundingBox.of(Double.NaN, 30, 0, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BoundingBox.of(0, 30, 0, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
