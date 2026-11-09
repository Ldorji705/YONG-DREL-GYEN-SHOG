package com.yongdrel.event.adapter.out.persistence;

import com.yongdrel.event.domain.model.EventStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lottery_events")
public class EventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(nullable = false, length = 150)
    public String title;

    @Column(length = 1000)
    public String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public EventStatus status = EventStatus.DRAFT;

    @Column(nullable = false)
    public LocalDateTime startDate;

    @Column(nullable = false)
    public LocalDateTime endDate;

    @Column(nullable = false, updatable = false)
    public String createdBy;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @Column(nullable = false)
    public LocalDateTime updatedAt;

    @Column(nullable = false)
    public int maxTickets = 0;

    @Column(nullable = false)
    public int reservedTickets = 0;

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
