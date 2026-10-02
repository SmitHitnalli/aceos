package com.aceos.ui.view;

import com.aceos.model.Complaint;
import com.aceos.model.ComplaintPriority;
import com.aceos.service.AceOsService;
import com.aceos.service.AceOsServiceException;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ComplaintManagementView extends VBox {

    public ComplaintManagementView(
            AceOsService service) {
        setSpacing(20);
        setPadding(new Insets(40));
        setMaxWidth(720);

        Label title = new Label("Complaint Management");
        title.setStyle(
                "-fx-font-size: 28px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        Label description = new Label(
                "Submit a maintenance complaint "
                        + "for a registered asset."
        );
        description.setStyle(
                "-fx-font-size: 15px;"
                        + "-fx-text-fill: #475569;"
        );

        TextField assetIdField = new TextField();
        assetIdField.setPromptText(
                "Enter the asset ID"
        );

        TextField requesterIdField = new TextField();
        requesterIdField.setPromptText(
                "Enter the requester user ID"
        );

        TextArea complaintDescriptionField =
                new TextArea();
        complaintDescriptionField.setPromptText(
                "Describe the maintenance problem"
        );
        complaintDescriptionField.setPrefRowCount(5);
        complaintDescriptionField.setWrapText(true);

        ComboBox<ComplaintPriority> priorityBox =
                new ComboBox<>();
        priorityBox.getItems().addAll(
                ComplaintPriority.values()
        );
        priorityBox.setPromptText(
                "Select a priority"
        );
        priorityBox.setMaxWidth(Double.MAX_VALUE);

        GridPane form = new GridPane();
        form.setHgap(16);
        form.setVgap(16);

        form.add(new Label("Asset ID"), 0, 0);
        form.add(assetIdField, 1, 0);

        form.add(new Label("Requester ID"), 0, 1);
        form.add(requesterIdField, 1, 1);

        form.add(new Label("Description"), 0, 2);
        form.add(complaintDescriptionField, 1, 2);

        form.add(new Label("Priority"), 0, 3);
        form.add(priorityBox, 1, 3);

        GridPane.setHgrow(
                assetIdField,
                Priority.ALWAYS
        );
        GridPane.setHgrow(
                requesterIdField,
                Priority.ALWAYS
        );
        GridPane.setHgrow(
                complaintDescriptionField,
                Priority.ALWAYS
        );
        GridPane.setHgrow(
                priorityBox,
                Priority.ALWAYS
        );

        Button submitButton =
                new Button("Submit Complaint");
        submitButton.setMinHeight(40);

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);

        submitButton.setOnAction(event ->
                submitComplaint(
                        service,
                        assetIdField,
                        requesterIdField,
                        complaintDescriptionField,
                        priorityBox,
                        submitButton,
                        statusLabel
                )
        );

        getChildren().addAll(
                title,
                description,
                form,
                submitButton,
                statusLabel
        );
    }

    private void submitComplaint(
            AceOsService service,
            TextField assetIdField,
            TextField requesterIdField,
            TextArea complaintDescriptionField,
            ComboBox<ComplaintPriority> priorityBox,
            Button submitButton,
            Label statusLabel) {
        submitButton.setDisable(true);

        try {
            int assetId = parseId(
                    assetIdField.getText(),
                    "Asset"
            );
            int requesterId = parseId(
                    requesterIdField.getText(),
                    "Requester"
            );

            Complaint submittedComplaint =
                    service.submitComplaint(
                            assetId,
                            requesterId,
                            complaintDescriptionField.getText(),
                            priorityBox.getValue()
                    );

            statusLabel.setStyle(
                    "-fx-text-fill: #15803d;"
            );
            statusLabel.setText(
                    "Submitted complaint #"
                            + submittedComplaint.getComplaintId()
                            + " for asset "
                            + submittedComplaint
                                    .getAsset()
                                    .getAssetCode()
                            + " ("
                            + submittedComplaint.getPriority()
                            + ", "
                            + submittedComplaint.getStatus()
                            + ")"
            );

            assetIdField.clear();
            requesterIdField.clear();
            complaintDescriptionField.clear();
            priorityBox
                    .getSelectionModel()
                    .clearSelection();
            assetIdField.requestFocus();
        } catch (AceOsServiceException exception) {
            statusLabel.setStyle(
                    "-fx-text-fill: #b91c1c;"
            );
            statusLabel.setText(
                    exception.getMessage()
            );
        } finally {
            submitButton.setDisable(false);
        }
    }

    private int parseId(
            String value,
            String entityName)
            throws AceOsServiceException {
        if (value == null || value.isBlank()) {
            throw new AceOsServiceException(
                    entityName + " ID is required"
            );
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new AceOsServiceException(
                    entityName
                            + " ID must be a whole number",
                    exception
            );
        }
    }
}