package com.eesara.drive.audit;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="service_audit_logs") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AuditLog {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 private Long apiKeyId; private Long userId;
 @Column(nullable=false,length=80) private String action;
 @Column(length=36) private String targetUuid;
 @Column(nullable=false,length=20) private String outcome;
 @Column(length=64) private String requestIp;
 @Column(length=500) private String detail;
 @Column(nullable=false,updatable=false) private LocalDateTime createdAt;
 @PrePersist void create(){createdAt=LocalDateTime.now();}
}
