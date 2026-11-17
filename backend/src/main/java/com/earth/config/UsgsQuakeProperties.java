package com.earth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 미국 지질조사국(USGS) 지진 피드. 인증키가 필요 없고 미국 정부 공개 자료(퍼블릭 도메인, 출처 표기 요청)다.
 *
 * @param enabled           끄면 받지 않는다(테스트에서 끈다)
 * @param feedUrl           GeoJSON 요약 피드. 지난 하루 규모 4.5 이상(하루 10건 안팎) — 지구본의 지진 표시 기간(24시간)과 맞다
 * @param refreshIntervalMs 수집 주기. 피드는 1분마다 다시 만들어지지만 해외 지진은 몇 분 늦어도 되어 2분으로 둔다
 */
@ConfigurationProperties(prefix = "earth.quakes.usgs")
public record UsgsQuakeProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/4.5_day.geojson") String feedUrl,
        @DefaultValue("120000") long refreshIntervalMs) {
}
