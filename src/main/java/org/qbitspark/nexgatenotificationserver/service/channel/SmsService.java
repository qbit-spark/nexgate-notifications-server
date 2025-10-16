package org.qbitspark.nexgatenotificationserver.service.channel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.qbitspark.nexgatenotificationserver.dto.SmsResult;
import org.qbitspark.nexgatenotificationserver.enums.NotificationType;
import org.qbitspark.nexgatenotificationserver.provider.sms.SmsProvider;
import org.qbitspark.nexgatenotificationserver.service.template.TemplateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {

    private final TemplateService templateService;
    private final SmsProvider smsProvider;

    @Value("${sms.sender-id:Nexgate}")
    private String defaultSenderId;

    public boolean send(NotificationType type, String phone, Map<String, Object> data) {
        // Step 1: Pick template based on notification type
        String templateName = getTemplateForType(type);

        log.info("📱 Preparing SMS: type={}, template={}, to={}", type, templateName, phone);

        // Step 2: Formulate message using template
        String smsBody = templateService.renderSmsTemplate(templateName, data);

        String senderId = defaultSenderId;

        log.info("📱 SMS ready to send:");
        log.info("   To: {}", phone);
        log.info("   From: {}", senderId);
        log.info("   Type: {}", type);
        log.info("   Template: {}", templateName);
        log.info("   Message length: {} chars", smsBody.length());
        log.info("   Provider: {}", smsProvider.getProviderName());

        // Step 4: Send via provider
        SmsResult result = smsProvider.sendSms(phone, smsBody, senderId);

        if (result.isSuccess()) {
            log.info("✅ SMS sent successfully: messageId={}, provider={}",
                    result.getMessageId(), result.getProvider());
        } else {
            log.error("❌ SMS failed: error={}, provider={}",
                    result.getErrorMessage(), result.getProvider());
        }

        return result.isSuccess();
    }

    private String getTemplateForType(NotificationType type) {
        return switch (type) {
            case ORDER_CONFIRMATION -> "order_confirmation";
            case ORDER_SHIPPED -> "order_shipped";
            case ORDER_DELIVERED -> "order_delivered";
            case PAYMENT_RECEIVED -> "payment_received";
            case PAYMENT_FAILURE -> "payment_failure";
            case CART_ABANDONMENT -> "cart_abandonment";
            case CHECKOUT_EXPIRY -> "checkout_expiry";
            case WALLET_BALANCE_UPDATE -> "wallet_balance_update";
            case INSTALLMENT_DUE -> "installment_due";
            case SHOP_NEW_ORDER -> "shop_new_order";
            case SHOP_LOW_INVENTORY -> "shop_low_inventory";
            case GROUP_PURCHASE_COMPLETE -> "group_purchase_complete";
            case WELCOME_EMAIL -> "welcome_message";
            case PROMOTIONAL_OFFER -> "promotional_offer";
        };
    }

}