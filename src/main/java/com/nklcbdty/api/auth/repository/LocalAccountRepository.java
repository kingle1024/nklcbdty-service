package com.nklcbdty.api.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nklcbdty.api.auth.vo.LocalAccount;

public interface LocalAccountRepository extends JpaRepository<LocalAccount, Long> {

    Optional<LocalAccount> findByEmail(String email);

    boolean existsByEmail(String email);
}
