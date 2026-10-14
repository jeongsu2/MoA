package com.moa.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {

    Optional<Address> findByUserIdAndIsDefaultTrue(Long userId);
}
