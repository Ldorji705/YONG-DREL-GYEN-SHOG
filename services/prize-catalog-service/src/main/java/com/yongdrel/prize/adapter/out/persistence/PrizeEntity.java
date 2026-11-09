package com.yongdrel.prize.adapter.out.persistence;

import com.yongdrel.prize.domain.model.PrizeStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "prizes")
public class PrizeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(nullable = false, length = 150)
    public String name;

    @Column(length = 1000)
    public String description;

    @Column(nullable = false, precision = 15, scale = 2)
    public BigDecimal value;

    @Column(nullable = false)
    public Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public PrizeStatus status;

    @Column(nullable = false, updatable = false)
    public String createdBy;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @Column(nullable = false)
    public LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
