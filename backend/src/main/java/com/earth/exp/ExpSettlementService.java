package com.earth.exp;

import com.earth.config.ExpProperties;
import com.earth.domain.flare.Flare;
import com.earth.domain.flare.FlareRepository;
import com.earth.domain.flare.FlareStatus;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 끝난 플레어의 사실 확인 수로 작성자의 경험치를 정산한다(D-21).
 *
 * <p><b>왜 끝날 때 한 번인가.</b> 누를 때마다 바로 더하고 빼면, 누르기·취소를 반복할 때마다 경험치가 오르내리고 정확히
 * 회수해야 한다. 플레어가 끝나면 사실 확인을 더 받을 수 없으므로(확인은 보이는 플레어에만, 행 잠금 안에서 검사) 그때의
 * 최종 수로 한 번 계산하면 중간 과정을 신경 쓸 필요가 없다.
 *
 * <p><b>한 번만.</b> 플레어 행을 잠그고 settled_at을 확인·기록한다. 백엔드가 여러 대여도, 같은 플레어를 두 곳이 동시에
 * 집어도 하나만 정산한다. 원장의 (flare_id, reason) 유니크 인덱스가 마지막 방어선이다.
 *
 * <p><b>잠금 순서.</b> 플레어 → 작성자. 플레어 등록은 작성자만 잠그고(D-3) 기존 플레어는 잠그지 않으며, 사실 확인은 플레어만
 * 잠근다. 반대 순서로 잡는 곳이 없어 교착이 생기지 않는다. 작성자를 잠그는 이유: 같은 사람의 플레어 여러 개가 동시에
 * 정산되면 경험치를 읽고 더해 쓰는 사이에 서로를 덮어쓴다.
 */
@Service
public class ExpSettlementService {

    /** 정산 결과. 원장과 같은 값이며, 커밋 뒤 로그(ExpLog)에 쓴다. */
    public record Settlement(Long userId, Long flareId, int delta, int expBefore, int expAfter,
                             int seen, int ended, int disputed) {
    }

    private final FlareRepository flareRepository;
    private final UserRepository userRepository;
    private final ExpLedgerRepository ledgerRepository;
    private final ExpRule rule;

    public ExpSettlementService(FlareRepository flareRepository, UserRepository userRepository,
                                ExpLedgerRepository ledgerRepository, ExpProperties properties) {
        this.flareRepository = flareRepository;
        this.userRepository = userRepository;
        this.ledgerRepository = ledgerRepository;
        this.rule = new ExpRule(properties);
    }

    @Transactional(readOnly = true)
    public List<Long> findDue(int limit) {
        return flareRepository.findIdsDueForSettlement(Instant.now(), PageRequest.of(0, limit));
    }

    /** 플레어 하나를 정산한다. 이미 정산했거나, 아직 보이거나, 외부 플레어면 아무것도 하지 않는다. */
    @Transactional
    public Optional<Settlement> settle(Long flareId) {
        Flare flare = flareRepository.findByIdForUpdate(flareId).orElse(null);
        // 잠근 뒤에 다시 확인한다. 고르는 쿼리와 잠금 사이에 다른 곳이 정산했을 수 있다.
        // 임시 비공개는 운영자 결론(복구·삭제)이 날 때까지 정산하지 않는다(D-24).
        if (flare == null || flare.isSettled() || flare.isExternal() || flare.isVisible() || flare.isHidden()) {
            return Optional.empty();
        }
        User author = userRepository.findByIdForUpdate(flare.getAuthor().getId()).orElseThrow();
        int seen = flare.getConfirmCount();
        int ended = flare.getEndedCount();
        int disputed = flare.getDisputedCount();
        int delta = rule.delta(seen, disputed);
        // 운영자가 삭제한 플레어는 가점을 주지 않는다. 감점은 그대로 — 신고로 지워졌다고 감점이 사라지면 안 된다.
        boolean removed = flare.getStatus() == FlareStatus.REMOVED;
        if (removed) {
            delta = Math.min(0, delta);
        }

        int before = author.getExp();
        author.changeExp(delta);
        Instant now = Instant.now();
        flare.markSettled(now);
        // 변화가 0이어도 남긴다. "이 플레어는 이런 수로 정산됐다"가 분석의 근거다.
        ledgerRepository.save(new ExpLedgerEntry(author.getId(), flare.getId(), ExpLedgerEntry.FLARE_SETTLED, delta,
                before, author.getExp(), seen, ended, disputed,
                removed ? rule.describe() + " · 운영자 삭제: 가점 없음" : rule.describe(), now));
        return Optional.of(new Settlement(author.getId(), flare.getId(), delta, before, author.getExp(),
                seen, ended, disputed));
    }
}
