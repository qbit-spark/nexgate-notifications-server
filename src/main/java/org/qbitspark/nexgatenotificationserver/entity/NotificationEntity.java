package org.qbitspark.nexgatenotificationserver.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.qbitspark.nexgatenotificationserver.enums.NotificationChannel;
import org.qbitspark.nexgatenotificationserver.enums.NotificationStatus;
import org.qbitspark.nexgatenotificationserver.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private String correlationId;
    private String userId;
    private String recipientEmail;
    private String recipientPhone;
    private String recipientName;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    @ElementCollection
    @Enumerated(EnumType.STRING)
    private List<NotificationChannel> channels;

    @Enumerated(EnumType.STRING)
    private NotificationStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> templateData;

    private LocalDateTime createdAt;
    private LocalDateTime sentAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}