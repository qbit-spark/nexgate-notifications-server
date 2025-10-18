package org.qbitspark.nexgatenotificationserver.service.channel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.qbitspark.nexgatenotificationserver.api.payload.ApiResponse;
import org.qbitspark.nexgatenotificationserver.dto.InAppNotificationRequest;
import org.qbitspark.nexgatenotificationserver.enums.NotificationType;
import org.qbitspark.nexgatenotificationserver.security.ServiceAuthClient;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InAppService {

    private final ServiceAuthClient serviceAuthClient;

    public boolean send(NotificationType type, String userId, Map<String, Object> data) {
        log.info("Preparing in-app notification: type={}, userId={}", type, userId);

        // Extract metadata from data
        UUID userUuid = UUID.fromString(userId);
        UUID shopId = extractShopId(data);
        String serviceId = extractServiceId(data);
        String serviceType = extractServiceType(type);

        // Generate title and message
        String title = getTitleForType(type);
        String message = generateMessage(type, data);
        String priority = getPriorityForType(type);

        // Build request
        InAppNotificationRequest request = InAppNotificationRequest.builder()
                .userId(userUuid)
                .shopId(shopId)
                .serviceId(serviceId)
                .serviceType(serviceType)
                .title(title)
                .message(message)
                .type(type.name())
                .priority(priority)
                .data(data)
                .build();

        log.info("Sending in-app notification to parent server: userId={}, serviceType={}", userId, serviceType);

        // Send to parent server with authentication
        ApiResponse<Map> response = serviceAuthClient.postWithAuth(
                "/api/v1/notifications/in-app",
                request,
                Map.class
        );

        if (response.isSuccess()) {
            log.info("In-app notification sent successfully: userId={}", userId);
            return true;
        } else {
            log.error("Failed to send in-app notification: userId={}, error={}",
                    userId, response.getErrorMessage());
            return false;
        }
    }

    private UUID extractShopId(Map<String, Object> data) {
        Object shopObj = data.get("shop");
        if (shopObj instanceof Map) {
            Object shopIdObj = ((Map<?, ?>) shopObj).get("id");
            if (shopIdObj != null) {
                return UUID.fromString(shopIdObj.toString());
            }
        }
        return null;
    }

    private String extractServiceId(Map<String, Object> data) {
        // Try different keys for service ID
        Object orderId = data.get("orderId");
        if (orderId != null) return orderId.toString();

        Object paymentId = data.get("paymentId");
        if (paymentId != null) return paymentId.toString();

        Object cartId = data.get("cartId");
        if (cartId != null) return cartId.toString();

        // Default fallback
        return "GENERAL-" + System.currentTimeMillis();
    }

    private String extractServiceType(NotificationType type) {
        return switch (type) {
            case ORDER_CONFIRMATION, ORDER_SHIPPED, ORDER_DELIVERED -> "ORDER";
            case PAYMENT_RECEIVED, PAYMENT_FAILURE -> "PAYMENT";
            case INSTALLMENT_DUE -> "INSTALLMENT_AGREEMENT";
            case CART_ABANDONMENT -> "CART";
            case CHECKOUT_EXPIRY -> "CHECKOUT";
            case WALLET_BALANCE_UPDATE -> "WALLET";
            case SHOP_NEW_ORDER, SHOP_LOW_INVENTORY -> "SHOP";
            case GROUP_PURCHASE_COMPLETE, GROUP_PURCHASE_CREATED,
                 GROUP_MEMBER_JOINED, GROUP_SEATS_TRANSFERRED -> "GROUP_PURCHASE";
            case PROMOTIONAL_OFFER -> "PROMOTIONAL";
            case WELCOME_EMAIL -> "USER_ACCOUNT";
        };
    }

    private String getTitleForType(NotificationType type) {
        return switch (type) {
            case ORDER_CONFIRMATION -> "Order Confirmed";
            case ORDER_SHIPPED -> "Order Shipped";
            case ORDER_DELIVERED -> "Order Delivered";
            case PAYMENT_RECEIVED -> "Payment Received";
            case PAYMENT_FAILURE -> "Payment Failed";
            case CART_ABANDONMENT -> "Cart Reminder";
            case CHECKOUT_EXPIRY -> "Checkout Expired";
            case WALLET_BALANCE_UPDATE -> "Wallet Updated";
            case INSTALLMENT_DUE -> "Payment Due";
            case SHOP_NEW_ORDER -> "New Order";
            case SHOP_LOW_INVENTORY -> "Low Stock Alert";
            case GROUP_PURCHASE_COMPLETE -> "Group Buy Success";
            case GROUP_PURCHASE_CREATED -> "New Group Started";
            case GROUP_MEMBER_JOINED -> "Member Joined";
            case GROUP_SEATS_TRANSFERRED -> "Seats Transferred";
            case WELCOME_EMAIL -> "Welcome";
            case PROMOTIONAL_OFFER -> "Special Offer";
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
            case CHECKOUT_EXPIRY -> "Your checkout session has expired";
            case WALLET_BALANCE_UPDATE -> formatWalletUpdate(data);
            case INSTALLMENT_DUE -> formatInstallmentDue(data);
            case SHOP_NEW_ORDER -> formatShopNewOrder(data);
            case SHOP_LOW_INVENTORY -> formatLowInventory(data);
            case GROUP_PURCHASE_COMPLETE -> formatGroupPurchaseComplete(data);
            case GROUP_PURCHASE_CREATED -> formatGroupPurchaseCreated(data);
            case GROUP_MEMBER_JOINED -> formatGroupMemberJoined(data);
            case GROUP_SEATS_TRANSFERRED -> formatGroupSeatsTransferred(data);
            case WELCOME_EMAIL -> formatWelcome(data);
            case PROMOTIONAL_OFFER -> formatPromotionalOffer(data);
        };
    }

    private String getPriorityForType(NotificationType type) {
        return switch (type) {
            case PAYMENT_FAILURE, INSTALLMENT_DUE, SHOP_LOW_INVENTORY -> "HIGH";
            case ORDER_CONFIRMATION, PAYMENT_RECEIVED, SHOP_NEW_ORDER -> "NORMAL";
            case ORDER_SHIPPED, ORDER_DELIVERED, WALLET_BALANCE_UPDATE -> "NORMAL";
            case CART_ABANDONMENT, CHECKOUT_EXPIRY, PROMOTIONAL_OFFER,
                 GROUP_MEMBER_JOINED, GROUP_SEATS_TRANSFERRED -> "LOW";
            case WELCOME_EMAIL, GROUP_PURCHASE_COMPLETE, GROUP_PURCHASE_CREATED -> "NORMAL";
        };
    }

    // Message formatters
    private String formatOrderConfirmation(Map<String, Object> data) {
        String orderId = String.valueOf(data.get("orderId"));
        return String.format("Your order %s has been confirmed", orderId);
    }

    private String formatOrderShipped(Map<String, Object> data) {
        String orderId = String.valueOf(data.get("orderId"));
        return String.format("Your order %s has been shipped", orderId);
    }

    private String formatOrderDelivered(Map<String, Object> data) {
        String orderId = String.valueOf(data.get("orderId"));
        return String.format("Your order %s has been delivered", orderId);
    }

    private String formatPaymentReceived(Map<String, Object> data) {
        String amount = String.valueOf(data.get("amount"));
        return String.format("Payment of $%s received successfully", amount);
    }

    private String formatPaymentFailure(Map<String, Object> data) {
        String orderId = String.valueOf(data.get("orderId"));
        return String.format("Payment failed for order %s", orderId);
    }

    private String formatCartAbandonment(Map<String, Object> data) {
        return "You left items in your cart";
    }

    private String formatWalletUpdate(Map<String, Object> data) {
        Object walletObj = data.get("wallet");
        String balance = walletObj instanceof Map ?
                String.valueOf(((Map<?, ?>) walletObj).get("currentBalance")) : "0.00";
        return String.format("Your wallet balance is now $%s", balance);
    }

    private String formatInstallmentDue(Map<String, Object> data) {
        Object installmentObj = data.get("installment");
        String amount = installmentObj instanceof Map ?
                String.valueOf(((Map<?, ?>) installmentObj).get("amount")) : "0.00";
        return String.format("Installment payment of $%s is due", amount);
    }

    private String formatShopNewOrder(Map<String, Object> data) {
        String orderId = String.valueOf(data.get("orderId"));
        return String.format("New order %s received", orderId);
    }

    private String formatLowInventory(Map<String, Object> data) {
        Object productObj = data.get("product");
        String productName = productObj instanceof Map ?
                String.valueOf(((Map<?, ?>) productObj).get("name")) : "Product";
        return String.format("%s inventory is running low", productName);
    }

    private String formatGroupPurchase(Map<String, Object> data) {
        return "Group purchase completed successfully";
    }

    private String formatWelcome(Map<String, Object> data) {
        Object customerObj = data.get("customer");
        String name = customerObj instanceof Map ?
                String.valueOf(((Map<?, ?>) customerObj).get("name")) : "there";
        return String.format("Welcome to Nexgate, %s!", name);
    }

    private String formatPromotionalOffer(Map<String, Object> data) {
        String offer = String.valueOf(data.getOrDefault("offer", "Special offer"));
        return String.format("%s available now", offer);
    }

    // ========================================
// GROUP PURCHASE MESSAGE FORMATTERS
// ========================================

    private String formatGroupPurchaseComplete(Map<String, Object> data) {
        Map<?, ?> group = (Map<?, ?>) data.get("group");
        Map<?, ?> product = (Map<?, ?>) data.get("product");

        String groupCode = group != null ? String.valueOf(group.get("code")) : "group";
        String productName = product != null ? String.valueOf(product.get("name")) : "product";

        return String.format("Group %s complete! Your %s order has been created",
                groupCode, productName);
    }

    private String formatGroupPurchaseCreated(Map<String, Object> data) {
        Map<?, ?> group = (Map<?, ?>) data.get("group");
        Map<?, ?> product = (Map<?, ?>) data.get("product");

        String groupCode = group != null ? String.valueOf(group.get("code")) : "group";
        String productName = product != null ? String.valueOf(product.get("name")) : "product";
        String seatsOccupied = group != null ? String.valueOf(group.get("seatsOccupied")) : "0";
        String totalSeats = group != null ? String.valueOf(group.get("totalSeats")) : "0";

        return String.format("New group %s started for %s (%s/%s seats)",
                groupCode, productName, seatsOccupied, totalSeats);
    }

    private String formatGroupMemberJoined(Map<String, Object> data) {
        Map<?, ?> group = (Map<?, ?>) data.get("group");
        Map<?, ?> newMember = (Map<?, ?>) data.get("newMember");

        String groupCode = group != null ? String.valueOf(group.get("code")) : "group";
        String memberName = newMember != null ? String.valueOf(newMember.get("name")) : "Someone";
        String seatsRemaining = group != null ? String.valueOf(group.get("seatsRemaining")) : "0";

        return String.format("%s joined group %s. %s seats remaining",
                memberName, groupCode, seatsRemaining);
    }

    private String formatGroupSeatsTransferred(Map<String, Object> data) {
        Map<?, ?> transfer = (Map<?, ?>) data.get("transfer");
        Map<?, ?> target = (Map<?, ?>) data.get("target");

        String quantity = transfer != null ? String.valueOf(transfer.get("quantity")) : "0";
        String targetCode = target != null ? String.valueOf(target.get("groupCode")) : "group";

        return String.format("%s seats transferred to group %s", quantity, targetCode);
    }
}