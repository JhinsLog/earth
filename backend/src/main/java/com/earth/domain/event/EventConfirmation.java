package com.earth.domain.event;

import com.earth.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * "나도 봤다" — 같은 사건을 목격한 사람이 별에 남기는 확인.
 *
 * <p>(event, user) 유니크 제약이 있어 한 사람은 한 번만 공감할 수 있다. 이게 없으면
 * 한 사람이 반복 공감으로 별의 수명을 무한히 늘릴 수 있다.
 *
 * <p>{@code grantedSeconds}에 이 공감이 <b>실제로</b> 늘려준 수명을 담는다. 취소할 때 정확히
 * 그만큼만 되돌리기 위해서다. 5분을 그냥 빼면 안 되는데, 상한에 걸린 상태에서 공감하면 실제
 * 연장이 5분보다 적거나 0이기 때문이다. 그때 5분을 빼면 자기가 준 것보다 많이 회수한다.
 */
@Entity
@Table(name = "event_confirmations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventConfirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 이 공감이 실제로 늘려준 수명(초). 취소 시 이만큼만 되돌린다. */
    @Column(name = "granted_seconds", nullable = false)
    private long grantedSeconds;

    public EventConfirmation(Event event, User user, Duration granted) {
        this.event = event;
        this.user = user;
        this.createdAt = Instant.now();
        this.grantedSeconds = granted.toSeconds();
    }

    public Duration granted() {
        return Duration.ofSeconds(this.grantedSeconds);
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
