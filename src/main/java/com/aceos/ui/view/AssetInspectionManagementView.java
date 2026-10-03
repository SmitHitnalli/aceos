package com.aceos.ui.view;

import com.aceos.model.AssetInspection;
import com.aceos.model.InspectionStatus;
import com.aceos.service.AceOsService;
import com.aceos.service.AceOsServiceException;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;

public class AssetInspectionManagementView
        extends ScrollPane {

    public AssetInspectionManagementView(
            AceOsService service) {
        VBox content = new VBox(14);
        content.setPadding(new Insets(30));
        content.setMaxWidth(760);

        Label title = new Label("Asset Inspections");
        title.setStyle(
                "-fx-font-size: 28px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        Label description = new Label(
                "Schedule asset inspections and record "
                        + "technician pass or fail results."
        );
        description.setWrapText(true);
        description.setStyle(
                "-fx-font-size: 15px;"
                        + "-fx-text-fill: #475569;"
        );

        Label scheduleHeading = new Label(
                "Schedule Inspection"
        );
        styleSectionHeading(scheduleHeading);

        TextField assetIdField = new TextField();
        assetIdField.setPromptText(
                "Use an ID from Asset Directory"
        );

        TextField inspectionTypeField = new TextField();
        inspectionTypeField.setPromptText(
                "Example: Electrical safety inspection"
        );

        DatePicker scheduledDatePicker =
                new DatePicker(
                        LocalDate.now().plusDays(7)
                );
        scheduledDatePicker.setMaxWidth(Double.MAX_VALUE);

        GridPane scheduleForm = new GridPane();
        scheduleForm.setHgap(16);
        scheduleForm.setVgap(12);
        scheduleForm.add(new Label("Asset ID"), 0, 0);
        scheduleForm.add(assetIdField, 1, 0);
        scheduleForm.add(
                new Label("Inspection Type"),
                0,
                1
        );
        scheduleForm.add(inspectionTypeField, 1, 1);
        scheduleForm.add(
                new Label("Scheduled Date"),
                0,
                2
        );
        scheduleForm.add(scheduledDatePicker, 1, 2);

        GridPane.setHgrow(assetIdField, Priority.ALWAYS);
        GridPane.setHgrow(
                inspectionTypeField,
                Priority.ALWAYS
        );
        GridPane.setHgrow(
                scheduledDatePicker,
                Priority.ALWAYS
        );

        Button scheduleButton =
                new Button("Schedule Inspection");
        scheduleButton.setMinHeight(38);

        Label completionHeading = new Label(
                "Complete Inspection"
        );
        styleSectionHeading(completionHeading);

        TextField inspectionIdField = new TextField();
        inspectionIdField.setPromptText(
                "Use an ID from Inspection Directory"
        );

        TextField technicianIdField = new TextField();
        technicianIdField.setPromptText(
                "Use a technician ID from User Directory"
        );

        ComboBox<InspectionStatus> resultField =
                new ComboBox<>(
                        FXCollections.observableArrayList(
                                InspectionStatus.PASSED,
                                InspectionStatus.FAILED
                        )
                );
        resultField.setValue(InspectionStatus.PASSED);
        resultField.setMaxWidth(Double.MAX_VALUE);

        TextArea notesField = new TextArea();
        notesField.setPromptText(
                "Record findings and compliance details"
        );
        notesField.setPrefRowCount(3);
        notesField.setWrapText(true);

        DatePicker completionDatePicker =
                new DatePicker(LocalDate.now());
        completionDatePicker.setMaxWidth(Double.MAX_VALUE);

        GridPane completionForm = new GridPane();
        completionForm.setHgap(16);
        completionForm.setVgap(12);
        completionForm.add(
                new Label("Inspection ID"),
                0,
                0
        );
        completionForm.add(inspectionIdField, 1, 0);
        completionForm.add(
                new Label("Technician ID"),
                0,
                1
        );
        completionForm.add(technicianIdField, 1, 1);
        completionForm.add(new Label("Result"), 0, 2);
        completionForm.add(resultField, 1, 2);
        completionForm.add(new Label("Notes"), 0, 3);
        completionForm.add(notesField, 1, 3);
        completionForm.add(
                new Label("Completion Date"),
                0,
                4
        );
        completionForm.add(completionDatePicker, 1, 4);

        GridPane.setHgrow(
                inspectionIdField,
                Priority.ALWAYS
        );
        GridPane.setHgrow(
                technicianIdField,
                Priority.ALWAYS
        );
        GridPane.setHgrow(resultField, Priority.ALWAYS);
        GridPane.setHgrow(notesField, Priority.ALWAYS);
        GridPane.setHgrow(
                completionDatePicker,
                Priority.ALWAYS
        );

        Button completeButton =
                new Button("Complete Inspection");
        completeButton.setMinHeight(38);

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);

        scheduleButton.setOnAction(event ->
                scheduleInspection(
                        service,
                        assetIdField,
                        inspectionTypeField,
                        scheduledDatePicker,
                        scheduleButton,
                        statusLabel
                )
        );

        completeButton.setOnAction(event ->
                completeInspection(
                        service,
                        inspectionIdField,
                        technicianIdField,
                        resultField,
                        notesField,
                        completionDatePicker,
                        completeButton,
                        statusLabel
                )
        );

        content.getChildren().addAll(
                title,
                description,
                scheduleHeading,
                scheduleForm,
                scheduleButton,
                new Separator(),
                completionHeading,
                completionForm,
                completeButton,
                statusLabel
        );

        setContent(content);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setStyle(
                "-fx-background-color: transparent;"
                        + "-fx-background: transparent;"
        );
    }

    private void scheduleInspection(
            AceOsService service,
            TextField assetIdField,
            TextField inspectionTypeField,
            DatePicker scheduledDatePicker,
            Button scheduleButton,
            Label statusLabel) {
        scheduleButton.setDisable(true);

        try {
            int assetId = parseId(
                    assetIdField.getText(),
                    "Asset ID"
            );
            AssetInspection inspection =
                    service.scheduleAssetInspection(
                            assetId,
                            inspectionTypeField.getText(),
                            scheduledDatePicker.getValue()
                    );

            showSuccess(
                    statusLabel,
                    "Scheduled inspection #"
                            + inspection.getInspectionId()
                            + " for asset "
                            + inspection.getAsset().getAssetCode()
                            + " on "
                            + inspection.getScheduledDate()
            );

            assetIdField.clear();
            inspectionTypeField.clear();
            scheduledDatePicker.setValue(
                    LocalDate.now().plusDays(7)
            );
            assetIdField.requestFocus();
        } catch (AceOsServiceException exception) {
            showError(statusLabel, exception);
        } finally {
            scheduleButton.setDisable(false);
        }
    }

    private void completeInspection(
            AceOsService service,
            TextField inspectionIdField,
            TextField technicianIdField,
            ComboBox<InspectionStatus> resultField,
            TextArea notesField,
            DatePicker completionDatePicker,
            Button completeButton,
            Label statusLabel) {
        completeButton.setDisable(true);

        try {
            int inspectionId = parseId(
                    inspectionIdField.getText(),
                    "Inspection ID"
            );
            int technicianId = parseId(
                    technicianIdField.getText(),
                    "Technician ID"
            );

            AssetInspection inspection =
                    service.completeAssetInspection(
                            inspectionId,
                            technicianId,
                            resultField.getValue(),
                            notesField.getText(),
                            completionDatePicker.getValue()
                    );

            showSuccess(
                    statusLabel,
                    "Inspection #"
                            + inspection.getInspectionId()
                            + " completed as "
                            + inspection.getStatus()
                            + "; compliance: "
                            + (inspection.isCompliant()
                            ? "COMPLIANT"
                            : "NON-COMPLIANT")
            );

            inspectionIdField.clear();
            technicianIdField.clear();
            notesField.clear();
            resultField.setValue(InspectionStatus.PASSED);
            completionDatePicker.setValue(LocalDate.now());
            inspectionIdField.requestFocus();
        } catch (AceOsServiceException exception) {
            showError(statusLabel, exception);
        } finally {
            completeButton.setDisable(false);
        }
    }

    private int parseId(
            String value,
            String fieldName)
            throws AceOsServiceException {
        if (value == null || value.isBlank()) {
            throw new AceOsServiceException(
                    fieldName + " is required"
            );
        }

        try {
            int id = Integer.parseInt(value.trim());

            if (id <= 0) {
                throw new AceOsServiceException(
                        fieldName + " must be positive"
                );
            }

            return id;
        } catch (NumberFormatException exception) {
            throw new AceOsServiceException(
                    fieldName + " must be a whole number",
                    exception
            );
        }
    }

    private void styleSectionHeading(Label label) {
        label.setStyle(
                "-fx-font-size: 18px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );
    }

    private void showSuccess(
            Label statusLabel,
            String message) {
        statusLabel.setStyle("-fx-text-fill: #15803d;");
        statusLabel.setText(message);
    }

    private void showError(
            Label statusLabel,
            AceOsServiceException exception) {
        statusLabel.setStyle("-fx-text-fill: #b91c1c;");
        statusLabel.setText(exception.getMessage());
    }
}
