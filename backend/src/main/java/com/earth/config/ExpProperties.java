package com.earth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 경험치 정산 규칙(D-21). 플레어가 끝나면 사실 확인 수로 작성자의 경험치를 한 번 정산한다.
 *
 * <ul>
 *   <li>더하기: "나도 봤어요" {@code seenPerStep}명마다 {@code pointsPerStep}점, 플레어 하나에 최대 {@code maxStepsPerFlare}단계.
 *       1명당 1점처럼 단순하면 점수를 쉽게 짐작하고 맞춰 올리기 쉬워 계단식으로 준다</li>
 *   <li>빼기: "사실과 달라요"가 {@code disputedMin}명 이상이고 "나도 봤어요"보다 많을 때만, {@code disputedPerStep}명마다
 *       {@code penaltyPerStep}점. 한두 명이 몰려 눌러 멀쩡한 작성자를 깎지 못하게 한다. 이때는 더하기를 주지 않는다</li>
 *   <li>"끝났어요"는 경험치에 영향이 없다. 상황이 끝난 것은 작성자의 잘못이 아니다</li>
 * </ul>
 *
 * @param settlementEnabled    끄면 정산 스케줄러가 돌지 않는다(테스트는 서비스를 직접 부른다)
 * @param settlementIntervalMs 정산 주기
 */
@ConfigurationProperties(prefix = "earth.exp")
public record ExpProperties(
        @DefaultValue("true") boolean settlementEnabled,
        @DefaultValue("60000") long settlementIntervalMs,
        @DefaultValue("3") int seenPerStep,
        @DefaultValue("5") int pointsPerStep,
        @DefaultValue("10") int maxStepsPerFlare,
        @DefaultValue("3") int disputedMin,
        @DefaultValue("3") int disputedPerStep,
        @DefaultValue("5") int penaltyPerStep) {

    public ExpProperties {
        if (seenPerStep < 1 || disputedPerStep < 1 || disputedMin < 1) {
            throw new IllegalArgumentException("earth.exp의 인원 기준(seen-per-step, disputed-per-step, disputed-min)은 1 이상이어야 합니다.");
        }
    }
}
