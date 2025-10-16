package org.qbitspark.nexgatenotificationserver.service.channel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.qbitspark.nexgatenotificationserver.dto.EmailResult;
import org.qbitspark.nexgatenotificationserver.enums.NotificationType;
import org.qbitspark.nexgatenotificationserver.service.template.TemplateService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final TemplateService templateService;

    public EmailResult send(NotificationType type, String to, Map<String, Object> data) {
        // Step 1: Pick template based on notification type
        String templateName = getTemplateForType(type);
        String subject = getSubjectForType(type);

        log.info("📧 Preparing email: type={}, template={}, to={}", type, templateName, to);

        // Step 2: Formulate message using template
        String htmlBody = templateService.renderEmailTemplate(templateName, data);

        // Step 3: Mock send (will be replaced with actual provider later)
        String messageId = UUID.randomUUID().toString();

        log.info("📧 [MOCK] Email prepared:");
        log.info("   To: {}", to);
        log.info("   Subject: {}", subject);
        log.info("   Template: {}", templateName);
        log.info("   Data: {}", data);
        log.info("   MessageId: {}", messageId);
        log.info("   Body length: {} chars", htmlBody.length());

        // Simulate success
        return EmailResult.builder()
                .success(true)
                .messageId(messageId)
                .provider("mock-email")
                .build();
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
            case WELCOME_EMAIL -> "welcome_email";
            case PROMOTIONAL_OFFER -> "promotional_offer";
        };
    }

    private String getSubjectForType(NotificationType type) {
        return switch (type) {
            case ORDER_CONFIRMATION -> "Order Confirmation - Your Order Has Been Received";
            case ORDER_SHIPPED -> "Your Order Has Been Shipped";
            case ORDER_DELIVERED -> "Your Order Has Been Delivered";
            case PAYMENT_RECEIVED -> "Payment Received Successfully";
            case PAYMENT_FAILURE -> "Payment Failed - Action Required";
            case CART_ABANDONMENT -> "You Left Items in Your Cart";
            case CHECKOUT_EXPIRY -> "Your Checkout Session Has Expired";
            case WALLET_BALANCE_UPDATE -> "Your Wallet Balance Has Been Updated";
            case INSTALLMENT_DUE -> "Installment Payment Due Reminder";
            case SHOP_NEW_ORDER -> "New Order Received in Your Shop";
            case SHOP_LOW_INVENTORY -> "Low Inventory Alert";
            case GROUP_PURCHASE_COMPLETE -> "Group Purchase Completed Successfully";
            case WELCOME_EMAIL -> "Welcome to Nexgate!";
            case PROMOTIONAL_OFFER -> "Special Offer Just for You";
        };
    }
}