package com.earth.domain.event;

import com.earth.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * "나도 봤다" — 같은 사건을 목격한 사람이 별에 남기는 확인.
 *
 * <p>(event, user) 유니크 제약이 있어 한 사람은 한 번만 공감할 수 있다. 이게 없으면
 * 한 사람이 반복 공감으로 별의 수명을 무한히 늘릴 수 있다.
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

    public EventConfirmation(Event event, User user) {
        this.event = event;
        this.user = user;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
