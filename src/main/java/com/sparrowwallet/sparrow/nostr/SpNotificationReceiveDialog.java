package com.sparrowwallet.sparrow.nostr;

import com.sparrowwallet.drongo.nip05.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.io.Storage;
import javafx.collections.FXCollections;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class SpNotificationReceiveDialog extends Dialog<SilentPaymentNotification> {
    private static final Logger log = LoggerFactory.getLogger(SpNotificationReceiveDialog.class);

    private final TabPane keyTabs;
    private final TextField bunkerUriField;
    private final PasswordField savedKeyPasswordField;
    private final PasswordField nsecField;
    private final CheckBox saveNsecCheckbox;
    private final PasswordField savePasswordField;
    private final Button checkButton;
    private final ProgressIndicator progress;
    private final Label statusLabel;
    private final ListView<SilentPaymentNotification> notificationList;
    private final Nip46BunkerClient nostrConnectClient;

    public SpNotificationReceiveDialog() {
        final DialogPane dialogPane = getDialogPane();
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        AppServices.onEscapePressed(dialogPane.getScene(), this::close);

        dialogPane.setPrefWidth(680);
        dialogPane.setPrefHeight(620);
        dialogPane.getStylesheets().add(AppServices.class.getResource("app.css").toExternalForm());
        AppServices.moveToActiveWindowScreen(this);

        VBox content = new VBox(10);
        content.setPadding(new Insets(15));

        // Initialize early — referenced by delete button lambda
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");
        statusLabel.setWrapText(true);

        // Title
        HBox titleBox = new HBox(8);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label("Incoming SP Notifications");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Label nostrLabel = new Label("N");
        nostrLabel.setStyle("-fx-text-fill: #8B5CF6; -fx-font-weight: bold; -fx-font-size: 22px;");
        titleBox.getChildren().addAll(titleLabel, nostrLabel);

        Label descLabel = new Label("Check for Silent Payment notifications sent to you via Nostr encrypted DMs.");
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        // Key method tabs
        keyTabs = new TabPane();
        keyTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        keyTabs.setMinHeight(170);
        keyTabs.setPrefHeight(200);

        // Tab 1: Nostr Connect
        Tab bunkerTab = new Tab("Nostr Connect");
        VBox bunkerContent = new VBox(8);
        bunkerContent.setPadding(new Insets(10));

        // Generate nostrconnect URI for nsec.app — keep client alive for reuse
        nostrConnectClient = Nip46BunkerClient.forNostrConnect(null);
        String connectUri = nostrConnectClient.getNostrConnectUri();

        Label bunkerDesc = new Label("Copy this connection string and paste it into nsec.app, Amber, or your bunker app:");
        bunkerDesc.setWrapText(true);
        bunkerDesc.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        HBox connectUriBox = new HBox(6);
        connectUriBox.setAlignment(Pos.CENTER_LEFT);
        TextField connectUriField = new TextField(connectUri);
        connectUriField.setEditable(false);
        connectUriField.setStyle("-fx-font-size: 10px;");
        HBox.setHgrow(connectUriField, Priority.ALWAYS);
        Button copyUriButton = new Button("Copy");
        copyUriButton.setOnAction(_ -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(connectUriField.getText());
            Clipboard.getSystemClipboard().setContent(cc);
            copyUriButton.setText("Copied!");
            new java.util.Timer().schedule(new java.util.TimerTask() {
                @Override public void run() { javafx.application.Platform.runLater(() -> copyUriButton.setText("Copy")); }
            }, 2000);
        });
        connectUriBox.getChildren().addAll(connectUriField, copyUriButton);

        // Also allow pasting a bunker:// URI
        bunkerUriField = new TextField();
        bunkerUriField.setPromptText("Or paste a bunker:// URI here");
        bunkerUriField.setStyle("-fx-font-size: 10px;");

        bunkerContent.getChildren().addAll(bunkerDesc, connectUriBox, bunkerUriField);
        bunkerTab.setContent(bunkerContent);

        // Tab 2: Saved Key
        Tab savedKeyTab = new Tab("Saved Key");
        VBox savedKeyContent = new VBox(6);
        savedKeyContent.setPadding(new Insets(10));
        boolean hasSavedKey = NostrKeyStore.exists(Storage.getSparrowDir());
        if(hasSavedKey) {
            Label savedDesc = new Label("An encrypted Nostr key is stored on this device. Enter your password to unlock it.");
            savedDesc.setWrapText(true);
            savedDesc.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
            savedKeyPasswordField = new PasswordField();
            savedKeyPasswordField.setPromptText("Password to decrypt stored nsec");
            Button deleteKeyButton = new Button("Delete Saved Key");
            deleteKeyButton.setStyle("-fx-font-size: 10px; -fx-text-fill: #dc2626;");
            deleteKeyButton.setOnAction(_ -> {
                if(NostrKeyStore.delete(Storage.getSparrowDir())) {
                    statusLabel.setText("Saved key deleted");
                    savedKeyTab.setDisable(true);
                }
            });
            savedKeyContent.getChildren().addAll(savedDesc, savedKeyPasswordField, deleteKeyButton);
        } else {
            savedKeyPasswordField = new PasswordField();
            Label noKeyLabel = new Label("No saved key found. Use the Manual tab to enter your nsec with the option to save it.");
            noKeyLabel.setWrapText(true);
            noKeyLabel.setStyle("-fx-text-fill: #888;");
            savedKeyContent.getChildren().add(noKeyLabel);
            savedKeyTab.setDisable(true);
        }
        savedKeyTab.setContent(savedKeyContent);

        // Tab 3: Manual nsec
        Tab manualTab = new Tab("Manual nsec");
        VBox manualContent = new VBox(6);
        manualContent.setPadding(new Insets(10));
        Label manualDesc = new Label("Enter your nsec directly. Optionally save it encrypted for future sessions.");
        manualDesc.setWrapText(true);
        manualDesc.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
        nsecField = new PasswordField();
        nsecField.setPromptText("nsec1...");
        HBox saveRow = new HBox(8);
        saveRow.setAlignment(Pos.CENTER_LEFT);
        saveNsecCheckbox = new CheckBox("Save encrypted for next time");
        savePasswordField = new PasswordField();
        savePasswordField.setPromptText("Encryption password");
        savePasswordField.setDisable(true);
        savePasswordField.setMaxWidth(200);
        saveNsecCheckbox.selectedProperty().addListener((_, _, selected) -> savePasswordField.setDisable(!selected));
        saveRow.getChildren().addAll(saveNsecCheckbox, savePasswordField);
        manualContent.getChildren().addAll(manualDesc, nsecField, saveRow);
        manualTab.setContent(manualContent);

        keyTabs.getTabs().addAll(bunkerTab, savedKeyTab, manualTab);
        if(hasSavedKey) {
            keyTabs.getSelectionModel().select(savedKeyTab);
        }

        // Check button row
        HBox checkBox = new HBox(8);
        checkBox.setAlignment(Pos.CENTER_LEFT);
        checkButton = new Button("Check for Payments");
        checkButton.setStyle("-fx-font-weight: bold;");
        progress = new ProgressIndicator();
        progress.setMaxHeight(18);
        progress.setMaxWidth(18);
        progress.setVisible(false);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        checkBox.getChildren().addAll(checkButton, progress, statusLabel);

        // Notification list
        notificationList = new ListView<>();
        notificationList.setCellFactory(_ -> new NotificationCell());
        notificationList.setPlaceholder(new Label("No notifications found yet."));
        notificationList.setPrefHeight(200);
        VBox.setVgrow(notificationList, Priority.ALWAYS);

        content.getChildren().addAll(titleBox, descLabel, keyTabs, checkBox, new Separator(), notificationList);
        dialogPane.setContent(content);

        // Dialog buttons
        ButtonType closeType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType copyType = new ButtonType("Copy TXID", ButtonBar.ButtonData.LEFT);
        dialogPane.getButtonTypes().addAll(copyType, closeType);

        Button copyButton = (Button)dialogPane.lookupButton(copyType);
        copyButton.setDisable(true);
        notificationList.getSelectionModel().selectedItemProperty().addListener((_, _, n) -> copyButton.setDisable(n == null));
        copyButton.setOnAction(e -> {
            SilentPaymentNotification selected = notificationList.getSelectionModel().getSelectedItem();
            if(selected != null) {
                ClipboardContent cc = new ClipboardContent();
                cc.putString(selected.txid());
                Clipboard.getSystemClipboard().setContent(cc);
                statusLabel.setText("Copied txid: " + selected.txid().substring(0, 16) + "...");
            }
            e.consume();
        });

        checkButton.setOnAction(_ -> pollNotifications());
        setResultConverter(_ -> null);
    }

    private void pollNotifications() {
        Tab selectedTab = keyTabs.getSelectionModel().getSelectedItem();
        String tabText = selectedTab.getText();

        if(tabText.contains("Nostr Connect")) {
            pollViaBunker();
        } else if(tabText.contains("Saved")) {
            pollViaSavedKey();
        } else {
            pollViaManualNsec();
        }
    }

    private void pollViaBunker() {
        String bunkerUri = bunkerUriField.getText().trim();

        Nip46BunkerClient bunker;
        if(!bunkerUri.isEmpty() && bunkerUri.startsWith("bunker://")) {
            // bunker:// flow
            try {
                bunker = Nip46BunkerClient.fromUri(bunkerUri);
            } catch(Exception e) {
                statusLabel.setText("Invalid bunker URI: " + e.getMessage());
                return;
            }
        } else {
            // nostrconnect:// flow — reuse the client that generated the URI
            bunker = nostrConnectClient;
        }

        checkButton.setDisable(true);
        progress.setVisible(true);
        statusLabel.setText("Waiting for bunker app to connect — paste the connection string and approve...");

        BunkerPollService service = new BunkerPollService(bunker);
        service.setOnSucceeded(_ -> {
            progress.setVisible(false);
            checkButton.setDisable(false);
            List<SilentPaymentNotification> notifications = service.getValue();
            notificationList.setItems(FXCollections.observableList(notifications));
            if(notifications.isEmpty()) {
                statusLabel.setText("No Silent Payment notifications found");
            } else {
                long totalSats = notifications.stream().mapToLong(SilentPaymentNotification::amount).sum();
                statusLabel.setText("Found " + notifications.size() + " notification(s) totaling " + String.format("%,d", totalSats) + " sats");
            }
        });
        service.setOnFailed(_ -> {
            progress.setVisible(false);
            checkButton.setDisable(false);
            statusLabel.setText("Bunker error: " + (service.getException() != null ? service.getException().getMessage() : "Unknown"));
        });
        service.start();
    }

    private void pollViaSavedKey() {
        String password = savedKeyPasswordField.getText();
        if(password.isEmpty()) {
            statusLabel.setText("Enter your password");
            return;
        }
        try {
            Optional<String> nsec = NostrKeyStore.load(Storage.getSparrowDir(), password);
            if(nsec.isEmpty()) {
                statusLabel.setText("No saved key found");
                return;
            }
            byte[] privKey = NostrKeyStore.decodeNsec(nsec.get());
            startPolling(privKey);
        } catch(Exception e) {
            statusLabel.setText("Decryption failed - wrong password?");
        }
    }

    private void pollViaManualNsec() {
        String nsec = nsecField.getText().trim();
        if(nsec.isEmpty()) {
            statusLabel.setText("Enter your nsec");
            return;
        }
        byte[] privKey;
        try {
            privKey = NostrKeyStore.decodeNsec(nsec);
        } catch(Exception e) {
            statusLabel.setText("Invalid nsec: " + e.getMessage());
            return;
        }
        if(saveNsecCheckbox.isSelected()) {
            String password = savePasswordField.getText();
            if(password.isEmpty()) {
                statusLabel.setText("Enter a password to save the key");
                return;
            }
            try {
                NostrKeyStore.save(Storage.getSparrowDir(), nsec, password);
                statusLabel.setText("Key saved. Polling relays...");
            } catch(Exception e) {
                log.error("Failed to save nsec", e);
            }
        }
        startPolling(privKey);
    }

    private void startPolling(byte[] privKey) {
        checkButton.setDisable(true);
        progress.setVisible(true);
        statusLabel.setText("Polling Nostr relays...");

        PollService service = new PollService(privKey);
        service.setOnSucceeded(_ -> {
            progress.setVisible(false);
            checkButton.setDisable(false);
            List<SilentPaymentNotification> notifications = service.getValue();
            notificationList.setItems(FXCollections.observableList(notifications));
            if(notifications.isEmpty()) {
                statusLabel.setText("No Silent Payment notifications found");
            } else {
                long totalSats = notifications.stream().mapToLong(SilentPaymentNotification::amount).sum();
                statusLabel.setText("Found " + notifications.size() + " notification(s) totaling " + String.format("%,d", totalSats) + " sats");
            }
        });
        service.setOnFailed(_ -> {
            progress.setVisible(false);
            checkButton.setDisable(false);
            statusLabel.setText("Error: " + (service.getException() != null ? service.getException().getMessage() : "Unknown"));
        });
        service.start();
    }

    private static class PollService extends Service<List<SilentPaymentNotification>> {
        private final byte[] privKey;
        PollService(byte[] privKey) { this.privKey = privKey; }

        @Override
        protected Task<List<SilentPaymentNotification>> createTask() {
            return new Task<>() {
                @Override
                protected List<SilentPaymentNotification> call() {
                    Nip17Receiver receiver = new Nip17Receiver(privKey);
                    long since = (System.currentTimeMillis() / 1000) - (30 * 86400);
                    return receiver.pollNotifications(since, null);
                }
            };
        }
    }

    private static class BunkerPollService extends Service<List<SilentPaymentNotification>> {
        private final Nip46BunkerClient bunker;
        BunkerPollService(Nip46BunkerClient bunker) { this.bunker = bunker; }

        @Override
        protected Task<List<SilentPaymentNotification>> createTask() {
            return new Task<>() {
                @Override
                protected List<SilentPaymentNotification> call() throws Exception {
                    bunker.connect();
                    String pubkey = bunker.getPublicKey();

                    // Create receiver that delegates decryption to the bunker
                    Nip17Receiver receiver = new Nip17Receiver(pubkey, (senderPubKeyHex, ciphertext) ->
                            bunker.decrypt(senderPubKeyHex, ciphertext));

                    long since = (System.currentTimeMillis() / 1000) - (30 * 86400);
                    return receiver.pollNotifications(since, null);
                }
            };
        }
    }

    private static class NotificationCell extends ListCell<SilentPaymentNotification> {
        @Override
        protected void updateItem(SilentPaymentNotification notif, boolean empty) {
            super.updateItem(notif, empty);
            if(empty || notif == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox box = new VBox(3);
                box.setPadding(new Insets(6, 8, 6, 8));
                HBox topRow = new HBox(8);
                topRow.setAlignment(Pos.CENTER_LEFT);
                Label amountLabel = new Label(String.format("%,d sats", notif.amount()));
                amountLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
                Label outputLabel = new Label("Output #" + notif.vout());
                outputLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");
                topRow.getChildren().addAll(amountLabel, outputLabel);
                Label txidLabel = new Label("TX: " + notif.txid().substring(0, 20) + "..." + notif.txid().substring(56));
                txidLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 11px; -fx-text-fill: #555;");
                box.getChildren().addAll(topRow, txidLabel);
                setGraphic(box);
                setText(null);
            }
        }
    }
}
