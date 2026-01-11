package com.petlytic.repositories;

import com.petlytic.models.PasswordResetToken;
import com.petlytic.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByUserAndResetCode(User user, String resetCode);

    void deleteAllByUser(User user);
}

