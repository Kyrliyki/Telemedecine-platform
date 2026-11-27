package com.telemed.user.infrastructure.persistence.repository;

import com.telemed.user.domain.valueobject.UserRole;
import com.telemed.user.domain.valueobject.UserStatus;
import com.telemed.user.infrastructure.persistence.entity.DoctorProfileEntity;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaDoctorProfileRepository extends JpaRepository<DoctorProfileEntity, UUID> {

  @Query(
      """
      SELECT d FROM DoctorProfileEntity d, UserEntity u JOIN u.roles r
      WHERE d.userId = u.id
        AND u.status = :status
        AND r = :role
        AND d.verificationStatus = 'VERIFIED'
        AND d.acceptingPatients = TRUE
        AND (:specialization = ''
             OR LOWER(d.specialization) LIKE LOWER(CONCAT('%', :specialization, '%')))
      """)
  Page<DoctorProfileEntity> findActiveDoctors(
      @Param("specialization") String specialization,
      @Param("status") UserStatus status,
      @Param("role") UserRole role,
      Pageable pageable);

  @Query(
      """
      SELECT d FROM DoctorProfileEntity d, UserEntity u JOIN u.roles r
      WHERE d.userId = u.id
        AND r = :role
        AND (:verificationStatus = ''
             OR d.verificationStatus = :verificationStatus)
        AND (:specialization = ''
             OR LOWER(d.specialization) LIKE LOWER(CONCAT('%', :specialization, '%')))
      """)
  Page<DoctorProfileEntity> findForAdmin(
      @Param("verificationStatus") String verificationStatus,
      @Param("specialization") String specialization,
      @Param("role") UserRole role,
      Pageable pageable);
}
