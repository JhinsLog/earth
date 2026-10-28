package com.earth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 접속 국가 판정.
 *
 * @param countryHeader 접속자의 국가 코드(ISO 3166-1 alpha-2)가 담긴 요청 헤더. Cloudflare가 엣지에서
 *                      붙여 준다. 이 서버는 Cloudflare Tunnel 뒤에만 있어 클라이언트가 직접 닿을 수 없으므로
 *                      값을 위조할 경로가 없다. 터널 없이 직접 노출하면 누구나 이 헤더로 국가를 속일 수 있다.
 */
@ConfigurationProperties(prefix = "earth.geo")
public record GeoProperties(
        @DefaultValue("CF-IPCountry") String countryHeader) {
}
