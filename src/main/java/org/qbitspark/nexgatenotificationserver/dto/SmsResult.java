package org.qbitspark.nexgatenotificationserver.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsResult {
    private boolean success;
    private String messageId;
    private String provider;
    private String errorMessage;
    private String recipient;
    private int statusCode;
}