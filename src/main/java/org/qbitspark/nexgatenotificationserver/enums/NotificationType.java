package org.qbitspark.nexgatenotificationserver.enums;

public enum NotificationType {
    // Order
    ORDER_CONFIRMATION,
    ORDER_SHIPPED,
    ORDER_DELIVERED,

    // Payment
    PAYMENT_RECEIVED,
    PAYMENT_FAILURE,

    // Cart / Checkout
    CART_ABANDONMENT,
    CHECKOUT_EXPIRY,

    // Wallet
    WALLET_BALANCE_UPDATE,

    // Installment
    INSTALLMENT_DUE,

    // Shop
    SHOP_NEW_ORDER,
    SHOP_LOW_INVENTORY,

    // Group Purchase
    GROUP_PURCHASE_COMPLETE,
    GROUP_PURCHASE_CREATED,
    GROUP_MEMBER_JOINED,
    GROUP_SEATS_TRANSFERRED,

    // User
    WELCOME_EMAIL,
    PROMOTIONAL_OFFER,

    // ── Event ──────────────────────────────────────────────────
    /**
     * Fired once per booking — sent to the BUYER.
     * Carries all tickets as a bundled PDF attachment.
     */
    EVENT_BOOKING_CONFIRMED,

    /**
     * Fired per attendee — sent to each individual ATTENDEE.
     * Registered users: PDF + "View in app" button.
     * Non-registered:   PDF + "View in app" button + "Create account" button.
     */
    EVENT_ATTENDEE_TICKET_ISSUED,

    /**
     * Fired once per booking — sent to the EVENT ORGANIZER.
     * Summary notification: who booked, how many tickets, total amount.
     */
    EVENT_ORGANIZER_NEW_BOOKING
}