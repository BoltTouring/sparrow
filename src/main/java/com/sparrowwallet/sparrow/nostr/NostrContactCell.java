package com.sparrowwallet.sparrow.nostr;

import com.sparrowwallet.drongo.nip05.NostrContact;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Custom ListCell for displaying Nostr contacts with Silent Payment indicators.
 */
public class NostrContactCell extends ListCell<NostrContact> {

    @Override
    protected void updateItem(NostrContact contact, boolean empty) {
        super.updateItem(contact, empty);

        if(empty || contact == null) {
            setText(null);
            setGraphic(null);
            setTooltip(null);
            getStyleClass().remove("no-sp");
        } else {
            BorderPane pane = new BorderPane();
            pane.setPadding(new Insets(4, 8, 4, 8));
            pane.getStyleClass().add("nostr-contact-cell");

            // Left: name and NIP-05
            VBox nameBox = new VBox(2);
            nameBox.setAlignment(Pos.CENTER_LEFT);

            Label nameLabel = new Label(contact.displayName());
            nameLabel.getStyleClass().add("contact-name");

            if(contact.nip05() != null && !contact.nip05().isEmpty()) {
                Label nip05Label = new Label(contact.nip05());
                nip05Label.getStyleClass().add("contact-nip05");
                nameBox.getChildren().addAll(nameLabel, nip05Label);
            } else {
                Label pubkeyLabel = new Label(contact.getShortPubkey());
                pubkeyLabel.getStyleClass().add("contact-nip05");
                nameBox.getChildren().addAll(nameLabel, pubkeyLabel);
            }

            pane.setLeft(nameBox);

            // Right: SP badge
            HBox rightBox = new HBox(5);
            rightBox.setAlignment(Pos.CENTER_RIGHT);

            if(contact.hasSilentPaymentAddress()) {
                Label spBadge = new Label("\u20BF");
                spBadge.getStyleClass().add("sp-badge");
                spBadge.setTooltip(new Tooltip("Has Silent Payment address"));

                if(contact.signatureVerified()) {
                    Label verifiedBadge = new Label("\u2713");
                    verifiedBadge.setStyle("-fx-text-fill: #22C55E; -fx-font-size: 11px;");
                    verifiedBadge.setTooltip(new Tooltip("Signature verified"));
                    rightBox.getChildren().addAll(spBadge, verifiedBadge);
                } else {
                    rightBox.getChildren().add(spBadge);
                }
            }

            pane.setRight(rightBox);

            // Dim contacts without SP address
            if(!contact.hasSilentPaymentAddress()) {
                pane.setOpacity(0.5);
            }

            // Tooltip with full pubkey
            setTooltip(new Tooltip(contact.pubkey()));

            setText(null);
            setGraphic(pane);
        }
    }
}
