package com.sparrowwallet.sparrow.nostr;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.nip05.Nip17Receiver;
import com.sparrowwallet.drongo.nip05.SilentPaymentNotification;
import com.sparrowwallet.drongo.protocol.Bech32;
import com.sparrowwallet.sparrow.AppServices;
import javafx.application.Platform;
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

import java.util.List;

/**
 * Dialog for receiving Silent Payment notifications via NIP-17 DMs.
 * The user enters their Nostr nsec, clicks "Check for Payments",
 * and the dialog shows any incoming SP notifications.
 */
public class SpNotificationReceiveDialog extends Dialog<SilentPaymentNotification> {
    private static final Logger log = LoggerFactory.getLogger(SpNotificationReceiveDialog.class);

    private final PasswordField nsecField;
    private final Button checkButton;
    private final ProgressIndicator progress;
    private final Label statusLabel;
    private final ListView<SilentPaymentNotification> notificationList;

    public SpNotificationReceiveDialog() {
        final DialogPane dialogPane = getDialogPane();
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        AppServices.onEscapePressed(dialogPane.getScene(), this::close);

        dialogPane.setPrefWidth(650);
        dialogPane.setPrefHeight(500);
        dialogPane.getStylesheets().add(AppServices.class.getResource("app.css").toExternalForm());
        AppServices.moveToActiveWindowScreen(this);

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));

        // Title
        HBox titleBox = new HBox(8);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label("Incoming SP Notifications");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Label nostrLabel = new Label("N");
        nostrLabel.setStyle("-fx-text-fill: #8B5CF6; -fx-font-weight: bold; -fx-font-size: 22px;");
        titleBox.getChildren().addAll(titleLabel, nostrLabel);

        // Description
        Label descLabel = new Label("Enter your Nostr nsec to check for Silent Payment notifications sent to you via NIP-17.");
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: #666;");

        // nsec input
        HBox nsecBox = new HBox(8);
        nsecBox.setAlignment(Pos.CENTER_LEFT);
        Label nsecLabel = new Label("Your nsec:");
        nsecLabel.setMinWidth(70);
        nsecField = new PasswordField();
        nsecField.setPromptText("nsec1...");
        HBox.setHgrow(nsecField, Priority.ALWAYS);
        checkButton = new Button("Check for Payments");
        progress = new ProgressIndicator();
        progress.setMaxHeight(18);
        progress.setMaxWidth(18);
        progress.setVisible(false);
        nsecBox.getChildren().addAll(nsecLabel, nsecField, checkButton, progress);

        statusLabel = new Label("Enter your nsec and click Check for Payments");
        statusLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");

        // Notification list
        notificationList = new ListView<>();
        notificationList.setCellFactory(_ -> new NotificationCell());
        notificationList.setPlaceholder(new Label("No notifications found yet."));
        VBox.setVgrow(notificationList, Priority.ALWAYS);

        content.getChildren().addAll(titleBox, descLabel, nsecBox, statusLabel, new Separator(), notificationList);

        dialogPane.setContent(content);

        // Buttons
        ButtonType closeButtonType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType copyButtonType = new ButtonType("Copy TXID", ButtonBar.ButtonData.LEFT);
        dialogPane.getButtonTypes().addAll(copyButtonType, closeButtonType);

        Button copyButton = (Button)dialogPane.lookupButton(copyButtonType);
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

        // Check button action
        checkButton.setOnAction(_ -> pollNotifications());
        nsecField.setOnAction(_ -> pollNotifications());

        setResultConverter(dialogButton -> null);
    }

    private void pollNotifications() {
        String nsec = nsecField.getText().trim();
        if(nsec.isEmpty()) {
            statusLabel.setText("Please enter your nsec");
            return;
        }

        byte[] privKey;
        try {
            privKey = decodeNsec(nsec);
        } catch(Exception e) {
            statusLabel.setText("Invalid nsec: " + e.getMessage());
            return;
        }

        checkButton.setDisable(true);
        progress.setVisible(true);
        statusLabel.setText("Polling Nostr relays for SP notifications...");

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
                statusLabel.setText("Found " + notifications.size() + " notification(s) totaling " + totalSats + " sats");
            }
        });
        service.setOnFailed(_ -> {
            progress.setVisible(false);
            checkButton.setDisable(false);
            String msg = service.getException() != null ? service.getException().getMessage() : "Unknown error";
            statusLabel.setText("Error: " + msg);
        });
        service.start();
    }

    /**
     * Decode an nsec bech32 string to a 32-byte private key.
     */
    private byte[] decodeNsec(String nsec) {
        Bech32.Bech32Data decoded = Bech32.decode(nsec, 90);
        if(!decoded.hrp.equals("nsec")) {
            throw new IllegalArgumentException("Expected nsec prefix, got: " + decoded.hrp);
        }
        byte[] converted = Bech32.convertBits(decoded.data, 0, decoded.data.length, 5, 8, false);
        if(converted.length != 32) {
            throw new IllegalArgumentException("Invalid nsec: decoded to " + converted.length + " bytes");
        }
        return converted;
    }

    private static class PollService extends Service<List<SilentPaymentNotification>> {
        private final byte[] privKey;

        PollService(byte[] privKey) {
            this.privKey = privKey;
        }

        @Override
        protected Task<List<SilentPaymentNotification>> createTask() {
            return new Task<>() {
                @Override
                protected List<SilentPaymentNotification> call() {
                    Nip17Receiver receiver = new Nip17Receiver(privKey);
                    // Poll for notifications from the last 30 days
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
