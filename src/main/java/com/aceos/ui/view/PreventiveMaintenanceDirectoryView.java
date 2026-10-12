package com.aceos.ui.view;

import com.aceos.model.MaintenanceScheduleStatus;
import com.aceos.model.PreventiveMaintenanceSchedule;
import com.aceos.service.AceOsService;

import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.Locale;

public class PreventiveMaintenanceDirectoryView extends VBox {

    public PreventiveMaintenanceDirectoryView(AceOsService service) {

        setSpacing(18);
        setPadding(new Insets(40));

        Label title = new Label("Preventive Maintenance Directory");
        title.setStyle(
                "-fx-font-size: 28px;" + "-fx-font-weight: bold;" + "-fx-text-fill: #1e293b;");

        Label description =
                new Label(
                        "View recurring maintenance schedules, "
                                + "due dates, and current statuses.");
        description.setWrapText(true);
        description.setStyle("-fx-font-size: 15px;" + "-fx-text-fill: #475569;");

        TextField searchField = new TextField();
        searchField.setPromptText("Search ID, asset, task, due date, or timing");

        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().add("ALL");
        for (MaintenanceScheduleStatus status : MaintenanceScheduleStatus.values()) {
            statusFilter.getItems().add(status.name());
        }
        statusFilter.setValue("ALL");

        HBox filters =
                new HBox(10, new Label("Search"), searchField, new Label("Status"), statusFilter);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        ObservableList<PreventiveMaintenanceSchedule> sourceItems =
                FXCollections.observableArrayList();
        FilteredList<PreventiveMaintenanceSchedule> filteredItems =
                new FilteredList<>(sourceItems, schedule -> true);

        TableView<PreventiveMaintenanceSchedule> table = new TableView<>();
        table.setItems(filteredItems);

        table.setPlaceholder(new Label("No preventive maintenance " + "schedules found"));

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<PreventiveMaintenanceSchedule, Number> idColumn = new TableColumn<>("ID");

        idColumn.setCellValueFactory(
                data -> new ReadOnlyIntegerWrapper(data.getValue().getMaintenanceScheduleId()));

        TableColumn<PreventiveMaintenanceSchedule, String> assetColumn = new TableColumn<>("Asset");

        assetColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().getAsset().getAssetCode()));

        TableColumn<PreventiveMaintenanceSchedule, String> taskColumn = new TableColumn<>("Task");

        taskColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().getTaskDescription()));

        TableColumn<PreventiveMaintenanceSchedule, Number> frequencyColumn =
                new TableColumn<>("Every (Days)");

        frequencyColumn.setCellValueFactory(
                data -> new ReadOnlyIntegerWrapper(data.getValue().getFrequencyDays()));

        TableColumn<PreventiveMaintenanceSchedule, String> dueDateColumn =
                new TableColumn<>("Next Due");

        dueDateColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().getNextDueDate().toString()));

        TableColumn<PreventiveMaintenanceSchedule, String> statusColumn =
                new TableColumn<>("Status");

        statusColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().getStatus().name()));

        TableColumn<PreventiveMaintenanceSchedule, String> timingColumn =
                new TableColumn<>("Timing");

        timingColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(getTiming(data.getValue())));

        table.getColumns().add(idColumn);
        table.getColumns().add(assetColumn);
        table.getColumns().add(taskColumn);
        table.getColumns().add(frequencyColumn);
        table.getColumns().add(dueDateColumn);
        table.getColumns().add(statusColumn);
        table.getColumns().add(timingColumn);
        table.setPrefHeight(420);

        Button refreshButton = new Button("Refresh Schedules");
        refreshButton.setMinHeight(40);

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);

        Runnable applyFilters =
                () -> {
                    String query =
                            searchField.getText() == null
                                    ? ""
                                    : searchField.getText().trim().toLowerCase(Locale.ROOT);
                    String selectedStatus = statusFilter.getValue();

                    filteredItems.setPredicate(
                            schedule -> {
                                boolean matchesSearch =
                                        query.isEmpty()
                                                || String.valueOf(
                                                                schedule.getMaintenanceScheduleId())
                                                        .contains(query)
                                                || schedule.getAsset()
                                                        .getAssetCode()
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(query)
                                                || schedule.getTaskDescription()
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(query)
                                                || String.valueOf(schedule.getFrequencyDays())
                                                        .contains(query)
                                                || schedule.getNextDueDate()
                                                        .toString()
                                                        .contains(query)
                                                || getTiming(schedule)
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(query);

                                boolean matchesStatus =
                                        "ALL".equals(selectedStatus)
                                                || schedule.getStatus()
                                                        .name()
                                                        .equals(selectedStatus);

                                return matchesSearch && matchesStatus;
                            });
                };

        searchField
                .textProperty()
                .addListener((observable, oldValue, newValue) -> applyFilters.run());
        statusFilter
                .valueProperty()
                .addListener((observable, oldValue, newValue) -> applyFilters.run());

        refreshButton.setOnAction(
                event -> loadSchedules(service, sourceItems, refreshButton, statusLabel));

        getChildren().addAll(title, description, filters, table, refreshButton, statusLabel);

        loadSchedules(service, sourceItems, refreshButton, statusLabel);
    }

    private void loadSchedules(
            AceOsService service,
            ObservableList<PreventiveMaintenanceSchedule> sourceItems,
            Button refreshButton,
            Label statusLabel) {

        refreshButton.setDisable(true);

        com.aceos.ui.FxTasks.run(
                () -> service.getAllPreventiveMaintenanceSchedules(),
                loaded -> {
                    sourceItems.setAll(loaded);

                    statusLabel.setStyle("-fx-text-fill: #15803d;");
                    statusLabel.setText(sourceItems.size() + " maintenance schedules loaded");
                },
                exception -> {
                    statusLabel.setStyle("-fx-text-fill: #b91c1c;");
                    statusLabel.setText(exception.getMessage());
                },
                () -> {
                    refreshButton.setDisable(false);
                });
    }

    private String getTiming(PreventiveMaintenanceSchedule schedule) {

        if (schedule.getStatus() == MaintenanceScheduleStatus.PAUSED) {
            return "PAUSED";
        }

        if (schedule.isDueOn(LocalDate.now())) {
            return "DUE";
        }

        return "UPCOMING";
    }
}
