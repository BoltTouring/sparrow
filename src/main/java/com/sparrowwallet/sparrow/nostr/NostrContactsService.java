package com.sparrowwallet.sparrow.nostr;

import com.sparrowwallet.drongo.nip05.NostrContact;
import com.sparrowwallet.drongo.nip05.NostrContactResolver;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

import java.util.List;

/**
 * JavaFX Service for asynchronously resolving Nostr contacts.
 */
public class NostrContactsService extends Service<List<NostrContact>> {
    private final String npubOrNip05;

    public NostrContactsService(String npubOrNip05) {
        this.npubOrNip05 = npubOrNip05;
    }

    @Override
    protected Task<List<NostrContact>> createTask() {
        return new Task<>() {
            @Override
            protected List<NostrContact> call() throws Exception {
                NostrContactResolver resolver = new NostrContactResolver(npubOrNip05);
                return resolver.resolveContacts();
            }
        };
    }
}
