package com.aceos.ui.view;

import com.aceos.model.WorkOrder;
import com.aceos.service.AceOsService;
import com.aceos.service.AceOsServiceException;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class WorkOrderManagementView extends VBox {

    public WorkOrderManagementView(
            AceOsService service) {
        setSpacing(20);
        setPadding(new Insets(40));
        setMaxWidth(720);

        Label title = new Label("Work-Order Assignment");
        title.setStyle(
                "-fx-font-size: 28px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        Label description = new Label(
                "Assign an accepted complaint "
                        + "to a maintenance technician."
        );
        description.setStyle(
                "-fx-font-size: 15px;"
                        + "-fx-text-fill: #475569;"
        );

        Label guidance = new Label(
                "The complaint must be ACCEPTED, and "
                        + "the selected user must be a TECHNICIAN."
        );
        guidance.setWrapText(true);
        guidance.setStyle(
                "-fx-text-fill: #64748b;"
        );

        TextField complaintIdField = new TextField();
        complaintIdField.setPromptText(
                "Enter the accepted complaint ID"
        );

        TextField technicianIdField = new TextField();
        technicianIdField.setPromptText(
                "Enter the technician user ID"
        );

        GridPane form = new GridPane();
        form.setHgap(16);
        form.setVgap(16);

        form.add(new Label("Complaint ID"), 0, 0);
        form.add(complaintIdField, 1, 0);

        form.add(new Label("Technician ID"), 0, 1);
        form.add(technicianIdField, 1, 1);

        GridPane.setHgrow(
                complaintIdField,
                Priority.ALWAYS
        );
        GridPane.setHgrow(
                technicianIdField,
                Priority.ALWAYS
        );

        Button assignButton =
                new Button("Assign Work Order");
        assignButton.setMinHeight(40);

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);

        assignButton.setOnAction(event ->
                assignWorkOrder(
                        service,
                        complaintIdField,
                        technicianIdField,
                        assignButton,
                        statusLabel
                )
        );

        getChildren().addAll(
                title,
                description,
                guidance,
                form,
                assignButton,
                statusLabel
        );
    }

    private void assignWorkOrder(
            AceOsService service,
            TextField complaintIdField,
            TextField technicianIdField,
            Button assignButton,
            Label statusLabel) {
        assignButton.setDisable(true);

        try {
            int complaintId = parseId(
                    complaintIdField.getText(),
                    "Complaint"
            );
            int technicianId = parseId(
                    technicianIdField.getText(),
                    "Technician"
            );

            WorkOrder workOrder =
                    service.assignWorkOrder(
                            complaintId,
                            technicianId
                    );

            statusLabel.setStyle(
                    "-fx-text-fill: #15803d;"
            );
            statusLabel.setText(
                    "Created work order #"
                            + workOrder.getWorkOrderId()
                            + " for complaint #"
                            + workOrder
                                    .getComplaint()
                                    .getComplaintId()
                            + ", assigned to "
                            + workOrder
                                    .getTechnician()
                                    .getName()
                            + " ("
                            + workOrder.getStatus()
                            + ")"
            );

            complaintIdField.clear();
            technicianIdField.clear();
            complaintIdField.requestFocus();
        } catch (AceOsServiceException exception) {
            statusLabel.setStyle(
                    "-fx-text-fill: #b91c1c;"
            );
            statusLabel.setText(
                    exception.getMessage()
            );
        } finally {
            assignButton.setDisable(false);
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