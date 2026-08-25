package com.earth.domain.event;

public enum EventStatus {
    /** 지구본에 표시되는 정상 상태. */
    ACTIVE,
    /** 작성자가 종료한 상태(현재 미사용, 향후 수동 종료용). */
    CLOSED,
    /** 생성 후 TTL이 지나 자동으로 사라진 상태. */
    EXPIRED,
    /** 작성자가 삭제한 상태. 채팅 이력이 events를 FK로 참조하므로 물리 삭제 대신 소프트 삭제한다. */
    DELETED;

    /** 지구본과 목록에 노출되어야 하는 상태인지. */
    public boolean isVisible() {
        return this == ACTIVE;
    }
}
