package com.earth.service;

import com.earth.config.FlareProperties;
import com.earth.domain.flare.BoundingBox;
import com.earth.domain.flare.Flare;
import com.earth.domain.flare.FlareChanged;
import com.earth.domain.flare.FlareConfirmation;
import com.earth.domain.flare.FlareConfirmationRepository;
import com.earth.domain.flare.FlareRepository;
import com.earth.domain.geo.CountryBoundaryRepository;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.dto.FlareCreateRequest;
import com.earth.dto.FlareResponse;
import com.earth.dto.FlareUpdateRequest;
import com.earth.exception.EarthApiException;
import com.earth.exception.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class FlareService {

    private static final Duration RATE_LIMIT_WINDOW = Duration.ofHours(1);

    private final FlareRepository flareRepository;
    private final FlareConfirmationRepository confirmationRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;
    private final FlareProperties flareProperties;
    private final CountryBoundaryRepository countryBoundaryRepository;

    public FlareService(FlareRepository flareRepository,
                         FlareConfirmationRepository confirmationRepository,
                         UserRepository userRepository,
                         ApplicationEventPublisher events,
                         FlareProperties flareProperties,
                         CountryBoundaryRepository countryBoundaryRepository) {
        this.flareRepository = flareRepository;
        this.confirmationRepository = confirmationRepository;
        this.userRepository = userRepository;
        this.events = events;
        this.flareProperties = flareProperties;
        this.countryBoundaryRepository = countryBoundaryRepository;
    }

    /**
     * 이 사용자가 플레어의 상세(제목·내용·분류·작성자·공감 수)를 볼 수 있는지.
     *
     * <p>로그인했으면 전부. 비로그인이면 접속 국가와 같은 나라의 플레어만이고, 나머지는 위치와 존재만 준다.
     * 접속 국가를 모르면(헤더 없음, Cloudflare가 판정 실패) 어느 플레어의 상세도 주지 않는다.
     * 공해의 플레어(country_code null)도 비로그인에게는 상세를 주지 않는다.
     */
    static boolean canSeeDetails(User viewer, String viewerCountry, Flare flare) {
        if (viewer != null) return true;
        return viewerCountry != null && viewerCountry.equals(flare.getCountryCode());
    }

    public List<FlareResponse> findVisible(User viewer, String viewerCountry, Double southLat, Double northLat,
                                            Double westLng, Double eastLng) {
        Instant now = Instant.now();
        List<Flare> flares;
        boolean anyBound = southLat != null || northLat != null || westLng != null || eastLng != null;
        boolean allBounds = southLat != null && northLat != null && westLng != null && eastLng != null;
        if (!anyBound) {
            flares = flareRepository.findVisibleLatest(now);
        } else if (!allBounds) {
            // 일부만 오면 전 세계 조회로 조용히 넘기지 않는다. 화면 범위를 보냈다고 믿는 클라이언트가
            // 엉뚱한 플레어 500개를 받고도 알아채지 못한다.
            throw new EarthApiException(ErrorCode.INVALID_BOUNDS, "범위 좌표 네 개를 모두 보내야 합니다.");
        } else {
            flares = flareRepository.findVisibleWithin(now, toBoundingBox(southLat, northLat, westLng, eastLng));
        }
        // 플레어마다 "내가 공감했나"를 묻으면 플레어 수만큼 쿼리가 나간다(최대 500개). 한 번에 가져온다.
        Set<Long> confirmedIds = confirmedFlareIds(viewer, flares);
        return flares.stream()
                .map(flare -> {
                    FlareResponse full = FlareResponse.from(flare, confirmedIds.contains(flare.getId()));
                    return canSeeDetails(viewer, viewerCountry, flare) ? full : full.toRestricted();
                })
                .toList();
    }

    private Set<Long> confirmedFlareIds(User viewer, List<Flare> flares) {
        if (viewer == null || flares.isEmpty()) return Set.of();
        return confirmationRepository.findByUserAndFlareIn(viewer, flares).stream()
                .map(confirmation -> confirmation.getFlare().getId())
                .collect(Collectors.toSet());
    }

    private static BoundingBox toBoundingBox(double southLat, double northLat, double westLng, double eastLng) {
        try {
            return BoundingBox.of(southLat, northLat, westLng, eastLng);
        } catch (IllegalArgumentException e) {
            throw new EarthApiException(ErrorCode.INVALID_BOUNDS, e.getMessage());
        }
    }

    public FlareResponse findById(User viewer, String viewerCountry, Long flareId) {
        Flare flare = getFlareOrThrow(flareId);
        if (!canSeeDetails(viewer, viewerCountry, flare)) {
            throw new EarthApiException(ErrorCode.COUNTRY_RESTRICTED);
        }
        boolean confirmed = viewer != null && confirmationRepository.existsByFlareAndUser(flare, viewer);
        return FlareResponse.from(flare, confirmed);
    }

    @Transactional
    public FlareResponse create(User author, FlareCreateRequest request) {
        // 검사 전에 작성자 행을 잠근다. 잠그지 않으면 같은 사용자의 요청 여러 개가 아래 COUNT를
        // 거의 동시에 실행해 전부 같은 값을 보고 통과한다(한도 5에 동시 요청 15개 → 11개 저장).
        // 도배를 막으려고 만든 제한이 정확히 도배 방식으로 뚫리는 셈이다.
        // 잠금은 같은 사용자에게만 걸리므로 다른 사용자의 등록은 기다리지 않는다.
        userRepository.findByIdForUpdate(author.getId())
                .orElseThrow(() -> new EarthApiException(ErrorCode.USER_NOT_FOUND));

        // 한 사람이 지도를 도배하지 못하도록 최근 1시간 등록 수를 확인한다.
        // 클라이언트에서도 안내하지만 그쪽은 우회 가능하므로 실제 차단은 여기서 한다.
        long recentCount =
                flareRepository.countByAuthorAndCreatedAtAfter(author, Instant.now().minus(RATE_LIMIT_WINDOW));
        if (recentCount >= flareProperties.maxPerHour()) {
            throw new EarthApiException(
                    ErrorCode.FLARE_RATE_LIMIT_EXCEEDED,
                    "1시간에 최대 %d개까지 등록할 수 있습니다. 잠시 후 다시 시도해 주세요."
                            .formatted(flareProperties.maxPerHour()));
        }

        // 국가는 등록할 때 한 번만 판정한다. 12해리 최근접 판정은 거리 계산이라 조회 때마다 하기엔 무겁다.
        String countryCode = countryBoundaryRepository.countryCodeAt(request.latitude(), request.longitude());
        Flare flare = new Flare(author, request.title(), request.content(), request.category(),
                request.latitude(), request.longitude(), countryCode,
                Duration.ofMinutes(flareProperties.ttlMinutes()));
        flareRepository.save(flare);

        // 전파와 구독자 알림은 커밋된 뒤에 일어난다. 이유는 FlareChanged 주석 참고.
        FlareResponse response = FlareResponse.from(flare);
        events.publishEvent(FlareChanged.created(response));
        return response;
    }

    @Transactional
    public FlareResponse update(User actor, Long flareId, FlareUpdateRequest request) {
        Flare flare = getEditableOrThrow(actor, flareId);
        flare.update(request.title(), request.content(), request.category());

        FlareResponse response = FlareResponse.from(flare);
        // 다른 사용자 화면에도 수정 내용이 즉시 반영되도록 같은 채널로 전파한다.
        events.publishEvent(FlareChanged.updated(response));
        return response;
    }

    @Transactional
    public FlareResponse delete(User actor, Long flareId) {
        Flare flare = getEditableOrThrow(actor, flareId);
        flare.markDeleted();

        FlareResponse response = FlareResponse.from(flare);
        // status가 ACTIVE가 아니므로 구독자 화면에서는 목록에서 제거된다.
        events.publishEvent(FlareChanged.updated(response));
        return response;
    }

    /**
     * TTL이 지난 플레어를 EXPIRED로 전환하고 전파한다.
     *
     * <p>조회는 이미 expiresAt으로 걸러지므로 이 작업이 늦어도 사용자에게 만료된 플레어가
     * 보이지는 않는다. 여기서는 상태 정리와, 이미 화면을 켜 두고 있는 클라이언트에게
     * 사라졌음을 알리는 실시간 전파를 담당한다.
     *
     * @return 이번에 만료 처리된 개수
     */
    @Transactional
    public int expireDue() {
        List<Flare> due = flareRepository.findDueForExpiration(Instant.now());
        for (Flare flare : due) {
            flare.markExpired();
            events.publishEvent(FlareChanged.updated(FlareResponse.from(flare)));
        }
        return due.size();
    }

    /**
     * "나도 봤다" — 목격 확인을 남긴다.
     *
     * <p>이 서비스의 전제는 사건을 직접 보거나 겪은 사람이 플레어를 등록한다는 것이고, 공감은
     * 그 전제를 다른 목격자가 뒷받침하는 장치다. 쌓일수록 플레어가 오래 남고 더 밝게 보인다.
     *
     * <p>공감에는 위치 조건이 없다. 서버가 "이 사용자가 그 플레어 근처에 있었다"를 알 수 있는
     * 데이터를 남기지 않기 위해서다. 위치기반서비스 신고 의무는 영리 여부가 아니라 서버가
     * 위치데이터를 수집·처리하는지로 갈리므로, 그 경계를 넘지 않도록 설계를 맞췄다.
     * 남용은 등록 횟수 제한과 30분 TTL이 막는다.
     */
    @Transactional
    public FlareResponse confirm(User user, Long flareId) {
        Flare flare = getVisibleForUpdateOrThrow(flareId);
        if (flare.isAuthor(user)) {
            throw new EarthApiException(ErrorCode.CANNOT_CONFIRM_OWN_FLARE);
        }
        if (confirmationRepository.existsByFlareAndUser(flare, user)) {
            throw new EarthApiException(ErrorCode.ALREADY_CONFIRMED);
        }

        // 실제로 늘어난 시간을 기록해 둔다. 상한에 걸리면 요청한 5분보다 짧으므로,
        // 취소할 때 5분을 그냥 빼면 자기가 준 것보다 많이 회수하게 된다.
        // 외부 데이터로 만든 플레어는 공감 수만 오르고 수명은 늘지 않는다. 출처가 끝났다고 하면 플레어도 끝나야 한다.
        Duration extension = flare.isExternal()
                ? Duration.ZERO
                : Duration.ofMinutes(flareProperties.confirmExtensionMinutes());
        Duration granted = flare.applyConfirmation(extension, Duration.ofHours(flareProperties.maxLifetimeHours()));
        confirmationRepository.save(new FlareConfirmation(flare, user, granted));

        FlareResponse response = FlareResponse.from(flare, true);
        // 다른 사람 화면에서도 플레어가 밝아지고 수명이 늘어난 것이 즉시 보이도록 전파한다.
        // 전파용 스냅샷은 confirmedByMe를 담지 않는다 — 받는 사람마다 값이 다르다.
        events.publishEvent(FlareChanged.updated(FlareResponse.from(flare)));
        return response;
    }

    /**
     * 공감 취소. 그 공감이 실제로 늘려준 만큼만 수명을 되돌린다.
     *
     * <p>되돌리지 않으면 취소가 기록을 지워 유니크 제약이 다시 통과하므로 재공감으로 연장이
     * 또 붙는다. 버튼을 반복해 누르면 한 사람이 혼자 상한까지 밀어올릴 수 있었다.
     * 정확히 되돌리면 한 사람의 순 기여가 구조적으로 1회분을 넘지 못한다.
     */
    @Transactional
    public FlareResponse withdrawConfirmation(User user, Long flareId) {
        Flare flare = getVisibleForUpdateOrThrow(flareId);
        confirmationRepository.findByFlareAndUser(flare, user).ifPresent(confirmation -> {
            Duration granted = confirmation.granted();
            confirmationRepository.delete(confirmation);
            flare.withdrawConfirmation(granted);
        });

        FlareResponse response = FlareResponse.from(flare, false);
        events.publishEvent(FlareChanged.updated(FlareResponse.from(flare)));
        return response;
    }

    private Flare getVisibleOrThrow(Long flareId) {
        Flare flare = getFlareOrThrow(flareId);
        if (!flare.isVisible()) {
            throw new EarthApiException(ErrorCode.FLARE_NOT_FOUND);
        }
        return flare;
    }

    /**
     * 공감 수와 수명을 바꿀 때 쓰는 조회. 플레어 행을 잠근 채로 읽는다.
     *
     * <p>두 값 모두 "읽어서 계산하고 쓴다"라서, 잠그지 않으면 동시 공감이 서로를 덮어쓴다.
     * 사유는 {@code FlareRepository.findByIdForUpdate} 주석에 적어두었다.
     */
    private Flare getVisibleForUpdateOrThrow(Long flareId) {
        Flare flare = flareRepository.findByIdForUpdate(flareId)
                .orElseThrow(() -> new EarthApiException(ErrorCode.FLARE_NOT_FOUND));
        if (!flare.isVisible()) {
            throw new EarthApiException(ErrorCode.FLARE_NOT_FOUND);
        }
        return flare;
    }

    private Flare getEditableOrThrow(User actor, Long flareId) {
        Flare flare = getFlareOrThrow(flareId);
        if (!flare.isAuthor(actor)) {
            throw new EarthApiException(ErrorCode.FORBIDDEN);
        }
        if (!flare.isVisible()) {
            // 이미 만료·삭제된 플레어는 수정도 삭제도 할 수 없다.
            throw new EarthApiException(ErrorCode.FLARE_NOT_FOUND);
        }
        return flare;
    }

    Flare getFlareOrThrow(Long flareId) {
        return flareRepository.findById(flareId)
                .orElseThrow(() -> new EarthApiException(ErrorCode.FLARE_NOT_FOUND));
    }
}
