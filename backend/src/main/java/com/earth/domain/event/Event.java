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

    public boolean isAuthor(User user) {
        return user != null && this.author.getId().equals(user.getId());
    }

    /** 지구본에 아직 보여야 하는지. 상태와 만료 시각을 함께 본다. */
    public boolean isVisible() {
        return status.isVisible() && expiresAt.isAfter(Instant.now());
    }
}
