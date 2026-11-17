package com.earth.domain.flare;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 검열 조치 이력(V16). 덧붙이기만 한다. actorId가 null이면 시스템(신고 누적에 따른 자동 가림)이다. */
@Entity
@Table(name = "moderation_actions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModerationAction {

    public enum Kind { AUTO_HIDE, HIDE, RESTORE, REMOVE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flare_id", nullable = false)
    private Long flareId;

    @Column(name = "actor_id")
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind action;

    @Column(length = 200)
    private String note;

    @Column(name = "report_count", nullable = false)
    private int reportCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ModerationAction(Long flareId, Long actorId, Kind action, String note, int reportCount, Instant createdAt) {
        this.flareId = flareId;
        this.actorId = actorId;
        this.action = action;
        this.note = note;
        this.reportCount = reportCount;
        this.createdAt = createdAt;
    }
}
