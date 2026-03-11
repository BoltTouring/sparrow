package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.nip05.NostrContact;
import com.sparrowwallet.drongo.nip05.NostrContactCache;
import com.sparrowwallet.drongo.nip05.NostrContactResolver;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.CopyableTextField;
import com.sparrowwallet.sparrow.event.SendActionEvent;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for the Nostr Contacts wallet tab.
 * Resolves a user's Nostr follow list and displays contacts with Silent Payment addresses,
 * allowing direct payment initiation from the contacts view.
 */
public class ContactsController extends WalletFormController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(ContactsController.class);

    @FXML
    private CopyableTextField npubInput;

    @FXML
    private Button resolveButton;

    @FXML
    private Button refreshButton;

    @FXML
    private ProgressIndicator resolveProgress;

    @FXML
    private Label statusLabel;

    @FXML
    private TextField searchField;

    @FXML
    private CheckBox spOnlyCheckbox;

    @FXML
    private Label contactCountLabel;

    @FXML
    private ListView<NostrContact> contactsList;

    @FXML
    private HBox actionBar;

    @FXML
    private Button copySpButton;

    @FXML
    private Button payButton;

    private final ObservableList<NostrContact> allContacts = FXCollections.observableArrayList();
    private FilteredList<NostrContact> filteredContacts;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    @Override
    public void initializeView() {
        // Set up filtered list
        filteredContacts = new FilteredList<>(allContacts, _ -> true);
        contactsList.setItems(filteredContacts);
        contactsList.setCellFactory(_ -> new ContactsTabCell());

        // Search filter
        searchField.textProperty().addListener((_, _, _) -> updateFilter());
        spOnlyCheckbox.selectedProperty().addListener((_, _, _) -> updateFilter());

        // Selection listener — enable/disable action buttons
        contactsList.getSelectionModel().selectedItemProperty().addListener((_, _, contact) -> {
            boolean hasSp = contact != null && contact.hasSilentPaymentAddress();
            payButton.setDisable(!hasSp);
            copySpButton.setDisable(!hasSp);
        });

        // Double-click to pay
        contactsList.setOnMouseClicked(event -> {
            if(event.getClickCount() == 2) {
                NostrContact selected = contactsList.getSelectionModel().getSelectedItem();
                if(selected != null && selected.hasSilentPaymentAddress()) {
                    payContact(selected);
                }
            }
        });

        // Enter key to resolve
        npubInput.setOnAction(_ -> resolveContacts());

        // Placeholder for empty list
        contactsList.setPlaceholder(new Label("No contacts loaded.\nEnter your npub or NIP-05 address above and click Load Contacts."));

        // Auto-populate from cache if contacts were already resolved in another wallet tab
        String lastInput = NostrContactCache.getLastInput();
        if(lastInput != null) {
            npubInput.setText(lastInput);
            List<NostrContact> cached = NostrContactCache.getContacts(lastInput);
            if(cached != null && !cached.isEmpty()) {
                allContacts.setAll(cached);
                updateCountLabel();
                long spCount = cached.stream().filter(NostrContact::hasSilentPaymentAddress).count();
                statusLabel.setText("Loaded " + cached.size() + " contacts (" + spCount + " with Silent Payment address)");
                refreshButton.setVisible(true);
                refreshButton.setManaged(true);
            }
        }
    }

    private void updateFilter() {
        String search = searchField.getText();
        boolean spOnly = spOnlyCheckbox.isSelected();

        filteredContacts.setPredicate(contact -> {
            if(spOnly && !contact.hasSilentPaymentAddress()) {
                return false;
            }
            if(search == null || search.isBlank()) {
                return true;
            }
            String lower = search.toLowerCase();
            if(contact.displayName().toLowerCase().contains(lower)) {
                return true;
            }
            if(contact.nip05() != null && contact.nip05().toLowerCase().contains(lower)) {
                return true;
            }
            if(contact.pubkey().toLowerCase().startsWith(lower)) {
                return true;
            }
            return false;
        });

        updateCountLabel();
    }

    private void updateCountLabel() {
        int total = allContacts.size();
        int filtered = filteredContacts.size();
        long spCount = filteredContacts.stream().filter(NostrContact::hasSilentPaymentAddress).count();

        if(total == 0) {
            contactCountLabel.setText("");
        } else if(filtered == total) {
            contactCountLabel.setText(total + " contacts (" + spCount + " with SP)");
        } else {
            contactCountLabel.setText(filtered + " of " + total + " shown (" + spCount + " with SP)");
        }
    }

    @FXML
    public void resolveContacts() {
        String input = npubInput.getText();
        if(input == null || input.trim().isEmpty()) {
            statusLabel.setText("Please enter an npub or NIP-05 address");
            return;
        }

        input = input.trim();

        // Check cache first (skip on refresh — user explicitly wants fresh data)
        List<NostrContact> cached = NostrContactCache.getContacts(input);
        if(cached != null && !cached.isEmpty() && allContacts.isEmpty()) {
            allContacts.setAll(cached);
            updateCountLabel();
            long spCount = cached.stream().filter(NostrContact::hasSilentPaymentAddress).count();
            statusLabel.setText("Loaded " + cached.size() + " contacts from cache (" + spCount + " with Silent Payment address)");
            refreshButton.setVisible(true);
            refreshButton.setManaged(true);
            return;
        }

        resolveProgress.setVisible(true);
        resolveButton.setDisable(true);
        statusLabel.setText("Resolving contacts from Nostr relays...");
        allContacts.clear();
        searchField.clear();
        contactCountLabel.setText("");

        final String resolveInput = input;

        NostrContactsResolveService service = new NostrContactsResolveService(resolveInput);
        service.setOnSucceeded(_ -> {
            resolveProgress.setVisible(false);
            resolveButton.setDisable(false);

            List<NostrContact> contacts = service.getValue();
            allContacts.setAll(contacts);
            updateCountLabel();

            // Store in shared cache for other wallet tabs
            NostrContactCache.putContacts(resolveInput, contacts);

            long spCount = contacts.stream().filter(NostrContact::hasSilentPaymentAddress).count();
            statusLabel.setText("Loaded " + contacts.size() + " contacts (" + spCount + " with Silent Payment address)");

            // Show refresh button after first successful load
            refreshButton.setVisible(true);
            refreshButton.setManaged(true);
        });

        service.setOnFailed(failEvent -> {
            resolveProgress.setVisible(false);
            resolveButton.setDisable(false);

            String errorMsg = "Resolution failed";
            if(failEvent.getSource().getException() != null) {
                errorMsg = failEvent.getSource().getException().getMessage();
            }
            statusLabel.setText("Error: " + errorMsg);
            log.error("Nostr contact resolution failed for " + resolveInput, failEvent.getSource().getException());
        });

        service.start();
    }

    @FXML
    public void paySelectedContact() {
        NostrContact contact = contactsList.getSelectionModel().getSelectedItem();
        if(contact != null && contact.hasSilentPaymentAddress()) {
            payContact(contact);
        }
    }

    @FXML
    public void copySpAddress() {
        NostrContact contact = contactsList.getSelectionModel().getSelectedItem();
        if(contact != null && contact.hasSilentPaymentAddress()) {
            ClipboardContent content = new ClipboardContent();
            content.putString(contact.spAddress().getAddress());
            Clipboard.getSystemClipboard().setContent(content);
            statusLabel.setText("Copied SP address for " + contact.displayName());
        }
    }

    private void payContact(NostrContact contact) {
        // Switch to Send tab and pre-fill the SP address.
        // PauseTransition ensures the Send tab's PaymentController is initialized
        // before receiving the NostrContactPayEvent.
        EventManager.get().post(new SendActionEvent(getWalletForm().getWallet(), Collections.emptyList(), true));

        PauseTransition pause = new PauseTransition(Duration.millis(150));
        pause.setOnFinished(_ -> {
            EventManager.get().post(new NostrContactPayEvent(getWalletForm().getWallet(), contact));
        });
        pause.play();
    }

    // ===== Custom ListCell for the Contacts tab =====

    private static class ContactsTabCell extends ListCell<NostrContact> {
        @Override
        protected void updateItem(NostrContact contact, boolean empty) {
            super.updateItem(contact, empty);

            if(empty || contact == null) {
                setText(null);
                setGraphic(null);
                getStyleClass().removeAll("no-sp-cell");
            } else {
                BorderPane pane = new BorderPane();
                pane.setPadding(new Insets(6, 10, 6, 10));
                pane.getStyleClass().add("nostr-contact-cell");

                // Left: name, NIP-05, SP address
                VBox leftBox = new VBox(2);
                leftBox.setAlignment(Pos.CENTER_LEFT);

                Label nameLabel = new Label(contact.displayName());
                nameLabel.getStyleClass().add("contact-name");
                leftBox.getChildren().add(nameLabel);

                if(contact.nip05() != null && !contact.nip05().isEmpty()) {
                    Label nip05Label = new Label(contact.nip05());
                    nip05Label.getStyleClass().add("contact-nip05");
                    leftBox.getChildren().add(nip05Label);
                } else {
                    Label pubkeyLabel = new Label(contact.getShortPubkey());
                    pubkeyLabel.getStyleClass().add("contact-nip05");
                    leftBox.getChildren().add(pubkeyLabel);
                }

                if(contact.hasSilentPaymentAddress()) {
                    String spAddr = contact.spAddress().getAddress();
                    String truncated = spAddr.substring(0, Math.min(16, spAddr.length())) + "..." + spAddr.substring(Math.max(0, spAddr.length() - 6));
                    Label spLabel = new Label(truncated);
                    spLabel.getStyleClass().add("contact-sp-address");
                    leftBox.getChildren().add(spLabel);
                }

                pane.setLeft(leftBox);

                // Right: badges
                HBox rightBox = new HBox(6);
                rightBox.setAlignment(Pos.CENTER_RIGHT);

                if(contact.hasSilentPaymentAddress()) {
                    Label spBadge = new Label("\u20BF");
                    spBadge.getStyleClass().add("sp-badge");
                    spBadge.setTooltip(new Tooltip("Silent Payment: " + contact.spAddress().getAddress()));
                    rightBox.getChildren().add(spBadge);

                    if(contact.signatureVerified()) {
                        Label verifiedBadge = new Label("\u2713");
                        verifiedBadge.getStyleClass().add("verified-badge");
                        verifiedBadge.setTooltip(new Tooltip("Nostr event signature verified"));
                        rightBox.getChildren().add(verifiedBadge);
                    }
                }

                pane.setRight(rightBox);

                // Dim contacts without SP
                if(!contact.hasSilentPaymentAddress()) {
                    pane.getStyleClass().add("no-sp-cell");
                }

                // Right-click context menu
                ContextMenu contextMenu = new ContextMenu();

                MenuItem copyPubkeyItem = new MenuItem("Copy Nostr Pubkey");
                copyPubkeyItem.setOnAction(_ -> {
                    ClipboardContent cc = new ClipboardContent();
                    cc.putString(contact.pubkey());
                    Clipboard.getSystemClipboard().setContent(cc);
                });
                contextMenu.getItems().add(copyPubkeyItem);

                if(contact.nip05() != null && !contact.nip05().isEmpty()) {
                    MenuItem copyNip05Item = new MenuItem("Copy NIP-05");
                    copyNip05Item.setOnAction(_ -> {
                        ClipboardContent cc = new ClipboardContent();
                        cc.putString(contact.nip05());
                        Clipboard.getSystemClipboard().setContent(cc);
                    });
                    contextMenu.getItems().add(copyNip05Item);
                }

                if(contact.hasSilentPaymentAddress()) {
                    MenuItem copySpItem = new MenuItem("Copy SP Address");
                    copySpItem.setOnAction(_ -> {
                        ClipboardContent cc = new ClipboardContent();
                        cc.putString(contact.spAddress().getAddress());
                        Clipboard.getSystemClipboard().setContent(cc);
                    });
                    contextMenu.getItems().add(copySpItem);
                }

                setContextMenu(contextMenu);

                setText(null);
                setGraphic(pane);
            }
        }
    }

    // ===== Background service for resolving contacts =====

    private static class NostrContactsResolveService extends javafx.concurrent.Service<List<NostrContact>> {
        private final String npubOrNip05;

        public NostrContactsResolveService(String npubOrNip05) {
            this.npubOrNip05 = npubOrNip05;
        }

        @Override
        protected javafx.concurrent.Task<List<NostrContact>> createTask() {
            return new javafx.concurrent.Task<>() {
                @Override
                protected List<NostrContact> call() throws Exception {
                    NostrContactResolver resolver = new NostrContactResolver(npubOrNip05);
                    return resolver.resolveContacts();
                }
            };
        }
    }
}
