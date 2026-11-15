package com.yongdrel.reportingservice.adapter.out.persistence;

import com.yongdrel.reportingservice.domain.model.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "saved_reports")
class SavedReportEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="tenant_id",nullable=false) private Long tenantId;
    @Column(name="owner_id",nullable=false) private Long ownerId;
    @Column(nullable=false,length=80) private String name;
    @Enumerated(EnumType.STRING) @Column(name="report_type",nullable=false,length=20) private ReportType reportType;
    @Column(name="event_id") private Long eventId;
    @Column(name="period_from") private Instant from;
    @Column(name="period_to") private Instant to;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version @Column(nullable=false) private Long version;
    protected SavedReportEntity() {}
    static SavedReportEntity from(SavedReport v){var e=new SavedReportEntity();e.id=v.getId();e.tenantId=v.getTenantId();e.ownerId=v.getOwnerId();e.name=v.getName();e.reportType=v.getReportType();e.eventId=v.getEventId();e.from=v.getFrom();e.to=v.getTo();e.createdAt=v.getCreatedAt();e.updatedAt=v.getUpdatedAt();e.version=v.getVersion();return e;}
    SavedReport domain(){return new SavedReport(id,tenantId,ownerId,name,reportType,eventId,from,to,createdAt,updatedAt,version);}
}
