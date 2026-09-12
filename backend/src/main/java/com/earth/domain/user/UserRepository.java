package com.earth.domain.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

    /**
     * 사용자 행을 잠그고 읽는다. 같은 사용자의 요청을 직렬화하는 데 쓴다.
     *
     * <p>등록 빈도 제한은 "최근 1시간 등록 수를 세고, 한도보다 적으면 저장한다"는 두 단계다.
     * 그 사이에 다른 요청이 끼어들면 여러 요청이 모두 같은 카운트를 보고 통과해서, 도배를
     * 막으려고 만든 제한이 정확히 도배 방식으로 뚫린다(한도 5에 동시 요청 15개를 보내면
     * 11개가 저장됐다). 검사 전에 이 행을 잠그면 같은 사용자의 요청만 줄을 서고 다른
     * 사용자는 영향을 받지 않는다.
     *
     * <p>잠그는 대상이 사용자 행인 이유는, 레벨별 등록 한도를 도입하면 한도값도 같은 행에서
     * 읽게 되기 때문이다. 한도와 카운트를 한 잠금 안에서 함께 읽으면 판정이 일관된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
