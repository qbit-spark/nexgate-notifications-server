package org.qbitspark.nexgatenotificationserver.provider.email;


import org.qbitspark.nexgatenotificationserver.dto.EmailMessage;
import org.qbitspark.nexgatenotificationserver.dto.EmailResult;

public interface EmailProvider {
    String getProviderName();
    boolean isAvailable();
    EmailResult sendEmail(EmailMessage message);
}