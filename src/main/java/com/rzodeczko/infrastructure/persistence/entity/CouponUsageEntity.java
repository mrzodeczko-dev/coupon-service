package com.rzodeczko.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(
        name = "coupon_usages",
        uniqueConstraints = @UniqueConstraint(columnNames = {"code", "user_id"})
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CouponUsageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @CreatedDate
    @Column(name = "used_at", nullable = false, updatable = false)
    private Instant usedAt;
}
