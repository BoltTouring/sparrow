package com.sparrowwallet.sparrow.nostr;

import com.sparrowwallet.drongo.address.Address;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-level cache mapping derived SP output addresses to recipient Nostr pubkeys.
 * Set during payment creation (when we know the contact), read after broadcast
 * (when we need to send the NIP-17 notification).
 */
public class SpNotificationCache {
    private static final Map<String, SpNotificationEntry> pendingNotifications = new ConcurrentHashMap<>();

    private SpNotificationCache() {}

    /**
     * Register a pending SP notification. Called when a contact payment is being created.
     *
     * @param outputAddress the derived P2TR output address string
     * @param recipientPubKeyHex the recipient's 64-char hex Nostr pubkey
     */
    public static void register(String outputAddress, String recipientPubKeyHex) {
        pendingNotifications.put(outputAddress, new SpNotificationEntry(recipientPubKeyHex, System.currentTimeMillis()));
    }

    /**
     * Retrieve and remove a pending notification for the given output address.
     * Returns null if no notification is pending.
     */
    public static SpNotificationEntry consume(String outputAddress) {
        return pendingNotifications.remove(outputAddress);
    }

    /**
     * Clear stale entries older than 1 hour.
     */
    public static void cleanup() {
        long cutoff = System.currentTimeMillis() - 3600_000;
        pendingNotifications.entrySet().removeIf(e -> e.getValue().timestamp() < cutoff);
    }

    public record SpNotificationEntry(String recipientPubKeyHex, long timestamp) {}
}
