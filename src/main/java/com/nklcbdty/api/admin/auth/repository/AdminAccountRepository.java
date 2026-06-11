package com.nklcbdty.api.admin.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nklcbdty.api.admin.auth.vo.AdminAccount;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {
    Optional<AdminAccount> findByUsername(String username);
}
