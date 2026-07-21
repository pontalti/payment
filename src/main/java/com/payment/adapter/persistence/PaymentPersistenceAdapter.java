package com.payment.adapter.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

import com.payment.adapter.persistence.model.PaymentEntity;
import com.payment.domain.process.model.Payment;
import com.payment.domain.process.model.PaymentId;
import com.payment.domain.process.model.PaymentInstrument;
import com.payment.domain.process.model.PaymentNotFoundException;
import com.payment.domain.process.model.PaymentPage;
import com.payment.domain.process.model.PaymentStatus;
import com.payment.domain.process.port.out.PaymentRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PaymentPersistenceAdapter implements PaymentRepository{

	private static final String CACHE_NAME = "payments";
	
    private final PaymentJpaRepository jpaRepository;

    public PaymentPersistenceAdapter(PaymentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @CachePut(cacheNames = CACHE_NAME, key = "#payment.id().value().toString()")
    public Payment save(Payment payment) {
        PaymentEntity entity = toEntity(payment);
        PaymentEntity saved = this.jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "#id.value().toString()")
    public Optional<Payment> findById(PaymentId id) {
        return this.jpaRepository.findByUuid(id.value())
                .map(this::toDomain);
    }
    
    @Override
    public PaymentPage findNextPage(Long cursor, int limit) {
        Limit lim = Limit.of(limit);
        List<PaymentEntity> entities = (cursor == null)
                ? this.jpaRepository.findByOrderByIdAsc(lim)
                : this.jpaRepository.findByIdGreaterThanOrderByIdAsc(cursor, lim);

        List<Payment> items = entities.stream().map(this::toDomain).toList();
        Long nextCursor = entities.isEmpty()
                ? null
                : entities.get(entities.size() - 1).getId();

        return new PaymentPage(items, nextCursor);
    }
    
    @Override
    @CachePut(cacheNames = CACHE_NAME, key = "#id.value().toString()")
    public Payment updateStatus(PaymentId id, PaymentStatus status) {
        this.jpaRepository.updateStatusByUuid(id.value(), status);
        return this.jpaRepository.findByUuid(id.value())
                .map(this::toDomain)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }
    
    @Override
    @CacheEvict(cacheNames = CACHE_NAME, key = "#id.value().toString()")
    public void delete(PaymentId id) {
        int deleted = this.jpaRepository.deleteByUuid(id.value());
        if (deleted == 0) {
            throw new PaymentNotFoundException(id);
        }
    }

    private PaymentEntity toEntity(Payment payment) {
        PaymentInstrument instrument = payment.instrument();
        return new PaymentEntity(null,
                payment.id().value(),
                payment.orderId(),
                payment.amount(),
                payment.currency(),
                instrument.method(),
                instrument.fundingType(),
                payment.status(),
                payment.processedAt()
        );
    }

    private Payment toDomain(PaymentEntity entity) {
        return new Payment(
                new PaymentId(entity.getUuid()),
                entity.getOrderId(),
                entity.getAmount(),
                entity.getCurrency(),
                new PaymentInstrument(entity.getMethod(), entity.getFundingType()),
                entity.getStatus(),
                entity.getProcessedAt()
        );
    }

}
