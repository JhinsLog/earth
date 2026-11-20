package com.earth.domain.flare;

public enum FlareCategory {
    FIRE, ACCIDENT, WEATHER, CROWD,
    /** 레저·행사. 스키장 개장(외부 데이터), 앞으로 축제 등. */
    LEISURE,
    /**
     * 화산 분화. 공식 기관(Smithsonian·USGS 주간 보고) 출처만 쓴다. 자연현상은 공식 발표만 다루므로 사용자가 고를 수 없다
     * (userSelectable). 예전에는 ETC였는데, 기타의 노란색이 화산과 어울리지 않고 '기타'라는 표시도 정보가 없었다(D-25).
     */
    VOLCANO,
    ETC;

    /** 사용자가 직접 남길 수 있는 분류인가. 공식 출처 전용 분류는 등록·수정 요청에서 거부한다. */
    public boolean userSelectable() {
        return this != VOLCANO;
    }
}
