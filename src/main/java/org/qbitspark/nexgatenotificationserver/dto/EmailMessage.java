package org.qbitspark.nexgatenotificationserver.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailMessage {
    private String to;
    private String from;
    private String subject;
    private String htmlBody;
    private String textBody;
}