package com.earth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 홈 지구본의 실시간 구름. 출처와 갱신 방식은 {@code docs/data-sources.md}.
 *
 * @param enabled   끄면 수집하지 않고, 프론트는 내장된 정적 구름 사진을 쓴다(테스트에서 끈다).
 * @param bucketUrl NOAA GMGSI 공개 버킷. 인증 없이 읽는다.
 * @param product   GMGSI 제품. LW(장파 적외선)는 밤에도 구름이 보여 지구 전체를 한 장으로 덮는다.
 * @param width     지구본용 이미지 가로 픽셀. 세로는 절반(등장방형).
 */
@ConfigurationProperties(prefix = "earth.clouds")
public record CloudProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("https://noaa-gmgsi-pds.s3.amazonaws.com") String bucketUrl,
        @DefaultValue("GMGSI_LW") String product,
        @DefaultValue("2048") int width) {
}
