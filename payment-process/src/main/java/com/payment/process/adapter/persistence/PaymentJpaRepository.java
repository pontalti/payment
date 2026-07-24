package com.payment.process.adapter.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.payment.process.adapter.persistence.model.PaymentEntity;
import com.payment.process.domain.PaymentStatus;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {

    public Optional<PaymentEntity> findByUuid(UUID uuid);

    public List<PaymentEntity> findByOrderByIdAsc(Limit limit);

    public List<PaymentEntity> findByIdGreaterThanOrderByIdAsc(Long cursor, Limit limit);

    @Modifying
    @Query("UPDATE PaymentEntity p SET p.status = :status WHERE p.uuid = :uuid")
    public int updateStatusByUuid(@Param("uuid") UUID uuid, @Param("status") PaymentStatus status);
    
    @Modifying
    @Query("DELETE FROM PaymentEntity p WHERE p.uuid = :uuid")
    public int deleteByUuid(@Param("uuid") UUID uuid);
    
}