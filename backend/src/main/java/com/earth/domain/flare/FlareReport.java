package com.earth.domain.flare;

import com.earth.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 플레어 신고 하나. (flare, reporter)는 유니크다(V16) — 한 사람은 한 번만 신고한다. */
@Entity
@Table(name = "flare_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlareReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flare_id", nullable = false)
    private Flare flare;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportReason reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public FlareReport(Flare flare, User reporter, ReportReason reason) {
        this.flare = flare;
        this.reporter = reporter;
        this.reason = reason;
        this.createdAt = Instant.now();
    }
}
