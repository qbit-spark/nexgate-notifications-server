package org.qbitspark.nexgatenotificationserver.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InAppResult {
    private boolean success;
    private String notificationId;
    private String errorMessage;
    private int statusCode;
    private String recipient;
}