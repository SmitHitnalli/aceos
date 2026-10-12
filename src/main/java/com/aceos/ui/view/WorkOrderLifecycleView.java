package com.aceos.ui.view;

import com.aceos.service.AceOsService;
import com.aceos.service.AceOsServiceException;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class WorkOrderLifecycleView extends VBox {

    public WorkOrderLifecycleView(AceOsService service) {
        setSpacing(20);
        setPadding(new Insets(40));
        setMaxWidth(720);

        Label title = new Label("Work-Order Lifecycle");
        title.setStyle(
                "-fx-font-size: 28px;" + "-fx-font-weight: bold;" + "-fx-text-fill: #1e293b;");

        Label description =
                new Label("Start assigned maintenance work and " + "record its completion.");
        description.setStyle("-fx-font-size: 15px;" + "-fx-text-fill: #475569;");

        Label guidance =
                new Label(
                        "Starting changes ASSIGNED to IN_PROGRESS. "
                                + "Completion requires an IN_PROGRESS "
                                + "work order and a maintenance action.");
        guidance.setWrapText(true);
        guidance.setStyle("-fx-text-fill: #64748b;");

        TextField workOrderIdField = new TextField();
        workOrderIdField.setPromptText("Enter the work-order ID");

        TextArea actionTakenField = new TextArea();
        actionTakenField.setPromptText("Describe the maintenance action taken");
        actionTakenField.setPrefRowCount(5);
        actionTakenField.setWrapText(true);

        GridPane form = new GridPane();
        form.setHgap(16);
        form.setVgap(16);

        form.add(new Label("Work-Order ID"), 0, 0);
        form.add(workOrderIdField, 1, 0);

        form.add(new Label("Action Taken"), 0, 1);
        form.add(actionTakenField, 1, 1);

        GridPane.setHgrow(workOrderIdField, Priority.ALWAYS);
        GridPane.setHgrow(actionTakenField, Priority.ALWAYS);

        Button startButton = new Button("Start Work");
        startButton.setMinHeight(40);

        Button completeButton = new Button("Complete Work");
        completeButton.setMinHeight(40);

        HBox actions = new HBox(12, startButton, completeButton);

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);

        startButton.setOnAction(
                event ->
                        startWork(
                                service,
                                workOrderIdField,
                                actionTakenField,
                                startButton,
                                completeButton,
                                statusLabel));

        completeButton.setOnAction(
                event ->
                        completeWork(
                                service,
                                workOrderIdField,
                                actionTakenField,
                                startButton,
                                completeButton,
                                statusLabel));

        getChildren().addAll(title, description, guidance, form, actions, statusLabel);
    }

    private void startWork(
            AceOsService service,
            TextField workOrderIdField,
            TextArea actionTakenField,
            Button startButton,
            Button completeButton,
            Label statusLabel) {
        setButtonsDisabled(startButton, completeButton, true);

        try {
            int workOrderId = parseId(workOrderIdField.getText());

            com.aceos.ui.FxTasks.run(
                    () -> service.startWorkOrder(workOrderId),
                    startedWorkOrder -> {
                        statusLabel.setStyle("-fx-text-fill: #15803d;");
                        statusLabel.setText(
                                "Work order #"
                                        + startedWorkOrder.getWorkOrderId()
                                        + " is now "
                                        + startedWorkOrder.getStatus()
                                        + "; asset "
                                        + startedWorkOrder.getComplaint().getAsset().getAssetCode()
                                        + " is "
                                        + startedWorkOrder.getComplaint().getAsset().getStatus());

                        actionTakenField.requestFocus();
                    },
                    exception -> {
                        showError(statusLabel, exception);
                    },
                    () -> {
                        setButtonsDisabled(startButton, completeButton, false);
                    });
        } catch (AceOsServiceException exception) {
            showError(statusLabel, exception);

            setButtonsDisabled(startButton, completeButton, false);
        }
    }

    private void completeWork(
            AceOsService service,
            TextField workOrderIdField,
            TextArea actionTakenField,
            Button startButton,
            Button completeButton,
            Label statusLabel) {
        setButtonsDisabled(startButton, completeButton, true);

        try {
            int workOrderId = parseId(workOrderIdField.getText());

            var input1 = actionTakenField.getText();
            com.aceos.ui.FxTasks.run(
                    () -> service.completeWorkOrder(workOrderId, input1),
                    maintenanceRecord -> {
                        statusLabel.setStyle("-fx-text-fill: #15803d;");
                        statusLabel.setText(
                                "Completed work order #"
                                        + maintenanceRecord.getWorkOrder().getWorkOrderId()
                                        + " and created maintenance record #"
                                        + maintenanceRecord.getMaintenanceRecordId()
                                        + " ("
                                        + maintenanceRecord.getWorkOrder().getStatus()
                                        + ")");

                        workOrderIdField.clear();
                        actionTakenField.clear();
                        workOrderIdField.requestFocus();
                    },
                    exception -> {
                        showError(statusLabel, exception);
                    },
                    () -> {
                        setButtonsDisabled(startButton, completeButton, false);
                    });
        } catch (AceOsServiceException exception) {
            showError(statusLabel, exception);

            setButtonsDisabled(startButton, completeButton, false);
        }
    }

    private int parseId(String value) throws AceOsServiceException {
        if (value == null || value.isBlank()) {
            throw new AceOsServiceException("Work order ID is required");
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new AceOsServiceException("Work order ID must be a whole number", exception);
        }
    }

    private void showError(Label statusLabel, AceOsServiceException exception) {
        statusLabel.setStyle("-fx-text-fill: #b91c1c;");
        statusLabel.setText(exception.getMessage());
    }

    private void setButtonsDisabled(Button startButton, Button completeButton, boolean disabled) {
        startButton.setDisable(disabled);
        completeButton.setDisable(disabled);
    }
}
