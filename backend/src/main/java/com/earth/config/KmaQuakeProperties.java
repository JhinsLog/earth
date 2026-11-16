package com.earth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 기상청 지진정보 조회서비스(공공데이터포털 15000420)를 받아 지구본에 지진을 그리는 수집기.
 *
 * @param enabled           끄면 수집하지 않는다(테스트에서 끈다).
 * @param apiKey            공공데이터포털 일반 인증키(Decoding). <b>기본값을 두지 않는다.</b> 켜져 있는데 비어 있으면 기동을 막는다.
 *                          쿼리 문자열에 실려 가므로 요청 주소를 로그에 남기지 않는다(KmaQuakeClient).
 * @param baseUrl           API 주소. HTTPS를 쓴다.
 * @param refreshIntervalMs 수집 주기.
 * @param lookbackDays      발표 시각 기준으로 오늘 포함 며칠치를 받을지. 지구본에는 이 기간의 지진이 다 보인다.
 */
@ConfigurationProperties(prefix = "earth.quakes.kma")
public record KmaQuakeProperties(
        @DefaultValue("false") boolean enabled,
        String apiKey,
        @DefaultValue("https://apis.data.go.kr/1360000/EqkInfoService") String baseUrl,
        @DefaultValue("60000") long refreshIntervalMs,
        @DefaultValue("3") int lookbackDays) {
}
