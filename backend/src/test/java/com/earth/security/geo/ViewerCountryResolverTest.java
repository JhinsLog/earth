package com.earth.security.geo;

import com.earth.config.GeoProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ViewerCountryResolverTest {

    private final ViewerCountryResolver resolver = new ViewerCountryResolver(new GeoProperties("CF-IPCountry"));

    @Test
    @DisplayName("두 글자 국가 코드만 받고 대문자로 맞춘다")
    void acceptsAlpha2() {
        assertThat(resolver.resolve("KR")).contains("KR");
        assertThat(resolver.resolve(" kr ")).contains("KR");
    }

    @Test
    @DisplayName("Cloudflare의 판정 실패(XX)·Tor(T1)·없음·형식 오류는 국가 없음")
    void rejectsNonCountries() {
        assertThat(resolver.resolve("XX")).isEmpty();
        assertThat(resolver.resolve("T1")).isEmpty();
        assertThat(resolver.resolve(null)).isEmpty();
        assertThat(resolver.resolve("KOR")).isEmpty();
        assertThat(resolver.resolve("")).isEmpty();
    }
}
