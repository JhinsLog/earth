package com.earth.service;

import com.earth.config.ModerationProperties;
import com.earth.domain.flare.Flare;
import com.earth.domain.flare.FlareChanged;
import com.earth.domain.flare.FlareReport;
import com.earth.domain.flare.FlareReportRepository;
import com.earth.domain.flare.FlareRepository;
import com.earth.domain.flare.FlareStatus;
import com.earth.domain.flare.ModerationAction;
import com.earth.domain.flare.ModerationActionRepository;
import com.earth.domain.flare.ReportReason;
import com.earth.domain.user.User;
import com.earth.dto.FlareResponse;
import com.earth.dto.HiddenFlareResponse;
import com.earth.exception.EarthApiException;
import com.earth.exception.ErrorCode;
import com.earth.moderation.ModerationRecorded;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 사용자 플레어 신고와 검열 조치(D-24).
 *
 * <p><b>흐름.</b> 사용자가 신고 → 서로 다른 사람의 신고가 {@code hideThreshold}만큼 쌓이면 즉시 임시 비공개(HIDDEN) → 운영자가
 * 검토해 복구하거나 삭제(REMOVED)한다. 운영자는 신고 없이 바로 가리거나 지울 수도 있다. 사람을 특정하는 위치나 개인정보는
 * 운영자가 볼 때까지 떠 있으면 이미 늦으므로, 판단 전에 먼저 가리고 판단은 나중에 한다.
 *
 * <p><b>동시성.</b> 신고 수를 세고 가리는 것은 플레어 행 잠금 안에서 한다(사실 확인과 같은 잠금, D-4). 잠그지 않으면 마지막 몇 명이
 * 동시에 신고할 때 모두 "아직 2건"을 보고 아무도 가리지 않거나, 둘 다 가리며 자동 가림 기록이 두 번 남는다. 가린 뒤에는 보이는
 * 플레어가 아니므로 늦게 온 신고는 FLARE_NOT_FOUND로 끝난다.
 */
@Service
@Transactional(readOnly = true)
public class ModerationService {

    private final FlareRepository flareRepository;
    private final FlareReportRepository reportRepository;
    private final ModerationActionRepository actionRepository;
    private final ModerationProperties properties;
    private final ApplicationEventPublisher events;

    public ModerationService(FlareRepository flareRepository, FlareReportRepository reportRepository,
                             ModerationActionRepository actionRepository, ModerationProperties properties,
                             ApplicationEventPublisher events) {
        this.flareRepository = flareRepository;
        this.reportRepository = reportRepository;
        this.actionRepository = actionRepository;
        this.properties = properties;
        this.events = events;
    }

    /** @return 이 신고로 플레어가 가려졌으면 true */
    @Transactional
    public boolean report(User reporter, Long flareId, ReportReason reason) {
        Flare flare = lockVisible(flareId);
        if (flare.isExternal()) {
            throw new EarthApiException(ErrorCode.REPORT_NOT_ALLOWED);
        }
        if (flare.isAuthor(reporter)) {
            throw new EarthApiException(ErrorCode.CANNOT_REPORT_OWN_FLARE);
        }
        // 잠금 안이라 같은 사람의 동시 신고도 여기서 갈린다. 유니크 제약(V16)이 마지막 방어선이다.
        if (reportRepository.existsByFlareAndReporter(flare, reporter)) {
            throw new EarthApiException(ErrorCode.ALREADY_REPORTED);
        }
        reportRepository.save(new FlareReport(flare, reporter, reason));
        int count = flare.addReport();
        events.publishEvent(new ModerationRecorded("REPORT", flare.getId(), reporter.getId(), reason.name(), count));

        if (count < properties.hideThreshold()) {
            return false;
        }
        Instant now = Instant.now();
        flare.hide(now);
        record(flare, null, ModerationAction.Kind.AUTO_HIDE, "신고 " + count + "건", now);
        return true;
    }

    /** 운영자 가림. 신고가 없어도 바로 가린다. */
    @Transactional
    public FlareResponse hide(User admin, Long flareId, String note) {
        Flare flare = lockVisible(flareId);
        Instant now = Instant.now();
        flare.hide(now);
        record(flare, admin.getId(), ModerationAction.Kind.HIDE, note, now);
        return FlareResponse.from(flare);
    }

