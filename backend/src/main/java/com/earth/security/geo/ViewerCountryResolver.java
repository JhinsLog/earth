package com.earth.security.geo;

import com.earth.config.GeoProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 요청 헤더에서 접속 국가를 읽는다. 비로그인 사용자에게 어느 나라 별의 상세를 보여줄지 정하는 데 쓴다.
 *
 * <p>Cloudflare는 판정하지 못하면 {@code XX}, Tor 출구 노드면 {@code T1}을 준다. 둘 다 실제 국가가 아니므로
 * "국가 없음"으로 본다. 국가가 없으면 비로그인 사용자는 어느 나라 별의 상세도 볼 수 없다(안전한 쪽으로 닫힘).
 */
@Component
public class ViewerCountryResolver {

    private static final Pattern ALPHA2 = Pattern.compile("[A-Z]{2}");

    private final GeoProperties geoProperties;

    public ViewerCountryResolver(GeoProperties geoProperties) {
        this.geoProperties = geoProperties;
    }

    public String headerName() {
        return geoProperties.countryHeader();
    }

    public Optional<String> resolve(String headerValue) {
        if (headerValue == null) return Optional.empty();
        String code = headerValue.strip().toUpperCase(Locale.ROOT);
        if (!ALPHA2.matcher(code).matches() || code.equals("XX") || code.equals("T1")) {
            return Optional.empty();
        }
        return Optional.of(code);
    }
}
