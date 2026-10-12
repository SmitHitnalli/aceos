package com.aceos.ui.view;

import com.aceos.model.WorkOrder;
import com.aceos.model.WorkOrderStatus;
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

import java.util.Locale;

public class WorkOrderDirectoryView extends VBox {

    public WorkOrderDirectoryView(AceOsService service) {
        setSpacing(18);
        setPadding(new Insets(40));

        Label title = new Label("Work Order Directory");
        title.setStyle(
                "-fx-font-size: 28px;" + "-fx-font-weight: bold;" + "-fx-text-fill: #1e293b;");

        Label description =
                new Label("View work-order IDs, assignments, " + "and current lifecycle statuses.");
        description.setWrapText(true);
        description.setStyle("-fx-font-size: 15px;" + "-fx-text-fill: #475569;");

        TextField searchField = new TextField();
        searchField.setPromptText("Search ID, complaint, asset, or technician");

        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().add("ALL");
        for (WorkOrderStatus status : WorkOrderStatus.values()) {
            statusFilter.getItems().add(status.name());
        }
        statusFilter.setValue("ALL");

        HBox filters =
                new HBox(10, new Label("Search"), searchField, new Label("Status"), statusFilter);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        ObservableList<WorkOrder> sourceItems = FXCollections.observableArrayList();
        FilteredList<WorkOrder> filteredItems = new FilteredList<>(sourceItems, workOrder -> true);

        TableView<WorkOrder> table = new TableView<>();
        table.setItems(filteredItems);
        table.setPlaceholder(new Label("No work orders found"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<WorkOrder, Number> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(
                data -> new ReadOnlyIntegerWrapper(data.getValue().getWorkOrderId()));

        TableColumn<WorkOrder, Number> complaintColumn = new TableColumn<>("Complaint");
        complaintColumn.setCellValueFactory(
                data ->
                        new ReadOnlyIntegerWrapper(
                                data.getValue().getComplaint().getComplaintId()));

        TableColumn<WorkOrder, String> assetColumn = new TableColumn<>("Asset");
        assetColumn.setCellValueFactory(
                data ->
                        new ReadOnlyStringWrapper(
                                data.getValue().getComplaint().getAsset().getAssetCode()));

        TableColumn<WorkOrder, String> technicianColumn = new TableColumn<>("Technician");
        technicianColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().getTechnician().getName()));

        TableColumn<WorkOrder, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().getStatus().name()));

        TableColumn<WorkOrder, String> activityColumn = new TableColumn<>("Latest Activity");
        activityColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(getLatestActivity(data.getValue())));

        table.getColumns().add(idColumn);
        table.getColumns().add(complaintColumn);
        table.getColumns().add(assetColumn);
        table.getColumns().add(technicianColumn);
        table.getColumns().add(statusColumn);
        table.getColumns().add(activityColumn);
        table.setPrefHeight(420);

        Button refreshButton = new Button("Refresh Work Orders");
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
                            workOrder -> {
                                boolean matchesSearch =
                                        query.isEmpty()
                                                || String.valueOf(workOrder.getWorkOrderId())
                                                        .contains(query)
                                                || String.valueOf(
                                                                workOrder
                                                                        .getComplaint()
                                                                        .getComplaintId())
                                                        .contains(query)
                                                || workOrder
                                                        .getComplaint()
                                                        .getAsset()
                                                        .getAssetCode()
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(query)
                                                || workOrder
                                                        .getTechnician()
                                                        .getName()
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(query);

                                boolean matchesStatus =
                                        "ALL".equals(selectedStatus)
                                                || workOrder
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
                event -> loadWorkOrders(service, sourceItems, refreshButton, statusLabel));

        getChildren().addAll(title, description, filters, table, refreshButton, statusLabel);

        loadWorkOrders(service, sourceItems, refreshButton, statusLabel);
    }

    private void loadWorkOrders(
            AceOsService service,
            ObservableList<WorkOrder> sourceItems,
            Button refreshButton,
            Label statusLabel) {
        refreshButton.setDisable(true);

        com.aceos.ui.FxTasks.run(
                () -> service.getAllWorkOrders(),
                loaded -> {
                    sourceItems.setAll(loaded);

                    statusLabel.setStyle("-fx-text-fill: #15803d;");
                    statusLabel.setText(sourceItems.size() + " work orders loaded");
                },
                exception -> {
                    statusLabel.setStyle("-fx-text-fill: #b91c1c;");
                    statusLabel.setText(exception.getMessage());
                },
                () -> {
                    refreshButton.setDisable(false);
                });
    }

    private String getLatestActivity(WorkOrder workOrder) {
        if (workOrder.getVerifiedAt() != null) {
            return workOrder.getVerifiedAt().toString().replace('T', ' ');
        }

        if (workOrder.getCompletedAt() != null) {
            return workOrder.getCompletedAt().toString().replace('T', ' ');
        }

        if (workOrder.getStartedAt() != null) {
            return workOrder.getStartedAt().toString().replace('T', ' ');
        }

        return workOrder.getAssignedAt().toString().replace('T', ' ');
    }
}
