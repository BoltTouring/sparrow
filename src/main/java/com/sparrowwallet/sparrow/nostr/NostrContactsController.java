package com.sparrowwallet.sparrow.nostr;

import com.sparrowwallet.drongo.nip05.NostrContact;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.control.CopyableTextField;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Button;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for the Nostr Contacts dialog.
 * Manages npub/NIP-05 input, contact resolution, and list display.
 */
public class NostrContactsController implements Initializable {

    @FXML
    private CopyableTextField npubInput;

    @FXML
    private Button resolveButton;

    @FXML
    private ProgressIndicator resolveProgress;

    @FXML
    private Label statusLabel;

    @FXML
    private Label contactCountLabel;

    @FXML
    private ListView<NostrContact> contactsList;

    private final ObjectProperty<NostrContact> selectedContactProperty = new SimpleObjectProperty<>();
    private final BooleanProperty closeProperty = new SimpleBooleanProperty(false);

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        contactsList.setCellFactory(_ -> new NostrContactCell());

        contactsList.getSelectionModel().selectedItemProperty().addListener((_, _, newValue) -> {
            selectedContactProperty.set(newValue);
        });

        npubInput.setOnAction(_ -> resolveContacts());
    }

    public void initializeView() {
        // Called after FXML load — any additional setup goes here
    }

    @FXML
    public void resolveContacts() {
        String input = npubInput.getText();
        if(input == null || input.trim().isEmpty()) {
            statusLabel.setText("Please enter an npub or NIP-05 address");
            return;
        }

        input = input.trim();

        resolveProgress.setVisible(true);
        resolveButton.setDisable(true);
        statusLabel.setText("Resolving contacts...");
        contactsList.setItems(FXCollections.emptyObservableList());
        contactCountLabel.setText("");

        NostrContactsService service = new NostrContactsService(input);
        service.setOnSucceeded(_ -> {
            resolveProgress.setVisible(false);
            resolveButton.setDisable(false);

            List<NostrContact> contacts = service.getValue();
            contactsList.setItems(FXCollections.observableList(contacts));

            long spCount = contacts.stream().filter(NostrContact::hasSilentPaymentAddress).count();
            contactCountLabel.setText("Contacts: " + contacts.size());
            statusLabel.setText("Found " + contacts.size() + " contacts (" + spCount + " with Silent Payment)");
        });

        service.setOnFailed(failEvent -> {
            resolveProgress.setVisible(false);
            resolveButton.setDisable(false);

            String errorMsg = "Resolution failed";
            if(failEvent.getSource().getException() != null) {
                errorMsg = failEvent.getSource().getException().getMessage();
            }
            statusLabel.setText("Error: " + errorMsg);
        });

        service.start();
    }

    public NostrContact getSelectedContact() {
        return selectedContactProperty.get();
    }

    public ObjectProperty<NostrContact> selectedContactProperty() {
        return selectedContactProperty;
    }

    public BooleanProperty closeProperty() {
        return closeProperty;
    }
}
