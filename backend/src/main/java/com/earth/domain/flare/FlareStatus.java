package com.earth.domain.flare;

public enum FlareStatus {
    /** 라스에 표시되는 정상 상태. */
    ACTIVE,
    /** 작성자가 종료한 상태(현재 미사용, 향후 수동 종료용). */
    CLOSED,
    /** 생성 후 TTL이 지나 자동으로 사라진 상태. */
    EXPIRED,
    /** 작성자가 삭제한 상태. 채팅 이력이 flares를 FK로 참조하므로 물리 삭제 대신 소프트 삭제한다. */
    DELETED,
    /**
     * 임시 비공개(V16, D-24). 신고가 쌓였거나 운영자가 가렸다. 운영자가 복구(ACTIVE 또는 EXPIRED)하거나 삭제(REMOVED)할 때까지
     * 작성자 말고는 볼 수 없다. 경험치 정산도 결론이 날 때까지 미룬다.
     */
    HIDDEN,
    /** 운영자가 삭제한 상태. 작성자 삭제(DELETED)와 갈라 둔다 — 경험치 가점을 주지 않는다. */
    REMOVED;

    /** 라스와 목록에 노출되어야 하는 상태인지. */
    public boolean isVisible() {
        return this == ACTIVE;
    }
}
