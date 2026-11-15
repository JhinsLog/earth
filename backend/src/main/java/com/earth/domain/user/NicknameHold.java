package com.earth.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 사용자가 버린 닉네임. {@link Nicknames#CHANGE_COOLDOWN} 동안 다른 사람이 가져가지 못한다.
 * 버리자마자 남이 가져가면 대화 상대는 그 사람을 원래 주인으로 착각한다(사칭, D-17).
 *
 * <p>저장은 {@link NicknameHoldRepository#hold}로만 한다. 같은 키를 다시 버리면 행을 덮어쓴다.
 */
@Entity
@Table(name = "nickname_holds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NicknameHold {

    @Id
    @Column(name = "nickname_key")
    private String nicknameKey;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "released_at", nullable = false)
    private Instant releasedAt;
}
