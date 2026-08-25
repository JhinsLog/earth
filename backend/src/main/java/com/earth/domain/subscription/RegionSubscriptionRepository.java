package com.earth.domain.subscription;

import com.earth.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegionSubscriptionRepository extends JpaRepository<RegionSubscription, Long> {
    List<RegionSubscription> findAllByUser(User user);
}
