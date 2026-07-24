package com.payment.process.adapter.persistence.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.Hibernate;

import com.payment.process.domain.FundingType;
import com.payment.process.domain.PaymentMethod;
import com.payment.process.domain.PaymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payment")  
public class PaymentEntity {
	
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
	
    @Column(name = "uuid", unique = true, nullable = false, updatable = false)
    private UUID uuid;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "funding_type")
    private FundingType fundingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
    
    @Override
    public int hashCode() {
    	return Hibernate.getClass(this).hashCode();
    }
    
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PaymentEntity other = (PaymentEntity) obj;
		return uuid != null && Objects.equals(uuid, other.uuid);
	}

	@Override
	public String toString() {
		return "PaymentEntity [id=" + id + ", uuid=" + uuid + ", orderId=" + orderId + ", amount=" + amount
				+ ", currency=" + currency + ", method=" + method + ", fundingType=" + fundingType + ", status="
				+ status + ", processedAt=" + processedAt + "]";
	}
    
}
