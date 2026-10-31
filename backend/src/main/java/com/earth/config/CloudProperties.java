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
 * @param ringEnabled 정지궤도 위성 다섯 기를 10분마다 직접 합성한다. 끄면 GMGSI(1시간)만 쓴다.
 * @param gibsWmsUrl  NASA GIBS WMS(GOES-East·West, Himawari).
 * @param gibsDomainsUrl GIBS WMTS의 레이어별 관측 시각 목록(DescribeDomains) 주소 앞부분.
 * @param gibsColormapUrl GIBS 적외선 영상의 색상표. 색을 섭씨로 되돌리는 데 쓴다.
 * @param eumetviewWmsUrl EUMETSAT EUMETView WMS(Meteosat 0°, 인도양).
 */
@ConfigurationProperties(prefix = "earth.clouds")
public record CloudProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("https://noaa-gmgsi-pds.s3.amazonaws.com") String bucketUrl,
        @DefaultValue("GMGSI_LW") String product,
        @DefaultValue("2048") int width,
        @DefaultValue("true") boolean ringEnabled,
        @DefaultValue("https://gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi") String gibsWmsUrl,
        @DefaultValue("https://gibs.earthdata.nasa.gov/wmts/epsg4326/best/1.0.0") String gibsDomainsUrl,
        @DefaultValue("https://gibs.earthdata.nasa.gov/colormaps/v1.3/Clean_Longwave_Infrared_Window_Band.xml")
        String gibsColormapUrl,
        @DefaultValue("https://view.eumetsat.int/geoserver/ows") String eumetviewWmsUrl) {
}
