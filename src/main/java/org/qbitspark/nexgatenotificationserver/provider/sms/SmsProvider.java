package org.qbitspark.nexgatenotificationserver.provider.sms;

import org.qbitspark.nexgatenotificationserver.dto.SmsResult;

import java.util.List;
import java.util.Map;

public interface SmsProvider {
    String getProviderName();
    boolean isAvailable();

    // Single SMS
    SmsResult sendSms(String to, String message, String senderId);

    // Batch SMS (multiple recipients, same message)
    List<SmsResult> sendSmsBatch(List<String> recipients, String message, String senderId);

    // Batch SMS (multiple recipients, different messages)
    List<SmsResult> sendSmsBatchCustom(Map<String, String> recipientMessages, String senderId);
}