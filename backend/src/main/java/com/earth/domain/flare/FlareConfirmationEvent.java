package com.earth.domain.flare;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 사실 확인 이력 한 줄(V14). {@link FlareConfirmation}은 지금 상태만 담아 누가 언제 무엇을 눌렀다 바꿨는지가 남지 않는다.
 * 경험치의 근거가 되는 행동이라 덧붙이기만 하는 이력을 따로 둔다.
 */
@Entity
@Table(name = "flare_confirmation_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlareConfirmationEvent {

    public enum Action { ADD, CHANGE, WITHDRAW }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flare_id", nullable = false)
    private Long flareId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_kind", length = 10)
    private ConfirmationKind fromKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_kind", length = 10)
    private ConfirmationKind toKind;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 이전 종류와 새 종류로 행동을 정한다. 이전이 없으면 ADD, 새 것이 없으면 WITHDRAW, 둘 다 있으면 CHANGE. */
    public static FlareConfirmationEvent of(Long flareId, Long userId, ConfirmationKind from, ConfirmationKind to) {
        FlareConfirmationEvent event = new FlareConfirmationEvent();
        event.flareId = flareId;
        event.userId = userId;
        event.action = from == null ? Action.ADD : to == null ? Action.WITHDRAW : Action.CHANGE;
        event.fromKind = from;
        event.toKind = to;
        event.createdAt = Instant.now();
        return event;
    }
}
