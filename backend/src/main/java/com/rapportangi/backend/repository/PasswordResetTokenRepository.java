package com.rapportangi.backend.repository;

import com.rapportangi.backend.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository
  extends JpaRepository<PasswordResetToken, Long>
{
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from PasswordResetToken t where t.tokenHash=:hash")
  Optional<PasswordResetToken> findLocked(@Param("hash") String hash);

  void deleteByUserId(Long userId);
}
