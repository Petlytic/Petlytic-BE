package com.petlytic.repositories;

import com.petlytic.models.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String refreshToken);

    @Query("select t from RefreshToken t where t.user.id = :userId and t.revoked = false")
    List<RefreshToken> findAllValidTokenByUser(UUID userId);
}
