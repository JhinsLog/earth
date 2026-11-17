package com.earth.exp;

import com.earth.config.ExpProperties;

/** 사실 확인 수 → 경험치 변화량. 규칙은 {@link ExpProperties}. 상태가 없어 단위 테스트로 검증한다. */
public final class ExpRule {

    private final ExpProperties properties;

    public ExpRule(ExpProperties properties) {
        this.properties = properties;
    }

    public int delta(int seen, int disputed) {
        if (disputed >= properties.disputedMin() && disputed > seen) {
            return -(disputed / properties.disputedPerStep()) * properties.penaltyPerStep();
        }
        return Math.min(seen / properties.seenPerStep(), properties.maxStepsPerFlare()) * properties.pointsPerStep();
    }

    /** 원장에 남길 규칙 요약. 규칙이 바뀐 뒤에도 당시 기준을 알 수 있게 한다. */
    public String describe() {
        return "seen/%d*%d(max %d steps); disputed>=%d&>seen: -disputed/%d*%d".formatted(
                properties.seenPerStep(), properties.pointsPerStep(), properties.maxStepsPerFlare(),
                properties.disputedMin(), properties.disputedPerStep(), properties.penaltyPerStep());
    }
}
