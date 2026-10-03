package com.aceos.ui.view;

import com.aceos.model.MaintenanceScheduleStatus;
import com.aceos.model.PreventiveMaintenanceSchedule;
import com.aceos.service.AceOsService;
import com.aceos.service.AceOsServiceException;

import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.time.LocalDate;

public class PreventiveMaintenanceDirectoryView
        extends VBox {

    public PreventiveMaintenanceDirectoryView(
            AceOsService service) {

        setSpacing(18);
        setPadding(new Insets(40));

        Label title = new Label(
                "Preventive Maintenance Directory"
        );
        title.setStyle(
                "-fx-font-size: 28px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        Label description = new Label(
                "View recurring maintenance schedules, "
                        + "due dates, and current statuses."
        );
        description.setWrapText(true);
        description.setStyle(
                "-fx-font-size: 15px;"
                        + "-fx-text-fill: #475569;"
        );

        TableView<PreventiveMaintenanceSchedule> table =
                new TableView<>();

        table.setPlaceholder(
                new Label(
                        "No preventive maintenance "
                                + "schedules found"
                )
        );

        table.setColumnResizePolicy(
                TableView
                        .CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        );

        TableColumn<PreventiveMaintenanceSchedule, Number>
                idColumn =
                new TableColumn<>("ID");

        idColumn.setCellValueFactory(data ->
                new ReadOnlyIntegerWrapper(
                        data.getValue()
                                .getMaintenanceScheduleId()
                )
        );

        TableColumn<PreventiveMaintenanceSchedule, String>
                assetColumn =
                new TableColumn<>("Asset");

        assetColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        data.getValue()
                                .getAsset()
                                .getAssetCode()
                )
        );

        TableColumn<PreventiveMaintenanceSchedule, String>
                taskColumn =
                new TableColumn<>("Task");

        taskColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        data.getValue()
                                .getTaskDescription()
                )
        );

        TableColumn<PreventiveMaintenanceSchedule, Number>
                frequencyColumn =
                new TableColumn<>("Every (Days)");

        frequencyColumn.setCellValueFactory(data ->
                new ReadOnlyIntegerWrapper(
                        data.getValue()
                                .getFrequencyDays()
                )
        );

        TableColumn<PreventiveMaintenanceSchedule, String>
                dueDateColumn =
                new TableColumn<>("Next Due");

        dueDateColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        data.getValue()
                                .getNextDueDate()
                                .toString()
                )
        );

        TableColumn<PreventiveMaintenanceSchedule, String>
                statusColumn =
                new TableColumn<>("Status");

        statusColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        data.getValue()
                                .getStatus()
                                .name()
                )
        );

        TableColumn<PreventiveMaintenanceSchedule, String>
                timingColumn =
                new TableColumn<>("Timing");

        timingColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        getTiming(data.getValue())
                )
        );

        table.getColumns().add(idColumn);
        table.getColumns().add(assetColumn);
        table.getColumns().add(taskColumn);
        table.getColumns().add(frequencyColumn);
        table.getColumns().add(dueDateColumn);
        table.getColumns().add(statusColumn);
        table.getColumns().add(timingColumn);
        table.setPrefHeight(420);

        Button refreshButton =
                new Button("Refresh Schedules");
        refreshButton.setMinHeight(40);

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);

        refreshButton.setOnAction(event ->
                loadSchedules(
                        service,
                        table,
                        refreshButton,
                        statusLabel
                )
        );

        getChildren().addAll(
                title,
                description,
                table,
                refreshButton,
                statusLabel
        );

        loadSchedules(
                service,
                table,
                refreshButton,
                statusLabel
        );
    }

    private void loadSchedules(
            AceOsService service,
            TableView<PreventiveMaintenanceSchedule> table,
            Button refreshButton,
            Label statusLabel) {

        refreshButton.setDisable(true);

        try {
            table.getItems().setAll(
                    service
                            .getAllPreventiveMaintenanceSchedules()
            );

            statusLabel.setStyle(
                    "-fx-text-fill: #15803d;"
            );
            statusLabel.setText(
                    table.getItems().size()
                            + " maintenance schedules loaded"
            );
        } catch (AceOsServiceException exception) {
            statusLabel.setStyle(
                    "-fx-text-fill: #b91c1c;"
            );
            statusLabel.setText(
                    exception.getMessage()
            );
        } finally {
            refreshButton.setDisable(false);
        }
    }

    private String getTiming(
            PreventiveMaintenanceSchedule schedule) {

        if (schedule.getStatus()
                == MaintenanceScheduleStatus.PAUSED) {
            return "PAUSED";
        }

        if (schedule.isDueOn(LocalDate.now())) {
            return "DUE";
        }

        return "UPCOMING";
    }
}