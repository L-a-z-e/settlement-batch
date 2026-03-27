package com.settlementbatch.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "settlements", uniqueConstraints = {
        @UniqueConstraint(name = "uk_settlements_order_id", columnNames = "order_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long sellerId;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private Long fee;

    @Column(nullable = false)
    private Long netAmount;

    @Column(nullable = false)
    private LocalDateTime settledAt;

    public Settlement(Long orderId, Long sellerId, Long amount, Long fee, Long netAmount) {
        this.orderId = orderId;
        this.sellerId = sellerId;
        this.amount = amount;
        this.fee = fee;
        this.netAmount = netAmount;
        this.settledAt = LocalDateTime.now();
    }
}
