package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.nip05.NostrContact;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.List;

/**
 * Event fired when the user selects one or more Nostr contacts for payment in the Contacts tab.
 * SendController listens for this to create payment tabs with pre-filled Silent Payment addresses.
 */
public class NostrContactPayEvent {
    private final Wallet wallet;
    private final List<NostrContact> contacts;

    public NostrContactPayEvent(Wallet wallet, List<NostrContact> contacts) {
        this.wallet = wallet;
        this.contacts = contacts;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public List<NostrContact> getContacts() {
        return contacts;
    }
}
