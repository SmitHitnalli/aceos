package com.aceos.ui.view;

import com.aceos.model.PreventiveMaintenanceSchedule;
import com.aceos.service.AceOsService;
import com.aceos.service.AceOsServiceException;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;

public class PreventiveMaintenanceManagementView
        extends VBox {

    public PreventiveMaintenanceManagementView(
            AceOsService service) {
        setSpacing(14);
        setPadding(new Insets(30));
        setMaxWidth(760);

        Label title = new Label(
                "Preventive Maintenance"
        );
        title.setStyle(
                "-fx-font-size: 28px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        Label description = new Label(
                "Create recurring maintenance schedules "
                        + "and manage their lifecycle."
        );
        description.setWrapText(true);
        description.setStyle(
                "-fx-font-size: 15px;"
                        + "-fx-text-fill: #475569;"
        );

        Label creationHeading = new Label(
                "Create Schedule"
        );
        creationHeading.setStyle(
                "-fx-font-size: 18px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        TextField assetIdField = new TextField();
        assetIdField.setPromptText(
                "Use an ID from Asset Directory"
        );

        TextField taskField = new TextField();
        taskField.setPromptText(
                "Example: Inspect and clean air filters"
        );

        TextField frequencyField = new TextField();
        frequencyField.setPromptText(
                "Example: 90"
        );

        DatePicker nextDueDatePicker =
                new DatePicker(
                        LocalDate.now().plusDays(30)
                );
        nextDueDatePicker.setMaxWidth(Double.MAX_VALUE);

        GridPane creationForm = new GridPane();
        creationForm.setHgap(16);
        creationForm.setVgap(12);

        creationForm.add(
                new Label("Asset ID"),
                0,
                0
        );
        creationForm.add(assetIdField, 1, 0);

        creationForm.add(
                new Label("Maintenance Task"),
                0,
                1
        );
        creationForm.add(taskField, 1, 1);

        creationForm.add(
                new Label("Frequency (Days)"),
                0,
                2
        );
        creationForm.add(frequencyField, 1, 2);

        creationForm.add(
                new Label("Next Due Date"),
                0,
                3
        );
        creationForm.add(nextDueDatePicker, 1, 3);

        GridPane.setHgrow(
                assetIdField,
                Priority.ALWAYS
        );
        GridPane.setHgrow(
                taskField,
                Priority.ALWAYS
        );
        GridPane.setHgrow(
                frequencyField,
                Priority.ALWAYS
        );
        GridPane.setHgrow(
                nextDueDatePicker,
                Priority.ALWAYS
        );

        Button createButton =
                new Button("Create Schedule");
        createButton.setMinHeight(38);

        Label lifecycleHeading = new Label(
                "Manage Existing Schedule"
        );
        lifecycleHeading.setStyle(
                "-fx-font-size: 18px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        TextField scheduleIdField = new TextField();
        scheduleIdField.setPromptText(
                "Use an ID from Schedule Directory"
        );

        DatePicker completionDatePicker =
                new DatePicker(LocalDate.now());
        completionDatePicker.setMaxWidth(Double.MAX_VALUE);

        GridPane lifecycleForm = new GridPane();
        lifecycleForm.setHgap(16);
        lifecycleForm.setVgap(12);

        lifecycleForm.add(
                new Label("Schedule ID"),
                0,
                0
        );
        lifecycleForm.add(scheduleIdField, 1, 0);

        lifecycleForm.add(
                new Label("Completion Date"),
                0,
                1
        );
        lifecycleForm.add(completionDatePicker, 1, 1);

        GridPane.setHgrow(
                scheduleIdField,
                Priority.ALWAYS
        );
        GridPane.setHgrow(
                completionDatePicker,
                Priority.ALWAYS
        );

        Button pauseButton =
                new Button("Pause Schedule");
        pauseButton.setMinHeight(38);

        Button activateButton =
                new Button("Activate Schedule");
        activateButton.setMinHeight(38);

        Button completeButton =
                new Button("Record Completion");
        completeButton.setMinHeight(38);

        HBox lifecycleActions = new HBox(
                12,
                pauseButton,
                activateButton,
                completeButton
        );

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);

        createButton.setOnAction(event ->
                createSchedule(
                        service,
                        assetIdField,
                        taskField,
                        frequencyField,
                        nextDueDatePicker,
                        createButton,
                        statusLabel
                )
        );

        pauseButton.setOnAction(event ->
                changeScheduleStatus(
                        service,
                        scheduleIdField,
                        pauseButton,
                        activateButton,
                        completeButton,
                        statusLabel,
                        true
                )
        );

        activateButton.setOnAction(event ->
                changeScheduleStatus(
                        service,
                        scheduleIdField,
                        pauseButton,
                        activateButton,
                        completeButton,
                        statusLabel,
                        false
                )
        );

        completeButton.setOnAction(event ->
                recordCompletion(
                        service,
                        scheduleIdField,
                        completionDatePicker,
                        pauseButton,
                        activateButton,
                        completeButton,
                        statusLabel
                )
        );

        getChildren().addAll(
                title,
                description,
                creationHeading,
                creationForm,
                createButton,
                new Separator(),
                lifecycleHeading,
                lifecycleForm,
                lifecycleActions,
                statusLabel
        );
    }

    private void createSchedule(
            AceOsService service,
            TextField assetIdField,
            TextField taskField,
            TextField frequencyField,
            DatePicker nextDueDatePicker,
            Button createButton,
            Label statusLabel) {
        createButton.setDisable(true);

        try {
            int assetId = parsePositiveWholeNumber(
                    assetIdField.getText(),
                    "Asset ID"
            );
            int frequencyDays =
                    parsePositiveWholeNumber(
                            frequencyField.getText(),
                            "Frequency"
                    );

            PreventiveMaintenanceSchedule schedule =
                    service
                            .createPreventiveMaintenanceSchedule(
                                    assetId,
                                    taskField.getText(),
                                    frequencyDays,
                                    nextDueDatePicker.getValue()
                            );

            showSuccess(
                    statusLabel,
                    "Created schedule #"
                            + schedule
                                    .getMaintenanceScheduleId()
                            + " for asset "
                            + schedule
                                    .getAsset()
                                    .getAssetCode()
                            + "; next due "
                            + schedule.getNextDueDate()
            );

            assetIdField.clear();
            taskField.clear();
            frequencyField.clear();
            nextDueDatePicker.setValue(
                    LocalDate.now().plusDays(30)
            );
            assetIdField.requestFocus();
        } catch (AceOsServiceException exception) {
            showError(statusLabel, exception);
        } finally {
            createButton.setDisable(false);
        }
    }

    private void changeScheduleStatus(
            AceOsService service,
            TextField scheduleIdField,
            Button pauseButton,
            Button activateButton,
            Button completeButton,
            Label statusLabel,
            boolean pause) {
        setLifecycleButtonsDisabled(
                pauseButton,
                activateButton,
                completeButton,
                true
        );

        try {
            int scheduleId = parsePositiveWholeNumber(
                    scheduleIdField.getText(),
                    "Maintenance schedule ID"
            );

            PreventiveMaintenanceSchedule schedule;

            if (pause) {
                schedule = service
                        .pausePreventiveMaintenanceSchedule(
                                scheduleId
                        );
            } else {
                schedule = service
                        .activatePreventiveMaintenanceSchedule(
                                scheduleId
                        );
            }

            showSuccess(
                    statusLabel,
                    "Schedule #"
                            + schedule
                                    .getMaintenanceScheduleId()
                            + " is now "
                            + schedule.getStatus()
            );
        } catch (AceOsServiceException exception) {
            showError(statusLabel, exception);
        } finally {
            setLifecycleButtonsDisabled(
                    pauseButton,
                    activateButton,
                    completeButton,
                    false
            );
        }
    }

    private void recordCompletion(
            AceOsService service,
            TextField scheduleIdField,
            DatePicker completionDatePicker,
            Button pauseButton,
            Button activateButton,
            Button completeButton,
            Label statusLabel) {
        setLifecycleButtonsDisabled(
                pauseButton,
                activateButton,
                completeButton,
                true
        );

        try {
            int scheduleId = parsePositiveWholeNumber(
                    scheduleIdField.getText(),
                    "Maintenance schedule ID"
            );

            PreventiveMaintenanceSchedule schedule =
                    service
                            .completePreventiveMaintenanceSchedule(
                                    scheduleId,
                                    completionDatePicker.getValue()
                            );

            showSuccess(
                    statusLabel,
                    "Recorded completion for schedule #"
                            + schedule
                                    .getMaintenanceScheduleId()
                            + "; next due "
                            + schedule.getNextDueDate()
            );

            scheduleIdField.clear();
            completionDatePicker.setValue(LocalDate.now());
            scheduleIdField.requestFocus();
        } catch (AceOsServiceException exception) {
            showError(statusLabel, exception);
        } finally {
            setLifecycleButtonsDisabled(
                    pauseButton,
                    activateButton,
                    completeButton,
                    false
            );
        }
    }

    private int parsePositiveWholeNumber(
            String value,
            String fieldName)
            throws AceOsServiceException {
        if (value == null || value.isBlank()) {
            throw new AceOsServiceException(
                    fieldName + " is required"
            );
        }

        try {
            int number = Integer.parseInt(value.trim());

            if (number <= 0) {
                throw new AceOsServiceException(
                        fieldName + " must be positive"
                );
            }

            return number;
        } catch (NumberFormatException exception) {
            throw new AceOsServiceException(
                    fieldName + " must be a whole number",
                    exception
            );
        }
    }

    private void showSuccess(
            Label statusLabel,
            String message) {
        statusLabel.setStyle(
                "-fx-text-fill: #15803d;"
        );
        statusLabel.setText(message);
    }

    private void showError(
            Label statusLabel,
            AceOsServiceException exception) {
        statusLabel.setStyle(
                "-fx-text-fill: #b91c1c;"
        );
        statusLabel.setText(exception.getMessage());
    }

    private void setLifecycleButtonsDisabled(
            Button pauseButton,
            Button activateButton,
            Button completeButton,
            boolean disabled) {
        pauseButton.setDisable(disabled);
        activateButton.setDisable(disabled);
        completeButton.setDisable(disabled);
    }
}
