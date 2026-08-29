package com.earth.domain.event;

import com.earth.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * 지구본 위에 별로 표시되는 이벤트.
 *
 * <p>생성 후 TTL이 지나면 자동으로 사라진다. 만료 시각을 컬럼으로 들고 있어 조회 시점에
 * 바로 걸러지므로, 스케줄러 주기와 무관하게 정확한 시각에 사라진다.
 */
@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(length = 1000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventCategory category;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status = EventStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * 이 별을 "나도 봤다"고 확인한 사람 수.
     *
     * <p>event_confirmations를 매번 세지 않도록 비정규화해 둔 값이다. 지구본은 별을 최대
     * 500개까지 한 번에 그리므로 목록 조회에서 별당 집계 쿼리가 나가면 안 된다.
     */
    @Column(name = "confirm_count", nullable = false)
    private int confirmCount = 0;

    public Event(User author, String title, String content, EventCategory category,
                 double latitude, double longitude, Duration ttl) {
        this.author = author;
        this.title = title;
        this.content = content;
        this.category = category;
        this.latitude = latitude;
        this.longitude = longitude;
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plus(ttl);
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    /** 수정해도 만료 시각은 연장하지 않는다. 수정으로 별을 무한히 유지할 수 없도록. */
    public void update(String title, String content, EventCategory category) {
        this.title = title;
        this.content = content;
        this.category = category;
        this.updatedAt = Instant.now();
    }

    public void markDeleted() {
        this.status = EventStatus.DELETED;
        this.updatedAt = Instant.now();
    }

    public void markExpired() {
        this.status = EventStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }

    /**
     * 공감 하나를 반영해 수명을 늘린다.
     *
     * <p>실제로 여러 사람이 목격한 사건은 계속 남고, 아무도 확인하지 않은 별은 원래 수명이
     * 지나면 사라진다. 가짜를 차단하는 대신 스스로 소멸하게 두는 방식이라 오판으로 정직한
     * 사용자를 벌하지 않는다.
     *
     * <p>연장은 현재 만료 시각이 아니라 <b>생성 시각</b>을 기준으로 상한을 건다. 만료 시각에
     * 계속 더하면 공감이 꾸준히 들어오는 별이 영원히 남을 수 있기 때문이다.
     */
    public void applyConfirmation(Duration extension, Duration maxLifetime) {
        this.confirmCount++;
        Instant extended = this.expiresAt.plus(extension);
        Instant hardLimit = this.createdAt.plus(maxLifetime);
        this.expiresAt = extended.isAfter(hardLimit) ? hardLimit : extended;
    }

    /** 공감을 취소한다. 이미 늘어난 수명은 되돌리지 않는다 — 되돌리면 취소로 남의 별을 죽일 수 있다. */
    public void withdrawConfirmation() {
        if (this.confirmCount > 0) {
            this.confirmCount--;
        }
    }

    public boolean isAuthor(User user) {
        return user != null && this.author.getId().equals(user.getId());
    }

    /** 지구본에 아직 보여야 하는지. 상태와 만료 시각을 함께 본다. */
    public boolean isVisible() {
        return status.isVisible() && expiresAt.isAfter(Instant.now());
    }
}
