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
}
