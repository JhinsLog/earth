package com.earth.domain.event;

/**
 * 별을 만든 주체. 상세 화면이 "누가 남긴 별인지"를 보여 주는 데 쓴다.
 *
 * <p>작성자(author)만으로는 구분하지 않는다. 외부 데이터로 만든 별도 작성자 칸에는 시스템 계정이 들어가므로,
 * 작성자가 사람인지 시스템인지를 화면이 계정 정보로 추측하게 두지 않고 별 자체에 적어 둔다.
 */
public enum EventOrigin {
    /** 사용자가 직접 남긴 별. */
    USER,
    /** 공공데이터 등 외부 API에서 받아 만든 별. 어디서 왔는지는 {@code Event.source}. */
    API
}
