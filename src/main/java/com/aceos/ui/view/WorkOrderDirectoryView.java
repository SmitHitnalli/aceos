package com.aceos.ui.view;

import com.aceos.model.WorkOrder;
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

public class WorkOrderDirectoryView extends VBox {

    public WorkOrderDirectoryView(
            AceOsService service) {
        setSpacing(18);
        setPadding(new Insets(40));

        Label title =
                new Label("Work Order Directory");
        title.setStyle(
                "-fx-font-size: 28px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        Label description = new Label(
                "View work-order IDs, assignments, "
                        + "and current lifecycle statuses."
        );
        description.setWrapText(true);
        description.setStyle(
                "-fx-font-size: 15px;"
                        + "-fx-text-fill: #475569;"
        );

        TableView<WorkOrder> table =
                new TableView<>();
        table.setPlaceholder(
                new Label("No work orders found")
        );
        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        );

        TableColumn<WorkOrder, Number> idColumn =
                new TableColumn<>("ID");
        idColumn.setCellValueFactory(data ->
                new ReadOnlyIntegerWrapper(
                        data.getValue()
                                .getWorkOrderId()
                )
        );

        TableColumn<WorkOrder, Number>
                complaintColumn =
                new TableColumn<>("Complaint");
        complaintColumn.setCellValueFactory(data ->
                new ReadOnlyIntegerWrapper(
                        data.getValue()
                                .getComplaint()
                                .getComplaintId()
                )
        );

        TableColumn<WorkOrder, String> assetColumn =
                new TableColumn<>("Asset");
        assetColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        data.getValue()
                                .getComplaint()
                                .getAsset()
                                .getAssetCode()
                )
        );

        TableColumn<WorkOrder, String>
                technicianColumn =
                new TableColumn<>("Technician");
        technicianColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        data.getValue()
                                .getTechnician()
                                .getName()
                )
        );

        TableColumn<WorkOrder, String> statusColumn =
                new TableColumn<>("Status");
        statusColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        data.getValue()
                                .getStatus()
                                .name()
                )
        );

        TableColumn<WorkOrder, String>
                activityColumn =
                new TableColumn<>("Latest Activity");
        activityColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        getLatestActivity(
                                data.getValue()
                        )
                )
        );

        table.getColumns().add(idColumn);
        table.getColumns().add(complaintColumn);
        table.getColumns().add(assetColumn);
        table.getColumns().add(technicianColumn);
        table.getColumns().add(statusColumn);
        table.getColumns().add(activityColumn);
        table.setPrefHeight(420);

        Button refreshButton =
                new Button("Refresh Work Orders");
        refreshButton.setMinHeight(40);

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);

        refreshButton.setOnAction(event ->
                loadWorkOrders(
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

        loadWorkOrders(
                service,
                table,
                refreshButton,
                statusLabel
        );
    }

    private void loadWorkOrders(
            AceOsService service,
            TableView<WorkOrder> table,
            Button refreshButton,
            Label statusLabel) {
        refreshButton.setDisable(true);

        try {
            table.getItems().setAll(
                    service.getAllWorkOrders()
            );

            statusLabel.setStyle(
                    "-fx-text-fill: #15803d;"
            );
            statusLabel.setText(
                    table.getItems().size()
                            + " work orders loaded"
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

    private String getLatestActivity(
            WorkOrder workOrder) {
        if (workOrder.getVerifiedAt() != null) {
            return workOrder
                    .getVerifiedAt()
                    .toString()
                    .replace('T', ' ');
        }

        if (workOrder.getCompletedAt() != null) {
            return workOrder
                    .getCompletedAt()
                    .toString()
                    .replace('T', ' ');
        }

        if (workOrder.getStartedAt() != null) {
            return workOrder
                    .getStartedAt()
                    .toString()
                    .replace('T', ' ');
        }

        return workOrder
                .getAssignedAt()
                .toString()
                .replace('T', ' ');
    }
}