    /** 운영자 복구. 가려진 플레어만. 수명이 남았으면 다시 지도에 뜬다. */
    @Transactional
    public FlareResponse restore(User admin, Long flareId, String note) {
        Flare flare = lock(flareId);
        if (!flare.isHidden()) {
            throw new EarthApiException(ErrorCode.FLARE_NOT_FOUND, "가려진 플레어만 복구할 수 있습니다.");
        }
        Instant now = Instant.now();
        int reports = flare.getReportCount();
        flare.restore(now);
        record(flare, admin.getId(), ModerationAction.Kind.RESTORE, note, reports, now);
        return FlareResponse.from(flare);
    }

    /** 운영자 삭제. 보이는 플레어든 가려진 플레어든. 사용자 플레어만 — 외부 플레어는 출처 동기화가 다시 만든다. */
    @Transactional
    public FlareResponse remove(User admin, Long flareId, String note) {
        Flare flare = lock(flareId);
        if (flare.isExternal()) {
            throw new EarthApiException(ErrorCode.REPORT_NOT_ALLOWED);
        }
        if (!flare.isHidden() && !flare.isVisible()) {
            throw new EarthApiException(ErrorCode.FLARE_NOT_FOUND);
        }
        Instant now = Instant.now();
        flare.remove(now);
        record(flare, admin.getId(), ModerationAction.Kind.REMOVE, note, now);
        return FlareResponse.from(flare);
    }

    /** 검토 목록: 가려진 플레어와 사유별 신고 수. 운영자만 부른다. */
    public List<HiddenFlareResponse> hidden(int limit) {
        List<Flare> flares = flareRepository.findHidden(PageRequest.of(0, Math.clamp(limit, 1, 200)));
        if (flares.isEmpty()) return List.of();
        Map<Long, Map<ReportReason, Long>> reasons = new HashMap<>();
        for (Object[] row : reportRepository.countByReason(flares.stream().map(Flare::getId).toList())) {
            reasons.computeIfAbsent((Long) row[0], k -> new EnumMap<>(ReportReason.class))
                    .put((ReportReason) row[1], (Long) row[2]);
        }
        return flares.stream()
                .map(f -> HiddenFlareResponse.from(f, reasons.getOrDefault(f.getId(), Map.of())))
                .toList();
    }

    private void record(Flare flare, Long actorId, ModerationAction.Kind kind, String note, Instant now) {
        record(flare, actorId, kind, note, flare.getReportCount(), now);
    }

    private void record(Flare flare, Long actorId, ModerationAction.Kind kind, String note, int reports, Instant now) {
        String trimmed = note == null || note.isBlank() ? null : note.strip();
        if (trimmed != null && trimmed.length() > 200) trimmed = trimmed.substring(0, 200);
        actionRepository.save(new ModerationAction(flare.getId(), actorId, kind, trimmed, reports, now));
        events.publishEvent(new ModerationRecorded(kind.name(), flare.getId(), actorId, trimmed, reports));
        // 가리기·삭제는 ACTIVE가 아니므로 받는 화면에서 사라지고, 복구는 다시 나타난다. 커밋 뒤에 나간다(FlareChanged).
        // 가린 플레어는 위치와 상태만 보낸다 — 걷어낼 화면에 가려진 글 내용을 실어 보낼 이유가 없다.
        FlareResponse snapshot = FlareResponse.from(flare);
        events.publishEvent(FlareChanged.updated(flare.isVisible() ? snapshot : snapshot.toRestricted()));
    }

    private Flare lockVisible(Long flareId) {
        Flare flare = lock(flareId);
        if (!flare.isVisible()) {
            throw new EarthApiException(ErrorCode.FLARE_NOT_FOUND);
        }
        return flare;
    }

    private Flare lock(Long flareId) {
        return flareRepository.findByIdForUpdate(flareId)
                .orElseThrow(() -> new EarthApiException(ErrorCode.FLARE_NOT_FOUND));
    }

    /** 상세 조회에서 쓰는 판단: 가려졌거나 운영자가 지운 플레어는 작성자 말고는 볼 수 없다. */
    public static boolean hiddenFrom(Flare flare, User viewer) {
        boolean concealed = flare.getStatus() == FlareStatus.HIDDEN || flare.getStatus() == FlareStatus.REMOVED;
        return concealed && !flare.isAuthor(viewer);
    }
}
