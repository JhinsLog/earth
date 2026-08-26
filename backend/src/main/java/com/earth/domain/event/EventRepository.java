package com.earth.domain.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("""
            select e from Event e
            where e.status = 'ACTIVE'
              and e.latitude between :southLat and :northLat
              and e.longitude between :westLng and :eastLng
            order by e.createdAt desc
            """)
    List<Event> findActiveWithinBoundingBox(
            @Param("southLat") double southLat,
            @Param("northLat") double northLat,
            @Param("westLng") double westLng,
            @Param("eastLng") double eastLng);

    List<Event> findTop500ByStatusOrderByCreatedAtDesc(EventStatus status);

    /**
     * 특정 시각 이후 이 사용자가 만든 이벤트 수. 등록 빈도 제한에 쓰인다.
     * 매 요청마다 기준 시각을 지금으로부터 계산하므로 고정 구간이 아닌 슬라이딩 윈도우가 된다.
     */
    long countByAuthorAndCreatedAtAfter(com.earth.domain.user.User author, java.time.Instant threshold);
}
