package org.qbitspark.nexgatenotificationserver.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("type")
    private NotificationType type;

    @JsonProperty("recipients")
    private List<Recipient> recipients;

    @JsonProperty("channels")
    private List<NotificationChannel> channels;

    @JsonProperty("priority")
    private NotificationPriority priority;

    @JsonProperty("data")
    private Map<String, Object> data;
}