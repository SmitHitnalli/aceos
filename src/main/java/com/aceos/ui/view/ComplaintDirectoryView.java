package com.aceos.ui.view;

import com.aceos.model.Complaint;
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

public class ComplaintDirectoryView extends VBox {

    public ComplaintDirectoryView(
            AceOsService service) {
        setSpacing(18);
        setPadding(new Insets(40));

        Label title =
                new Label("Complaint Directory");
        title.setStyle(
                "-fx-font-size: 28px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        Label description = new Label(
                "View complaint IDs and workflow statuses "
                        + "for review and work-order assignment."
        );
        description.setWrapText(true);
        description.setStyle(
                "-fx-font-size: 15px;"
                        + "-fx-text-fill: #475569;"
        );

        TableView<Complaint> table =
                new TableView<>();
        table.setPlaceholder(
                new Label("No complaints found")
        );
        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        );

        TableColumn<Complaint, Number> idColumn =
                new TableColumn<>("ID");
        idColumn.setCellValueFactory(data ->
                new ReadOnlyIntegerWrapper(
                        data.getValue()
                                .getComplaintId()
                )
        );

        TableColumn<Complaint, String> assetColumn =
                new TableColumn<>("Asset");
        assetColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        data.getValue()
                                .getAsset()
                                .getAssetCode()
                )
        );

        TableColumn<Complaint, String>
                requesterColumn =
                new TableColumn<>("Requester");
        requesterColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        data.getValue()
                                .getReportedBy()
                                .getName()
                )
        );

        TableColumn<Complaint, String>
                priorityColumn =
                new TableColumn<>("Priority");
        priorityColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        data.getValue()
                                .getPriority()
                                .name()
                )
        );

        TableColumn<Complaint, String> statusColumn =
                new TableColumn<>("Status");
        statusColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        data.getValue()
                                .getStatus()
                                .name()
                )
        );

        TableColumn<Complaint, String> dateColumn =
                new TableColumn<>("Reported At");
        dateColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        data.getValue()
                                .getReportedAt()
                                .toString()
                                .replace('T', ' ')
                )
        );

        table.getColumns().add(idColumn);
        table.getColumns().add(assetColumn);
        table.getColumns().add(requesterColumn);
        table.getColumns().add(priorityColumn);
        table.getColumns().add(statusColumn);
        table.getColumns().add(dateColumn);
        table.setPrefHeight(420);

        Button refreshButton =
                new Button("Refresh Complaints");
        refreshButton.setMinHeight(40);

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);

        refreshButton.setOnAction(event ->
                loadComplaints(
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

        loadComplaints(
                service,
                table,
                refreshButton,
                statusLabel
        );
    }

    private void loadComplaints(
            AceOsService service,
            TableView<Complaint> table,
            Button refreshButton,
            Label statusLabel) {
        refreshButton.setDisable(true);

        try {
            table.getItems().setAll(
                    service.getAllComplaints()
            );

            statusLabel.setStyle(
                    "-fx-text-fill: #15803d;"
            );
            statusLabel.setText(
                    table.getItems().size()
                            + " complaints loaded"
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
}