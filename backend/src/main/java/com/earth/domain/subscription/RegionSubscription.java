package com.earth.domain.subscription;

import com.earth.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 사용자가 즐겨찾기한 관심 지역. 이 안에 별이 생기면 알림을 만든다.
 *
 * <p><b>여기 저장된 좌표는 사용자의 위치가 아니다.</b> 사용자가 직접 고른 "알림받고 싶은
 * 구역"의 중심과 반경이며, 사용자가 그곳에 있었는지와는 무관하다. 서울에 살면서 부산을
 * 구독할 수 있다. 위치정보로 오해해 다루지 않도록 명시해 둔다.
 *
 * <p>이 구분이 중요한 이유는, 이 서비스가 사용자의 위치를 서버에 남기지 않는다는 원칙 위에
 * 설계돼 있기 때문이다. 그 원칙이 깨지면 위치기반서비스 신고 의무가 발생한다.
 */
@Entity
@Table(name = "region_subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "radius_km", nullable = false)
    private double radiusKm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public RegionSubscription(User user, String label, double latitude, double longitude, double radiusKm) {
        this.user = user;
        this.label = label;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusKm = radiusKm;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public boolean covers(double eventLat, double eventLng) {
        return haversineKm(latitude, longitude, eventLat, eventLng) <= radiusKm;
    }

    private static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }
}
