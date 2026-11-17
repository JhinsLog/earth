package com.earth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 파리시 「Que faire à Paris ?」(파리 오픈데이터, ODbL)를 플레어로 만드는 수집기. 인증키가 필요 없다.
 *
 * @param enabled           끄면 수집하지 않는다(테스트에서 끈다)
 * @param baseUrl           데이터셋 주소(Opendatasoft Explore API v2.1)
 * @param refreshIntervalMs 수집 주기. 원본은 하루 한 번 갱신되지만, 오늘 회차가 끝난 장소를 내리려면 자주 봐야 한다
 * @param maxVenues         한 번에 띄울 장소 수 상한. 파리 하나가 하루 180곳 넘게 나와 지구본(한 번에 최대 500개)을 덮지 않게 한다
 */
@ConfigurationProperties(prefix = "earth.events.paris")
public record ParisEventsProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("https://opendata.paris.fr/api/explore/v2.1/catalog/datasets/que-faire-a-paris-") String baseUrl,
        @DefaultValue("3600000") long refreshIntervalMs,
        @DefaultValue("60") int maxVenues) {
}
