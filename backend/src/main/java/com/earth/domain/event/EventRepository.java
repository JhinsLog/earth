package com.earth.domain.event;

import com.earth.domain.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("""
            select e from Event e
            where e.status = com.earth.domain.event.EventStatus.ACTIVE
              and e.expiresAt > :now
              and e.latitude between :southLat and :northLat
              and e.longitude between :westLng and :eastLng
            order by e.createdAt desc
            """)
    List<Event> findVisibleWithinBoundingBox(
            @Param("now") Instant now,
            @Param("southLat") double southLat,
            @Param("northLat") double northLat,
            @Param("westLng") double westLng,
            @Param("eastLng") double eastLng);

    @Query("""
            select e from Event e
            where e.status = com.earth.domain.event.EventStatus.ACTIVE
              and e.expiresAt > :now
            order by e.createdAt desc
            limit 500
            """)
    List<Event> findVisibleLatest(@Param("now") Instant now);

    /** 만료 시각이 지났는데 아직 ACTIVE로 남아 있는 별. 스케줄러가 정리한다. */
    @Query("""
            select e from Event e
            where e.status = com.earth.domain.event.EventStatus.ACTIVE
              and e.expiresAt <= :now
            """)
    List<Event> findDueForExpiration(@Param("now") Instant now);

    /**
     * 특정 시각 이후 이 사용자가 만든 이벤트 수. 등록 빈도 제한에 쓰인다.
     * 매 요청마다 기준 시각을 지금으로부터 계산하므로 고정 구간이 아닌 슬라이딩 윈도우가 된다.
     */
    long countByAuthorAndCreatedAtAfter(User author, Instant threshold);

    /**
     * 별 행을 잠그고 읽는다. 같은 별에 대한 공감/취소를 직렬화하는 데 쓴다.
     *
     * <p>{@code confirm_count}와 {@code expires_at}은 "읽어서 계산하고 쓴다". 서로 다른 두
     * 사람이 거의 동시에 공감하면 둘 다 같은 값을 읽어 같은 값을 쓰므로, 나중에 쓴 쪽이 먼저
     * 쓴 값을 덮어쓴다(lost update). 공감 기록은 2건 남는데 카운터는 1만 오른다.
     *
     * <p>진짜 문제는 카운터가 아니라 <b>수명 연장이 함께 유실된다는 것</b>이다. "확인되지 않은
     * 별은 스스로 소멸하고, 여러 명이 목격한 사건은 오래 남는다"가 이 서비스의 전제인데,
     * 사람이 몰려 동시 공감이 겹칠수록 그 전제가 어긋난다. 하필 가장 중요한 별에서 틀어진다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);
}
