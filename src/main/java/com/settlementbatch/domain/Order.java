package com.settlementbatch.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_status_date", columnList = "status, order_date"),
        @Index(name = "idx_orders_date", columnList = "order_date")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sellerId;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private LocalDate orderDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** JDBC RowMapper용 정적 팩토리 — JPA 영속성 컨텍스트를 거치지 않는 경로 */
    public static Order fromRow(Long id, Long sellerId, Long amount,
                                String status, LocalDate orderDate, LocalDateTime createdAt) {
        Order order = new Order();
        order.id = id;
        order.sellerId = sellerId;
        order.amount = amount;
        order.status = status;
        order.orderDate = orderDate;
        order.createdAt = createdAt;
        return order;
    }

    public void settle() {
        this.status = "SETTLED";
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
