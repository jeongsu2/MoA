package com.moa.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AddressRepository extends JpaRepository<Address, Long> {

    Optional<Address> findByUserIdAndIsDefaultTrue(Long userId);

    // 기본이 맨 위, 나머지는 추가한 순서
    List<Address> findByUserIdOrderByIsDefaultDescIdAsc(Long userId);

    // 남의 배송지도 없는 배송지와 똑같이 빈 결과가 된다
    Optional<Address> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Address a set a.isDefault = false where a.user.id = :userId and a.isDefault = true")
    int clearDefault(@Param("userId") Long userId);

    // 한 문장으로 대상만 true, 나머지는 false. 대상이 내 배송지인지는 먼저 확인해야 한다.
    // 아니면 WHERE에 걸리는 행이 모두 false가 되어 기본이 0개가 된다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Address a set a.isDefault = case when a.id = :addressId then true else false end"
            + " where a.user.id = :userId")
    int changeDefault(@Param("userId") Long userId, @Param("addressId") Long addressId);
}
