package com.earth.domain.event;

import com.earth.dto.EventResponse;

/**
 * 별의 상태가 바뀌었음을 알리는 도메인 이벤트.
 *
 * <p>등록·수정·삭제·만료·공감이 모두 이 하나로 표현된다. {@code /topic/events}가 상태 변경
 * 전부를 같은 형식으로 흘려보내고, 클라이언트는 {@code status}를 보고 지구본에서 걷어낼지
 * 판단하기 때문이다.
 *
 * <p><b>서비스는 이것만 발행하고 실제 전파는 커밋된 뒤에 일어난다.</b> 트랜잭션 안에서 Redis로
 * 직접 내보내면, 이후 단계가 실패해 롤백되었을 때 존재하지 않는 별이 전 접속자 화면에 뜬다.
 * pub/sub에는 롤백이 없어서 한번 나간 메시지를 되돌릴 수 없고, 사용자는 새로고침해야 그
 * 별이 사라진다. 발행 시점을 커밋 이후로 못박아 두면 이 상황이 구조적으로 생기지 않는다.
 *
 * @param snapshot 전파할 응답 형태. 커밋 후에는 엔티티가 영속성 컨텍스트에서 떨어지므로
 *                 그 시점에 다시 읽지 않아도 되게 미리 만들어 담는다.
 * @param created  새로 등록된 별이면 {@code true}. 구독 지역 알림은 이때만 만든다.
 */
public record EventChanged(EventResponse snapshot, boolean created) {

    public static EventChanged created(EventResponse snapshot) {
        return new EventChanged(snapshot, true);
    }

    /** 수정·삭제·만료·공감처럼 이미 있던 별이 바뀐 경우. */
    public static EventChanged updated(EventResponse snapshot) {
        return new EventChanged(snapshot, false);
    }
}
