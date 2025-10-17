package org.qbitspark.nexgatenotificationserver.service.channel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.qbitspark.nexgatenotificationserver.dto.PushResult;
import org.qbitspark.nexgatenotificationserver.enums.NotificationType;
import org.qbitspark.nexgatenotificationserver.provider.push.PushProvider;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushService {

    private final PushProvider pushProvider;

    public boolean send(NotificationType type, String userId, Map<String, Object> data) {
        log.info("🔔 Preparing push notification: type={}, userId={}", type, userId);

        // Step 1: Generate title and message
        String title = getTitleForType(type);
        String message = generateMessage(type, data);
        int priority = getPriorityForType(type);

        log.info("🔔 Push notification ready:");
        log.info("   UserId: {}", userId);
        log.info("   Title: {}", title);
        log.info("   Message length: {} chars", message.length());
        log.info("   Priority: {}", priority);
        log.info("   Provider: {}", pushProvider.getProviderName());

        // Step 2: Send via provider
        PushResult result = pushProvider.sendPush(userId, title, message, priority);

        if (result.isSuccess()) {
            log.info("✅ Push notification sent successfully: messageId={}, provider={}",
                    result.getMessageId(), result.getProvider());
        } else {
            log.error("❌ Push notification failed: error={}, provider={}",
                    result.getErrorMessage(), result.getProvider());
        }

        return result.isSuccess();
    }

    private String getTitleForType(NotificationType type) {
        return switch (type) {
            case ORDER_CONFIRMATION -> "🛒 Order Confirmed!";
            case ORDER_SHIPPED -> "📦 Order Shipped!";
            case ORDER_DELIVERED -> "✅ Order Delivered!";
            case PAYMENT_RECEIVED -> "💳 Payment Received";
            case PAYMENT_FAILURE -> "⚠️ Payment Failed";
            case CART_ABANDONMENT -> "🛍️ Cart Reminder";
            case CHECKOUT_EXPIRY -> "⏰ Checkout Expired";
            case WALLET_BALANCE_UPDATE -> "💰 Wallet Updated";
            case INSTALLMENT_DUE -> "📅 Payment Due";
            case SHOP_NEW_ORDER -> "🔔 New Order!";
            case SHOP_LOW_INVENTORY -> "⚠️ Low Stock Alert";
            case GROUP_PURCHASE_COMPLETE -> "🎉 Group Buy Success!";
            case WELCOME_EMAIL -> "👋 Welcome to Nexgate!";
            case PROMOTIONAL_OFFER -> "🎁 Special Offer!";
        };
    }

    private String generateMessage(NotificationType type, Map<String, Object> data) {
        return switch (type) {
            case ORDER_CONFIRMATION -> formatOrderConfirmation(data);
            case ORDER_SHIPPED -> formatOrderShipped(data);
            case ORDER_DELIVERED -> formatOrderDelivered(data);
            case PAYMENT_RECEIVED -> formatPaymentReceived(data);
            case PAYMENT_FAILURE -> formatPaymentFailure(data);
            case CART_ABANDONMENT -> formatCartAbandonment(data);
            case CHECKOUT_EXPIRY -> formatCheckoutExpiry(data);
            case WALLET_BALANCE_UPDATE -> formatWalletUpdate(data);
            case INSTALLMENT_DUE -> formatInstallmentDue(data);
            case SHOP_NEW_ORDER -> formatShopNewOrder(data);
            case SHOP_LOW_INVENTORY -> formatLowInventory(data);
            case GROUP_PURCHASE_COMPLETE -> formatGroupPurchase(data);
            case WELCOME_EMAIL -> formatWelcome(data);
            case PROMOTIONAL_OFFER -> formatPromotionalOffer(data);
        };
    }

//    private int getPriorityForType(NotificationType type) {
//        return switch (type) {
//            case PAYMENT_FAILURE, INSTALLMENT_DUE, SHOP_LOW_INVENTORY -> 8; // High priority
//            case ORDER_CONFIRMATION, PAYMENT_RECEIVED, SHOP_NEW_ORDER -> 6; // Medium-high
//            case ORDER_SHIPPED, ORDER_DELIVERED, WALLET_BALANCE_UPDATE -> 5; // Medium
//            case CART_ABANDONMENT, CHECKOUT_EXPIRY, PROMOTIONAL_OFFER -> 3; // Low-medium
//            case WELCOME_EMAIL, GROUP_PURCHASE_COMPLETE -> 4; // Normal
//        };
//    }

    private int getPriorityForType(NotificationType type) {
        return switch (type) {
            case PAYMENT_FAILURE, INSTALLMENT_DUE, SHOP_LOW_INVENTORY, ORDER_CONFIRMATION, PAYMENT_RECEIVED, SHOP_NEW_ORDER -> 10;
            case ORDER_SHIPPED, ORDER_DELIVERED, WALLET_BALANCE_UPDATE -> 9;
            case CART_ABANDONMENT, CHECKOUT_EXPIRY, PROMOTIONAL_OFFER -> 3;
            case WELCOME_EMAIL, GROUP_PURCHASE_COMPLETE -> 4; // Normal
        };
    }

    // Message formatters for each notification type
    private String formatOrderConfirmation(Map<String, Object> data) {
        String orderId = String.valueOf(data.get("orderId"));
        Object paymentObj = data.get("payment");
        String amount = paymentObj instanceof Map ?
                String.valueOf(((Map<?, ?>) paymentObj).get("amount")) : "0.00";

        return String.format("Your order %s has been confirmed! Total: $%s. " +
                "We'll notify you when it ships.", orderId, amount);
    }

    private String formatOrderShipped(Map<String, Object> data) {
        String orderId = String.valueOf(data.get("orderId"));
        return String.format("Great news! Your order %s has been shipped and is on its way!", orderId);
    }

    private String formatOrderDelivered(Map<String, Object> data) {
        String orderId = String.valueOf(data.get("orderId"));
        return String.format("Your order %s has been delivered! Enjoy your purchase!", orderId);
    }

    private String formatPaymentReceived(Map<String, Object> data) {
        String amount = String.valueOf(data.get("amount"));
        String orderId = String.valueOf(data.get("orderId"));
        return String.format("Payment of $%s received successfully for order %s.", amount, orderId);
    }

    private String formatPaymentFailure(Map<String, Object> data) {
        String orderId = String.valueOf(data.get("orderId"));
        return String.format("Payment failed for order %s. Please update your payment method.", orderId);
    }

    private String formatCartAbandonment(Map<String, Object> data) {
        Object cartObj = data.get("cart");
        String total = cartObj instanceof Map ?
                String.valueOf(((Map<?, ?>) cartObj).get("total")) : "0.00";
        return String.format("You left items worth $%s in your cart. Complete your purchase now!", total);
    }

    private String formatCheckoutExpiry(Map<String, Object> data) {
        return "Your checkout session has expired. Please try again to complete your purchase.";
    }

    private String formatWalletUpdate(Map<String, Object> data) {
        Object walletObj = data.get("wallet");
        String balance = walletObj instanceof Map ?
                String.valueOf(((Map<?, ?>) walletObj).get("currentBalance")) : "0.00";
        return String.format("Your wallet has been updated. New balance: %s", balance);
    }

    private String formatInstallmentDue(Map<String, Object> data) {
        Object installmentObj = data.get("installment");
        String amount = installmentObj instanceof Map ?
                String.valueOf(((Map<?, ?>) installmentObj).get("amount")) : "0.00";
        String dueDate = installmentObj instanceof Map ?
                String.valueOf(((Map<?, ?>) installmentObj).get("dueDate")) : "soon";
        return String.format("Installment payment of %s is due on %s.", amount, dueDate);
    }

    private String formatShopNewOrder(Map<String, Object> data) {
        String orderId = String.valueOf(data.get("orderId"));
        Object customerObj = data.get("customer");
        String customerName = customerObj instanceof Map ?
                String.valueOf(((Map<?, ?>) customerObj).get("name")) : "Customer";
        return String.format("New order %s received from %s!", orderId, customerName);
    }

    private String formatLowInventory(Map<String, Object> data) {
        Object productObj = data.get("product");
        String productName = productObj instanceof Map ?
                String.valueOf(((Map<?, ?>) productObj).get("name")) : "Product";
        return String.format("Low stock alert! %s inventory is running low.", productName);
    }

    private String formatGroupPurchase(Map<String, Object> data) {
        String finalPrice = String.valueOf(data.get("finalPrice"));
        return String.format("Group purchase completed! Your final price: $%s", finalPrice);
    }

    private String formatWelcome(Map<String, Object> data) {
        Object customerObj = data.get("customer");
        String name = customerObj instanceof Map ?
                String.valueOf(((Map<?, ?>) customerObj).get("name")) : "there";
        return String.format("Welcome to Nexgate, %s! Start shopping and discover amazing deals.", name);
    }

    private String formatPromotionalOffer(Map<String, Object> data) {
        String offer = String.valueOf(data.getOrDefault("offer", "Special offer"));
        return String.format("%s available now! Don't miss out on exclusive deals.", offer);
    }
}