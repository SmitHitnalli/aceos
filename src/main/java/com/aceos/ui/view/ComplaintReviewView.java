package com.aceos.ui.view;

import com.aceos.model.Complaint;
import com.aceos.service.AceOsService;
import com.aceos.service.AceOsServiceException;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ComplaintReviewView extends VBox {

    public ComplaintReviewView(
            AceOsService service) {
        setSpacing(20);
        setPadding(new Insets(40));
        setMaxWidth(720);

        Label title = new Label("Complaint Review");
        title.setStyle(
                "-fx-font-size: 28px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        Label description = new Label(
                "Accept or reject a submitted complaint "
                        + "as an administrator."
        );
        description.setStyle(
                "-fx-font-size: 15px;"
                        + "-fx-text-fill: #475569;"
        );

        Label guidance = new Label(
                "Only complaints with SUBMITTED status "
                        + "can be reviewed."
        );
        guidance.setStyle(
                "-fx-text-fill: #64748b;"
        );

        TextField complaintIdField = new TextField();
        complaintIdField.setPromptText(
                "Enter the complaint ID"
        );

        TextField administratorIdField =
                new TextField();
        administratorIdField.setPromptText(
                "Enter the administrator user ID"
        );

        GridPane form = new GridPane();
        form.setHgap(16);
        form.setVgap(16);

        form.add(new Label("Complaint ID"), 0, 0);
        form.add(complaintIdField, 1, 0);

        form.add(new Label("Administrator ID"), 0, 1);
        form.add(administratorIdField, 1, 1);

        GridPane.setHgrow(
                complaintIdField,
                Priority.ALWAYS
        );
        GridPane.setHgrow(
                administratorIdField,
                Priority.ALWAYS
        );

        Button acceptButton =
                new Button("Accept Complaint");
        acceptButton.setMinHeight(40);

        Button rejectButton =
                new Button("Reject Complaint");
        rejectButton.setMinHeight(40);

        HBox actions = new HBox(
                12,
                acceptButton,
                rejectButton
        );

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);

        acceptButton.setOnAction(event ->
                reviewComplaint(
                        service,
                        complaintIdField,
                        administratorIdField,
                        acceptButton,
                        rejectButton,
                        statusLabel,
                        true
                )
        );

        rejectButton.setOnAction(event ->
                reviewComplaint(
                        service,
                        complaintIdField,
                        administratorIdField,
                        acceptButton,
                        rejectButton,
                        statusLabel,
                        false
                )
        );

        getChildren().addAll(
                title,
                description,
                guidance,
                form,
                actions,
                statusLabel
        );
    }

    private void reviewComplaint(
            AceOsService service,
            TextField complaintIdField,
            TextField administratorIdField,
            Button acceptButton,
            Button rejectButton,
            Label statusLabel,
            boolean accept) {
        acceptButton.setDisable(true);
        rejectButton.setDisable(true);

        try {
            int complaintId = parseId(
                    complaintIdField.getText(),
                    "Complaint"
            );
            int administratorId = parseId(
                    administratorIdField.getText(),
                    "Administrator"
            );

            Complaint reviewedComplaint;

            if (accept) {
                reviewedComplaint =
                        service.acceptComplaint(
                                complaintId,
                                administratorId
                        );
            } else {
                reviewedComplaint =
                        service.rejectComplaint(
                                complaintId,
                                administratorId
                        );
            }

            statusLabel.setStyle(
                    "-fx-text-fill: #15803d;"
            );
            statusLabel.setText(
                    "Complaint #"
                            + reviewedComplaint.getComplaintId()
                            + " is now "
                            + reviewedComplaint.getStatus()
            );

            complaintIdField.clear();
            administratorIdField.clear();
            complaintIdField.requestFocus();
        } catch (AceOsServiceException exception) {
            statusLabel.setStyle(
                    "-fx-text-fill: #b91c1c;"
            );
            statusLabel.setText(
                    exception.getMessage()
            );
        } finally {
            acceptButton.setDisable(false);
            rejectButton.setDisable(false);
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