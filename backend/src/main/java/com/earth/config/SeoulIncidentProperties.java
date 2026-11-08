package com.earth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 서울시 실시간 돌발정보(서울 열린데이터광장 OA-13315)를 플레어로 만드는 수집기.
 *
 * @param enabled           끄면 수집하지 않는다(테스트에서 끈다).
 * @param apiKey            열린데이터광장 인증키. <b>기본값을 두지 않는다.</b> 켜져 있는데 비어 있으면 기동을 막는다.
 * @param baseUrl           API 주소. 서울 열린데이터광장은 HTTPS를 제공하지 않는다(2026-11-07 8088·443 모두 확인).
 *                          그래서 인증키가 평문으로 오간다. 공개 데이터 읽기 전용 키라 새어도 피해는 호출 한도이고
 *                          재발급할 수 있어 받아들였다. 키가 로그에 남지 않게 주소를 찍지 않는다(SeoulIncidentClient).
 * @param refreshIntervalMs 수집 주기.
 * @param pageSize          한 번에 받을 건수. API 상한은 1,000. 공개 샘플키는 5건까지만 받아 주므로 로컬은 5로 둔다.
 */
@ConfigurationProperties(prefix = "earth.incidents.seoul")
public record SeoulIncidentProperties(
        @DefaultValue("false") boolean enabled,
        String apiKey,
        @DefaultValue("http://openapi.seoul.go.kr:8088") String baseUrl,
        @DefaultValue("600000") long refreshIntervalMs,
        @DefaultValue("1000") int pageSize) {
}
