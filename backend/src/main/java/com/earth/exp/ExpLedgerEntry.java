package com.earth.exp;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 경험치 원장 한 줄(V14). 덧붙이기만 하고 고치지 않는다. */
@Entity
@Table(name = "exp_ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpLedgerEntry {

    /** 플레어 정산. 이유가 늘어나면(예: 운영자 조정) 여기에 더한다. */
    public static final String FLARE_SETTLED = "FLARE_SETTLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "flare_id")
    private Long flareId;

    @Column(nullable = false, length = 30)
    private String reason;

    @Column(nullable = false)
    private int delta;

    @Column(name = "exp_before", nullable = false)
    private int expBefore;

    @Column(name = "exp_after", nullable = false)
    private int expAfter;

    @Column(name = "seen_count", nullable = false)
    private int seenCount;

    @Column(name = "ended_count", nullable = false)
    private int endedCount;

    @Column(name = "disputed_count", nullable = false)
    private int disputedCount;

    @Column(nullable = false, length = 200)
    private String rule;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ExpLedgerEntry(Long userId, Long flareId, String reason, int delta, int expBefore, int expAfter,
                          int seenCount, int endedCount, int disputedCount, String rule, Instant createdAt) {
        this.userId = userId;
        this.flareId = flareId;
        this.reason = reason;
        this.delta = delta;
        this.expBefore = expBefore;
        this.expAfter = expAfter;
        this.seenCount = seenCount;
        this.endedCount = endedCount;
        this.disputedCount = disputedCount;
        this.rule = rule;
        this.createdAt = createdAt;
    }
}
