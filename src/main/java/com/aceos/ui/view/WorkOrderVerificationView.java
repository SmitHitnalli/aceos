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

public class WorkOrderVerificationView extends VBox {

    public WorkOrderVerificationView(
            AceOsService service) {
        setSpacing(20);
        setPadding(new Insets(40));
        setMaxWidth(720);

        Label title = new Label(
                "Maintenance Verification"
        );
        title.setStyle(
                "-fx-font-size: 28px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        Label description = new Label(
                "Verify completed maintenance work "
                        + "as an administrator."
        );
        description.setStyle(
                "-fx-font-size: 15px;"
                        + "-fx-text-fill: #475569;"
        );

        Label guidance = new Label(
                "Only work orders with COMPLETED status "
                        + "can be verified."
        );
        guidance.setStyle(
                "-fx-text-fill: #64748b;"
        );

        TextField workOrderIdField =
                new TextField();
        workOrderIdField.setPromptText(
                "Enter the completed work-order ID"
        );

        TextField administratorIdField =
                new TextField();
        administratorIdField.setPromptText(
                "Enter the administrator user ID"
        );

        GridPane form = new GridPane();
        form.setHgap(16);
        form.setVgap(16);

        form.add(
                new Label("Work Order ID"),
                0,
                0
        );
        form.add(workOrderIdField, 1, 0);

        form.add(
                new Label("Administrator ID"),
                0,
                1
        );
        form.add(administratorIdField, 1, 1);

        GridPane.setHgrow(
                workOrderIdField,
                Priority.ALWAYS
        );
        GridPane.setHgrow(
                administratorIdField,
                Priority.ALWAYS
        );

        Button verifyButton =
                new Button("Verify Maintenance");
        verifyButton.setMinHeight(40);

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);

        verifyButton.setOnAction(event ->
                verifyWorkOrder(
                        service,
                        workOrderIdField,
                        administratorIdField,
                        verifyButton,
                        statusLabel
                )
        );

        getChildren().addAll(
                title,
                description,
                guidance,
                form,
                verifyButton,
                statusLabel
        );
    }

    private void verifyWorkOrder(
            AceOsService service,
            TextField workOrderIdField,
            TextField administratorIdField,
            Button verifyButton,
            Label statusLabel) {
        verifyButton.setDisable(true);

        try {
            int workOrderId = parseId(
                    workOrderIdField.getText(),
                    "Work order"
            );
            int administratorId = parseId(
                    administratorIdField.getText(),
                    "Administrator"
            );

            WorkOrder verifiedWorkOrder =
                    service.verifyWorkOrder(
                            workOrderId,
                            administratorId
                    );

            statusLabel.setStyle(
                    "-fx-text-fill: #15803d;"
            );
            statusLabel.setText(
                    "Work order #"
                            + verifiedWorkOrder
                                    .getWorkOrderId()
                            + " is "
                            + verifiedWorkOrder.getStatus()
                            + "; complaint #"
                            + verifiedWorkOrder
                                    .getComplaint()
                                    .getComplaintId()
                            + " is "
                            + verifiedWorkOrder
                                    .getComplaint()
                                    .getStatus()
                            + "; asset "
                            + verifiedWorkOrder
                                    .getComplaint()
                                    .getAsset()
                                    .getAssetCode()
                            + " is "
                            + verifiedWorkOrder
                                    .getComplaint()
                                    .getAsset()
                                    .getStatus()
            );

            workOrderIdField.clear();
            administratorIdField.clear();
            workOrderIdField.requestFocus();
        } catch (AceOsServiceException exception) {
            statusLabel.setStyle(
                    "-fx-text-fill: #b91c1c;"
            );
            statusLabel.setText(
                    exception.getMessage()
            );
        } finally {
            verifyButton.setDisable(false);
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