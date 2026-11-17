package com.earth.exp;

import com.earth.config.ExpProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExpRuleTest {

    // 운영 기본값과 같다: 봤어요 3명마다 5점(최대 10단계), 사실과 달라요 3명 이상·봤어요보다 많을 때 3명마다 -5점.
    private final ExpRule rule = new ExpRule(new ExpProperties(true, 60_000, 3, 5, 10, 3, 3, 5));

    @Test
    @DisplayName("나도 봤어요는 3명마다 5점 — 1~2명은 0점")
    void seenSteps() {
        assertThat(rule.delta(0, 0)).isZero();
        assertThat(rule.delta(2, 0)).isZero();
        assertThat(rule.delta(3, 0)).isEqualTo(5);
        assertThat(rule.delta(8, 0)).isEqualTo(10);
    }

    @Test
    @DisplayName("한 플레어에서 얻는 점수는 10단계(50점)까지")
    void capped() {
        assertThat(rule.delta(30, 0)).isEqualTo(50);
        assertThat(rule.delta(300, 0)).isEqualTo(50);
    }

    @Test
    @DisplayName("사실과 달라요는 3명 이상이고 봤어요보다 많을 때만 감점, 그때는 더하지 않는다")
    void disputes() {
        assertThat(rule.delta(0, 2)).as("2명으로는 깎지 못한다").isZero();
        assertThat(rule.delta(6, 4)).as("봤어요가 더 많으면 감점 없이 봤어요로 정산").isEqualTo(10);
        assertThat(rule.delta(4, 4)).as("같으면 다수가 아니다").isEqualTo(5);
        assertThat(rule.delta(1, 3)).isEqualTo(-5);
        assertThat(rule.delta(3, 7)).isEqualTo(-10);
    }
}
