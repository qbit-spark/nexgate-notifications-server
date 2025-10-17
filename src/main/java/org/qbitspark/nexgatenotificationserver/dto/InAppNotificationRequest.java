package org.qbitspark.nexgatenotificationserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InAppNotificationRequest {

    @JsonProperty("userId")
    private UUID userId;

    @JsonProperty("shopId")
    private UUID shopId;

    @JsonProperty("serviceId")
    private String serviceId;

    @JsonProperty("serviceType")
    private String serviceType;

    @JsonProperty("title")
    private String title;

    @JsonProperty("message")
    private String message;

    @JsonProperty("type")
    private String type;

    @JsonProperty("priority")
    private String priority;

    @JsonProperty("data")
    private Map<String, Object> data;
}