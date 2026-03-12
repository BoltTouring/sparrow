package com.sparrowwallet.sparrow.nostr;

import com.sparrowwallet.drongo.nip05.Nip17Sender;
import com.sparrowwallet.drongo.nip05.SilentPaymentNotification;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.silentpayments.SilentPayment;
import com.sparrowwallet.drongo.wallet.Payment;
import com.sparrowwallet.drongo.wallet.WalletTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.List;

import static com.sparrowwallet.drongo.protocol.ScriptType.P2TR;

/**
 * After a transaction containing Silent Payment outputs is broadcast,
 * this service sends NIP-17 encrypted notifications to each recipient
 * so they can claim their UTXOs without scanning the blockchain.
 */
public class SpNotificationSender {
    private static final Logger log = LoggerFactory.getLogger(SpNotificationSender.class);

    /**
     * Check a broadcast transaction for SP outputs that have pending notifications,
     * and send NIP-17 DMs to the recipients.
     *
     * @param walletTransaction the wallet transaction that was broadcast
     * @param transaction the raw broadcast transaction
     */
    public static void sendNotifications(WalletTransaction walletTransaction, Transaction transaction) {
        if(walletTransaction == null || transaction == null) {
            return;
        }

        List<Payment> payments = walletTransaction.getPayments();
        if(payments == null) {
            return;
        }

        String txid = transaction.getTxId().toString();
        boolean anySent = false;

        for(Payment payment : payments) {
            if(payment instanceof SilentPayment silentPayment) {
                String spAddressStr = silentPayment.getSilentPaymentAddress().getAddress();
                SpNotificationCache.SpNotificationEntry entry = SpNotificationCache.consume(spAddressStr);

                if(entry != null && payment.getAddress() != null) {
                    // Find the vout for this payment's derived address
                    int vout = findOutputIndex(transaction, payment);
                    if(vout >= 0) {
                        SilentPaymentNotification notification = new SilentPaymentNotification(txid, vout, payment.getAmount());

                        // Send in background thread to not block the UI
                        String recipientPubKey = entry.recipientPubKeyHex();
                        Thread.ofVirtual().start(() -> {
                            try {
                                // Use a random sender key — recipient verifies the UTXO on-chain, not the DM sender
                                byte[] senderKey = new byte[32];
                                new SecureRandom().nextBytes(senderKey);
                                // Ensure valid private key
                                while(new java.math.BigInteger(1, senderKey).compareTo(
                                        com.sparrowwallet.drongo.crypto.ECKey.CURVE.getCurve().getOrder()) >= 0
                                        || new java.math.BigInteger(1, senderKey).equals(java.math.BigInteger.ZERO)) {
                                    new SecureRandom().nextBytes(senderKey);
                                }

                                Nip17Sender sender = new Nip17Sender(senderKey);
                                sender.sendNotification(recipientPubKey, notification, null);
                                log.info("Sent SP notification for " + txid + ":" + vout +
                                        " (" + notification.amount() + " sats) to " +
                                        recipientPubKey.substring(0, 8) + "...");
                            } catch(Exception e) {
                                log.error("Failed to send SP notification for " + txid + ":" + vout, e);
                            }
                        });

                        anySent = true;
                    }
                }
            }
        }

        if(anySent) {
            SpNotificationCache.cleanup();
        }
    }

    /**
     * Find the output index of a payment's address in the transaction.
     */
    private static int findOutputIndex(Transaction transaction, Payment payment) {
        if(payment.getAddress() == null) return -1;
        byte[] targetScript = payment.getAddress().getOutputScript();

        for(int i = 0; i < transaction.getOutputs().size(); i++) {
            TransactionOutput output = transaction.getOutputs().get(i);
            if(java.util.Arrays.equals(output.getScriptBytes(), targetScript)) {
                return i;
            }
        }

        return -1;
    }
}
