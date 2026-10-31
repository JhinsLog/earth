package com.earth.clouds;

/**
 * 지구를 한 바퀴 두르는 정지궤도 기상위성 다섯 기. 경도마다 가장 정면에서 보는 위성의 영상을 쓴다.
 *
 * <p>영상은 원본 수치 자료가 아니라 각 기관이 웹 지도 서비스(WMS)로 공개하는 적외선(약 10.4 µm) 단일 채널
 * 영상이다. 원본 파일보다 작고, 서버가 위경도 격자(EPSG:4326)로 재투영해 준다. 출처와 라이선스는
 * {@code docs/data-sources.md}.
 *
 * @param subLongitude 위성 직하점 경도. 화면의 각 지점을 얼마나 비스듬히 보는지 계산하는 데 쓴다.
 * @param cadenceMinutes 관측 간격. 최신 영상을 이 간격의 시각들로 거슬러 찾는다.
 */
public enum GeoSatellite {
    GOES_WEST("GOES-West", -137.2, Provider.GIBS, "GOES-West_ABI_Band13_Clean_Infrared", "", 10),
    GOES_EAST("GOES-East", -75.2, Provider.GIBS, "GOES-East_ABI_Band13_Clean_Infrared", "", 10),
    MTG("Meteosat-12", 0.0, Provider.EUMETVIEW, "mtg_fd:ir105_hrfi", "mtg_fd:mtg_fd_ir105_hrfi_grayscale", 10),
    IODC("Meteosat IODC", 45.5, Provider.EUMETVIEW, "msg_iodc:ir108", "", 15),
    HIMAWARI("Himawari", 140.7, Provider.GIBS, "Himawari_AHI_Band13_Clean_Infrared", "", 10);

    /**
     * NASA GIBS는 온도 구간마다 색을 칠한 영상을 주고 색상표가 공개돼 있어 섭씨로 되돌릴 수 있다.
     * EUMETSAT EUMETView는 회색 밝기(차가울수록 밝음)만 주고 밝기와 온도의 대응을 공개하지 않아,
     * 겹치는 지역에서 GIBS 영상과 맞춰 환산한다({@link CloudComposer}).
     */
    public enum Provider { GIBS, EUMETVIEW }

    private final String label;
    private final double subLongitude;
    private final Provider provider;
    private final String layer;
    private final String style;
    private final int cadenceMinutes;

    GeoSatellite(String label, double subLongitude, Provider provider, String layer, String style, int cadenceMinutes) {
        this.label = label;
        this.subLongitude = subLongitude;
        this.provider = provider;
        this.layer = layer;
        this.style = style;
        this.cadenceMinutes = cadenceMinutes;
    }

    public String label() {
        return label;
    }

    public double subLongitude() {
        return subLongitude;
    }

    public Provider provider() {
        return provider;
    }

    public String layer() {
        return layer;
    }

    public String style() {
        return style;
    }

    public int cadenceMinutes() {
        return cadenceMinutes;
    }
}
