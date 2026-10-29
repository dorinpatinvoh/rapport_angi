package com.rapportangi.backend.repository;

import com.rapportangi.backend.entity.SalesCountry;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SalesCountryRepository
  extends JpaRepository<SalesCountry, String>
{
  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from SalesCountry c where c.code=:code")
  java.util.Optional<SalesCountry> locked(@Param("code") String code);
}
