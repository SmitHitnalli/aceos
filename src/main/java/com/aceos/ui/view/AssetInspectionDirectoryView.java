package com.aceos.ui.view;

import com.aceos.model.AssetInspection;
import com.aceos.model.InspectionStatus;
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

public class AssetInspectionDirectoryView extends VBox {

    public AssetInspectionDirectoryView(AceOsService service) {
        setSpacing(18);
        setPadding(new Insets(40));

        Label title = new Label("Inspection Directory");
        title.setStyle(
                "-fx-font-size: 28px;" + "-fx-font-weight: bold;" + "-fx-text-fill: #1e293b;");

        Label description =
                new Label(
                        "View scheduled, overdue, compliant, and "
                                + "non-compliant asset inspections.");
        description.setWrapText(true);
        description.setStyle("-fx-font-size: 15px;" + "-fx-text-fill: #475569;");

        TextField searchField = new TextField();
        searchField.setPromptText("Search ID, asset, type, inspector, or compliance");

        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().add("ALL");
        for (InspectionStatus status : InspectionStatus.values()) {
            statusFilter.getItems().add(status.name());
        }
        statusFilter.setValue("ALL");

        HBox filters =
                new HBox(10, new Label("Search"), searchField, new Label("Status"), statusFilter);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        ObservableList<AssetInspection> sourceItems = FXCollections.observableArrayList();
        FilteredList<AssetInspection> filteredItems =
                new FilteredList<>(sourceItems, inspection -> true);

        TableView<AssetInspection> table = new TableView<>();
        table.setItems(filteredItems);
        table.setPlaceholder(new Label("No inspections found"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<AssetInspection, Number> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(
                data -> new ReadOnlyIntegerWrapper(data.getValue().getInspectionId()));

        TableColumn<AssetInspection, String> assetColumn = new TableColumn<>("Asset");
        assetColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().getAsset().getAssetCode()));

        TableColumn<AssetInspection, String> typeColumn = new TableColumn<>("Type");
        typeColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().getInspectionType()));

        TableColumn<AssetInspection, String> scheduledColumn = new TableColumn<>("Scheduled");
        scheduledColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().getScheduledDate().toString()));

        TableColumn<AssetInspection, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().getStatus().name()));

        TableColumn<AssetInspection, String> complianceColumn = new TableColumn<>("Compliance");
        complianceColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(getComplianceState(data.getValue())));

        TableColumn<AssetInspection, String> inspectorColumn = new TableColumn<>("Inspector");
        inspectorColumn.setCellValueFactory(
                data ->
                        new ReadOnlyStringWrapper(
                                data.getValue().getInspectedBy() == null
                                        ? "-"
                                        : data.getValue().getInspectedBy().getName()));

        TableColumn<AssetInspection, String> notesColumn = new TableColumn<>("Notes");
        notesColumn.setCellValueFactory(
                data ->
                        new ReadOnlyStringWrapper(
                                data.getValue().getNotes() == null
                                        ? "-"
                                        : data.getValue().getNotes()));

        table.getColumns().add(idColumn);
        table.getColumns().add(assetColumn);
        table.getColumns().add(typeColumn);
        table.getColumns().add(scheduledColumn);
        table.getColumns().add(statusColumn);
        table.getColumns().add(complianceColumn);
        table.getColumns().add(inspectorColumn);
        table.getColumns().add(notesColumn);
        table.setPrefHeight(420);

        Button refreshButton = new Button("Refresh Inspections");
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
                            inspection -> {
                                String inspectorName =
                                        inspection.getInspectedBy() == null
                                                ? ""
                                                : inspection.getInspectedBy().getName();
                                String notes =
                                        inspection.getNotes() == null ? "" : inspection.getNotes();

                                boolean matchesSearch =
                                        query.isEmpty()
                                                || String.valueOf(inspection.getInspectionId())
                                                        .contains(query)
                                                || inspection
                                                        .getAsset()
                                                        .getAssetCode()
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(query)
                                                || inspection
                                                        .getInspectionType()
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(query)
                                                || inspection
                                                        .getScheduledDate()
                                                        .toString()
                                                        .contains(query)
                                                || inspectorName
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(query)
                                                || notes.toLowerCase(Locale.ROOT).contains(query)
                                                || getComplianceState(inspection)
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(query);

                                boolean matchesStatus =
                                        "ALL".equals(selectedStatus)
                                                || inspection
                                                        .getStatus()
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
                event -> loadInspections(service, sourceItems, refreshButton, statusLabel));

        TableColumn<AssetInspection, String> assignmentColumn =
                new TableColumn<>("Assigned technician");
        assignmentColumn.setCellValueFactory(
                data ->
                        new javafx.beans.property.SimpleStringProperty(
                                data.getValue().getAssignedTechnicianId() == 0
                                        ? "Unassigned"
                                        : "#" + data.getValue().getAssignedTechnicianId()));
        table.getColumns().add(assignmentColumn);
        getChildren().addAll(title, description, filters, table, refreshButton, statusLabel);

        loadInspections(service, sourceItems, refreshButton, statusLabel);
    }

    private void loadInspections(
            AceOsService service,
            ObservableList<AssetInspection> sourceItems,
            Button refreshButton,
            Label statusLabel) {
        refreshButton.setDisable(true);

        com.aceos.ui.FxTasks.run(
                () -> service.getAllAssetInspections(),
                loaded -> {
                    sourceItems.setAll(loaded);
                    statusLabel.setStyle("-fx-text-fill: #15803d;");
                    statusLabel.setText(sourceItems.size() + " inspections loaded");
                },
                exception -> {
                    statusLabel.setStyle("-fx-text-fill: #b91c1c;");
                    statusLabel.setText(exception.getMessage());
                },
                () -> {
                    refreshButton.setDisable(false);
                });
    }

    private String getComplianceState(AssetInspection inspection) {
        if (inspection.getStatus() == InspectionStatus.PASSED) {
            return "COMPLIANT";
        }

        if (inspection.getStatus() == InspectionStatus.FAILED) {
            return "NON-COMPLIANT";
        }

        LocalDate today = LocalDate.now();

        if (inspection.isOverdueOn(today)) {
            return "OVERDUE";
        }

        if (inspection.getScheduledDate().equals(today)) {
            return "DUE TODAY";
        }

        return "UPCOMING";
    }
}
