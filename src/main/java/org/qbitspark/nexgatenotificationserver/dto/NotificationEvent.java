package org.qbitspark.nexgatenotificationserver.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.qbitspark.nexgatenotificationserver.enums.NotificationChannel;
import org.qbitspark.nexgatenotificationserver.enums.NotificationPriority;
import org.qbitspark.nexgatenotificationserver.enums.NotificationType;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    private NotificationType type;
    private List<Recipient> recipients;
    private List<NotificationChannel> channels;
    private NotificationPriority priority;
    private Map<String, Object> data;
}