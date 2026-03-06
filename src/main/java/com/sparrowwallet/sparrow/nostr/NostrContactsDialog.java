package com.sparrowwallet.sparrow.nostr;

import com.sparrowwallet.drongo.nip05.NostrContact;
import com.sparrowwallet.sparrow.AppServices;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;

import java.io.IOException;

/**
 * Modal dialog for browsing Nostr contacts and selecting one to pay via Silent Payment.
 */
public class NostrContactsDialog extends Dialog<NostrContact> {

    public NostrContactsDialog() {
        final DialogPane dialogPane = getDialogPane();
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        AppServices.onEscapePressed(dialogPane.getScene(), this::close);

        try {
            FXMLLoader loader = new FXMLLoader(AppServices.class.getResource("nostr/nostr-contacts.fxml"));
            dialogPane.setContent(loader.load());
            NostrContactsController controller = loader.getController();
            controller.initializeView();

            dialogPane.setPrefWidth(700);
            dialogPane.setPrefHeight(600);
            dialogPane.setMinHeight(dialogPane.getPrefHeight());
            AppServices.moveToActiveWindowScreen(this);

            dialogPane.getStylesheets().add(AppServices.class.getResource("app.css").toExternalForm());
            dialogPane.getStylesheets().add(AppServices.class.getResource("nostr/nostr-contacts.css").toExternalForm());

            final ButtonType payButtonType = new ButtonType("Pay Contact", ButtonBar.ButtonData.APPLY);
            final ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

            dialogPane.getButtonTypes().addAll(payButtonType, cancelButtonType);

            Button payButton = (Button)dialogPane.lookupButton(payButtonType);
            payButton.setDisable(true);
            payButton.setDefaultButton(true);

            // Enable pay button only when a contact with SP address is selected
            controller.selectedContactProperty().addListener((_, _, contact) -> {
                payButton.setDisable(contact == null || !contact.hasSilentPaymentAddress());
                payButton.setDefaultButton(!payButton.isDisable());
            });

            controller.closeProperty().addListener((_, _, newValue) -> {
                if(newValue) {
                    close();
                }
            });

            setResultConverter(dialogButton -> {
                if(dialogButton == payButtonType) {
                    return controller.getSelectedContact();
                }
                return null;
            });
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
}
