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
        log.info("📬 Preparing in-app notification: type={}, userId={}", type, userId);

        UUID userUuid;
        try {
            userUuid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            log.error("❌ Invalid userId for in-app notification: {}", userId);
            return false;
        }

        UUID shopId = extractShopId(data);
        String serviceId = extractServiceId(type, data);
        String serviceType = extractServiceType(type);
        String title = getTitleForType(type);
        String message = generateMessage(type, data);
        String priority = getPriorityForType(type);

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

        ApiResponse<Map> response = serviceAuthClient.postWithAuth(
                "/api/v1/notifications/in-app", request, Map.class);

        if (response.isSuccess()) {
            log.info("✅ In-app notification sent: userId={}", userId);
            return true;
        } else {
            log.error("❌ In-app failed: userId={}, error={}", userId, response.getErrorMessage());
            return false;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  SERVICE TYPE
    // ──────────────────────────────────────────────────────────────────────────

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
            case EVENT_BOOKING_CONFIRMED, EVENT_ATTENDEE_TICKET_ISSUED, EVENT_ORGANIZER_NEW_BOOKING -> "EVENT";
        };
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  TITLE
    // ──────────────────────────────────────────────────────────────────────────

    private String getTitleForType(NotificationType type) {
        return switch (type) {
            case ORDER_CONFIRMATION    -> "Order Confirmed";
            case ORDER_SHIPPED         -> "Order Shipped";
            case ORDER_DELIVERED       -> "Order Delivered";
            case PAYMENT_RECEIVED      -> "Payment Received";
            case PAYMENT_FAILURE       -> "Payment Failed";
            case CART_ABANDONMENT      -> "Cart Reminder";
            case CHECKOUT_EXPIRY       -> "Checkout Expired";
            case WALLET_BALANCE_UPDATE -> "Wallet Updated";
            case INSTALLMENT_DUE       -> "Payment Due";
            case SHOP_NEW_ORDER        -> "New Order";
            case SHOP_LOW_INVENTORY    -> "Low Stock Alert";
            case GROUP_PURCHASE_COMPLETE   -> "Group Buy Success";
            case GROUP_PURCHASE_CREATED    -> "New Group Started";
            case GROUP_MEMBER_JOINED       -> "Member Joined";
            case GROUP_SEATS_TRANSFERRED   -> "Seats Transferred";
            case WELCOME_EMAIL         -> "Welcome";
            case PROMOTIONAL_OFFER     -> "Special Offer";
            case EVENT_BOOKING_CONFIRMED      -> "🎫 Booking Confirmed!";
            case EVENT_ATTENDEE_TICKET_ISSUED -> "🎟️ Your Ticket is Ready!";
            case EVENT_ORGANIZER_NEW_BOOKING  -> "🔔 New Booking Received!";
        };
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  MESSAGE
    // ──────────────────────────────────────────────────────────────────────────

    private String generateMessage(NotificationType type, Map<String, Object> data) {
        return switch (type) {
            case ORDER_CONFIRMATION    -> formatOrderConfirmation(data);
            case ORDER_SHIPPED         -> formatOrderShipped(data);
            case ORDER_DELIVERED       -> formatOrderDelivered(data);
            case PAYMENT_RECEIVED      -> formatPaymentReceived(data);
            case PAYMENT_FAILURE       -> formatPaymentFailure(data);
            case CART_ABANDONMENT      -> "You left items in your cart";
            case CHECKOUT_EXPIRY       -> "Your checkout session has expired";
            case WALLET_BALANCE_UPDATE -> formatWalletUpdate(data);
            case INSTALLMENT_DUE       -> formatInstallmentDue(data);
            case SHOP_NEW_ORDER        -> formatShopNewOrder(data);
            case SHOP_LOW_INVENTORY    -> formatLowInventory(data);
            case GROUP_PURCHASE_COMPLETE   -> formatGroupPurchaseComplete(data);
            case GROUP_PURCHASE_CREATED    -> formatGroupPurchaseCreated(data);
            case GROUP_MEMBER_JOINED       -> formatGroupMemberJoined(data);
            case GROUP_SEATS_TRANSFERRED   -> formatGroupSeatsTransferred(data);
            case WELCOME_EMAIL         -> formatWelcome(data);
            case PROMOTIONAL_OFFER     -> formatPromotionalOffer(data);
            case EVENT_BOOKING_CONFIRMED      -> formatEventBookingConfirmed(data);
            case EVENT_ATTENDEE_TICKET_ISSUED -> formatEventAttendeeTicket(data);
            case EVENT_ORGANIZER_NEW_BOOKING  -> formatEventOrganizerBooking(data);
        };
    }

    // ── Event message formatters ───────────────────────────────────────────────

    private String formatEventBookingConfirmed(Map<String, Object> data) {
        Map<?, ?> booking = castMap(data.get("booking"));
        Map<?, ?> event   = castMap(data.get("event"));
        String bookingId  = booking != null ? str(booking, "id")          : "—";
        String eventName  = event   != null ? str(event,   "name")        : "your event";
        String count      = booking != null ? str(booking, "ticketCount") : "your";
        return String.format("Booking %s confirmed! %s ticket(s) for \"%s\". Check your email for the PDF tickets.",
                bookingId, count, eventName);
    }

    private String formatEventAttendeeTicket(Map<String, Object> data) {
        Map<?, ?> event      = castMap(data.get("event"));
        Map<?, ?> currentTicket = castMap(data.get("currentTicket"));
        Map<?, ?> booking    = castMap(data.get("booking"));
        String eventName     = event        != null ? str(event,        "name")      : "your event";
        String ticketId      = currentTicket != null ? str(currentTicket, "ticketId") : "—";
        String bookingRef    = booking      != null ? str(booking,      "id")        : "—";
        return String.format("Your ticket %s for \"%s\" is ready. Booking ref: %s",
                ticketId, eventName, bookingRef);
    }

    // ── Priority ───────────────────────────────────────────────────────────────

    private String getPriorityForType(NotificationType type) {
        return switch (type) {
            case PAYMENT_FAILURE, INSTALLMENT_DUE, SHOP_LOW_INVENTORY -> "HIGH";
            case EVENT_BOOKING_CONFIRMED, EVENT_ATTENDEE_TICKET_ISSUED -> "HIGH";
            case EVENT_ORGANIZER_NEW_BOOKING -> "NORMAL";
            case ORDER_CONFIRMATION, PAYMENT_RECEIVED, SHOP_NEW_ORDER  -> "NORMAL";
            case ORDER_SHIPPED, ORDER_DELIVERED, WALLET_BALANCE_UPDATE  -> "NORMAL";
            case CART_ABANDONMENT, CHECKOUT_EXPIRY, PROMOTIONAL_OFFER,
                 GROUP_MEMBER_JOINED, GROUP_SEATS_TRANSFERRED           -> "LOW";
            case WELCOME_EMAIL, GROUP_PURCHASE_COMPLETE,
                 GROUP_PURCHASE_CREATED                                 -> "NORMAL";
        };
    }

    // ── Service ID extractor ───────────────────────────────────────────────────

    private String extractServiceId(NotificationType type, Map<String, Object> data) {
        return switch (type) {
            case EVENT_BOOKING_CONFIRMED, EVENT_ATTENDEE_TICKET_ISSUED, EVENT_ORGANIZER_NEW_BOOKING -> {
                Map<?, ?> booking = castMap(data.get("booking"));
                yield booking != null ? str(booking, "id") : "EVENT-" + System.currentTimeMillis();
            }
            default -> {
                Object orderId = data.get("orderId");
                if (orderId != null) yield orderId.toString();
                Object paymentId = data.get("paymentId");
                if (paymentId != null) yield paymentId.toString();
                yield "GENERAL-" + System.currentTimeMillis();
            }
        };
    }

    // ── Existing formatters (unchanged) ───────────────────────────────────────

    private UUID extractShopId(Map<String, Object> data) {
        Object shopObj = data.get("shop");
        if (shopObj instanceof Map) {
            Object shopIdObj = ((Map<?, ?>) shopObj).get("id");
            if (shopIdObj != null) {
                try { return UUID.fromString(shopIdObj.toString()); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        return null;
    }

    private String formatOrderConfirmation(Map<String, Object> data) {
        return String.format("Your order %s has been confirmed", str(data, "orderId"));
    }
    private String formatOrderShipped(Map<String, Object> data) {
        return String.format("Your order %s has been shipped", str(data, "orderId"));
    }
    private String formatOrderDelivered(Map<String, Object> data) {
        return String.format("Your order %s has been delivered", str(data, "orderId"));
    }
    private String formatPaymentReceived(Map<String, Object> data) {
        return String.format("Payment of %s received successfully", str(data, "amount"));
    }
    private String formatPaymentFailure(Map<String, Object> data) {
        return String.format("Payment failed for order %s", str(data, "orderId"));
    }
    private String formatWalletUpdate(Map<String, Object> data) {
        Map<?, ?> wallet = castMap(data.get("wallet"));
        String balance = wallet != null ? str(wallet, "currentBalance") : "0.00";
        return String.format("Your wallet balance is now %s", balance);
    }
    private String formatInstallmentDue(Map<String, Object> data) {
        Map<?, ?> inst = castMap(data.get("installment"));
        String amount = inst != null ? str(inst, "amount") : "0.00";
        return String.format("Installment payment of %s is due", amount);
    }
    private String formatShopNewOrder(Map<String, Object> data) {
        return String.format("New order %s received", str(data, "orderId"));
    }
    private String formatLowInventory(Map<String, Object> data) {
        Map<?, ?> product = castMap(data.get("product"));
        String name = product != null ? str(product, "name") : "Product";
        return String.format("%s inventory is running low", name);
    }
    private String formatGroupPurchaseComplete(Map<String, Object> data) {
        Map<?, ?> group = castMap(data.get("group"));
        return String.format("Group %s complete!", group != null ? str(group, "code") : "");
    }
    private String formatGroupPurchaseCreated(Map<String, Object> data) {
        Map<?, ?> group = castMap(data.get("group"));
        return String.format("New group %s started", group != null ? str(group, "code") : "");
    }
    private String formatGroupMemberJoined(Map<String, Object> data) {
        Map<?, ?> newMember = castMap(data.get("newMember"));
        String name = newMember != null ? str(newMember, "name") : "Someone";
        return String.format("%s joined the group", name);
    }
    private String formatGroupSeatsTransferred(Map<String, Object> data) {
        Map<?, ?> transfer = castMap(data.get("transfer"));
        String qty = transfer != null ? str(transfer, "quantity") : "0";
        return String.format("%s seats transferred successfully", qty);
    }
    private String formatWelcome(Map<String, Object> data) {
        Map<?, ?> customer = castMap(data.get("customer"));
        String name = customer != null ? str(customer, "name") : "there";
        return String.format("Welcome to Nexgate, %s!", name);
    }
    private String formatPromotionalOffer(Map<String, Object> data) {
        return String.valueOf(data.getOrDefault("offer", "Special offer available now"));
    }

    private String formatEventOrganizerBooking(Map<String, Object> data) {
        Map<?, ?> booking = castMap(data.get("booking"));
        Map<?, ?> buyer   = castMap(data.get("buyer"));
        String ref       = booking != null ? str(booking, "id")          : "N/A";
        String buyerName = buyer   != null ? str(buyer,   "name")        : "A customer";
        String tickets   = booking != null ? str(booking, "ticketCount") : "some";
        return String.format("%s booked %s ticket(s) — ref %s", buyerName, tickets, ref);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object o) {
        if (o instanceof Map) return (Map<String, Object>) o;
        return null;
    }

    private String str(Map<?, ?> map, String key) {
        if (map == null) return "";
        Object v = map.get(key);
        return v != null ? v.toString() : "";
    }
}