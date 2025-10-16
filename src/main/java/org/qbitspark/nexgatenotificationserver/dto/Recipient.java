package org.qbitspark.nexgatenotificationserver.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recipient {
    private String userId;
    private String email;
    private String phone;
    private String name;
    private String language;
}