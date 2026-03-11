package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.nip05.NostrContact;
import com.sparrowwallet.drongo.wallet.Wallet;

/**
 * Event fired when a user selects a Nostr contact for payment in the Contacts tab.
 * The PaymentController listens for this to pre-fill the Silent Payment address in the Send tab.
 */
public class NostrContactPayEvent {
    private final Wallet wallet;
    private final NostrContact contact;

    public NostrContactPayEvent(Wallet wallet, NostrContact contact) {
        this.wallet = wallet;
        this.contact = contact;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public NostrContact getContact() {
        return contact;
    }
}
