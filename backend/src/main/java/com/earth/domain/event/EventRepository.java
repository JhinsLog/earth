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

    /**
     * 화면 범위 안의 보이는 별, 최신순 500개.
     *
     * <p>LIMIT이 없으면 넓은 범위에서 응답이 무한정 커진다. 보이는 별 20만 개 기준으로 한국 범위가
     * 14만 행·200ms였고 정렬이 메모리를 넘어 디스크로 넘쳤다. 지구본에 한 번에 그릴 수 있는 양도
     * 그보다 훨씬 적다. 전 세계 조회({@link #findVisibleLatest})와 같은 상한을 쓴다.
     *
     * <p>경도 조건이 두 구간인 이유는 {@link BoundingBox} 참고. 날짜변경선을 넘지 않으면 두 구간이 같다.
     */
    @Query("""
            select e from Event e
            where e.status = com.earth.domain.event.EventStatus.ACTIVE
              and e.expiresAt > :now
              and e.latitude between :southLat and :northLat
              and (e.longitude between :westLng1 and :eastLng1
                   or e.longitude between :westLng2 and :eastLng2)
            order by e.createdAt desc
            limit 500
            """)
    List<Event> findVisibleWithinRanges(
            @Param("now") Instant now,
            @Param("southLat") double southLat,
            @Param("northLat") double northLat,
            @Param("westLng1") double westLng1,
            @Param("eastLng1") double eastLng1,
            @Param("westLng2") double westLng2,
            @Param("eastLng2") double eastLng2);

    /**
     * 범위 조회 전에 이 트랜잭션에서만 계획 재사용을 끈다.
     *
     * <p>같은 쿼리가 한 연결에서 반복되면 PostgreSQL은 파라미터 값을 보지 않는 범용 계획으로 바꾼다.
     * 이 쿼리는 범위에 따라 해당 행이 95개(유럽)부터 14만 개(한국)까지 벌어져서, 맞는 인덱스가 다르다.
     * 빽빽하면 최신순 인덱스로 걷다 500개에서 멈추고, 성기면 위경도 인덱스로 범위만 읽어야 한다.
     * 범용 계획은 범위를 아주 좁다고 가정해 위경도 인덱스에 고정되고, 한국 범위가 1ms에서 80ms가 된다
     * (보이는 별 20만 측정, V6 주석). 매번 계획하는 비용은 0.2~0.3ms다.
     * {@code SET LOCAL}과 같고 트랜잭션이 끝나면 풀린다. 연결 풀의 다른 쿼리에는 영향이 없다.
     */
    @Query(value = "select set_config('plan_cache_mode', 'force_custom_plan', true)", nativeQuery = true)
    String planEachExecutionInThisTransaction();

    default List<Event> findVisibleWithin(Instant now, BoundingBox box) {
        planEachExecutionInThisTransaction();
        return findVisibleWithinRanges(now, box.southLat(), box.northLat(),
                box.westLng1(), box.eastLng1(), box.westLng2(), box.eastLng2());
    }

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
