package com.commerceos.order.repository;

import com.commerceos.order.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByPhone(String phone);
    Optional<UserAccount> findByEmail(String email);
}
