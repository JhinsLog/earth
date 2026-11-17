package com.earth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Smithsonian·USGS 주간 화산 활동 보고(Weekly Volcanic Activity Report)를 공공 정보 플레어로 만드는 수집기. 인증키가 필요 없다.
 *
 * @param enabled           끄면 수집하지 않는다(테스트에서 끈다)
 * @param feedUrl           주간 보고 RSS 주소
 * @param refreshIntervalMs 수집 주기. 보고는 주 1회(목요일)라 6시간이면 충분하다
 * @param maxReportAgeDays  보고가 발행된 뒤 이 날수가 지나면 내린다. 보고가 끊겨도 오래된 "분화 중"이 남지 않게 한다
 */
@ConfigurationProperties(prefix = "earth.volcanoes")
public record VolcanoProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("https://volcano.si.edu/news/WeeklyVolcanoRSS.xml") String feedUrl,
        @DefaultValue("21600000") long refreshIntervalMs,
        @DefaultValue("14") int maxReportAgeDays) {
}
