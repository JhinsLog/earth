package com.earth.domain.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * 한 사용자가 다른 사용자를 차단한 기록. 차단한 사람에게만 차단된 사람의 글이 보이지 않는다.
 *
 * <p>저장은 {@link UserBlockRepository#insertIgnoringDuplicate}로만 한다. 엔티티 저장(save)으로 하면
 * "있는지 보고 없으면 넣는다"가 두 단계라 동시 요청 둘이 모두 넣으려다 하나가 제약 위반으로 실패한다.
 */
@Entity
@Table(name = "user_blocks")
@IdClass(UserBlock.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlock {

    @Id
    @Column(name = "blocker_id")
    private Long blockerId;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id")
    private User blocked;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public record Key(Long blockerId, Long blocked) implements Serializable {
    }
}
