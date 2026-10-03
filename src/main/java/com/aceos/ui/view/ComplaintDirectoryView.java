package com.aceos.ui.view;

import com.aceos.model.Complaint;
import com.aceos.model.ComplaintStatus;
import com.aceos.service.AceOsService;
import com.aceos.service.AceOsServiceException;

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

        TextField searchField = new TextField();
        searchField.setPromptText(
                "Search ID, asset, requester, priority, or text"
        );

        ComboBox<String> statusFilter =
                new ComboBox<>();
        statusFilter.getItems().add("ALL");
        for (ComplaintStatus status : ComplaintStatus.values()) {
            statusFilter.getItems().add(status.name());
        }
        statusFilter.setValue("ALL");

        HBox filters = new HBox(
                10,
                new Label("Search"),
                searchField,
                new Label("Status"),
                statusFilter
        );
        HBox.setHgrow(searchField, Priority.ALWAYS);

        ObservableList<Complaint> sourceItems =
                FXCollections.observableArrayList();
        FilteredList<Complaint> filteredItems =
                new FilteredList<>(
                        sourceItems,
                        complaint -> true
                );

        TableView<Complaint> table =
                new TableView<>();
        table.setItems(filteredItems);
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

        Runnable applyFilters = () -> {
            String query = searchField.getText() == null
                    ? ""
                    : searchField.getText().trim()
                            .toLowerCase(Locale.ROOT);
            String selectedStatus = statusFilter.getValue();

            filteredItems.setPredicate(complaint -> {
                boolean matchesSearch = query.isEmpty()
                        || String.valueOf(
                                complaint.getComplaintId()
                        ).contains(query)
                        || complaint.getAsset().getAssetCode()
                                .toLowerCase(Locale.ROOT)
                                .contains(query)
                        || complaint.getReportedBy().getName()
                                .toLowerCase(Locale.ROOT)
                                .contains(query)
                        || complaint.getPriority().name()
                                .toLowerCase(Locale.ROOT)
                                .contains(query)
                        || complaint.getDescription()
                                .toLowerCase(Locale.ROOT)
                                .contains(query);

                boolean matchesStatus =
                        "ALL".equals(selectedStatus)
                        || complaint.getStatus().name()
                                .equals(selectedStatus);

                return matchesSearch && matchesStatus;
            });
        };

        searchField.textProperty().addListener(
                (observable, oldValue, newValue) ->
                        applyFilters.run()
        );
        statusFilter.valueProperty().addListener(
                (observable, oldValue, newValue) ->
                        applyFilters.run()
        );

        refreshButton.setOnAction(event ->
                loadComplaints(
                        service,
                        sourceItems,
                        refreshButton,
                        statusLabel
                )
        );

        getChildren().addAll(
                title,
                description,
                filters,
                table,
                refreshButton,
                statusLabel
        );

        loadComplaints(
                service,
                sourceItems,
                refreshButton,
                statusLabel
        );
    }

    private void loadComplaints(
            AceOsService service,
            ObservableList<Complaint> sourceItems,
            Button refreshButton,
            Label statusLabel) {
        refreshButton.setDisable(true);

        try {
            sourceItems.setAll(
                    service.getAllComplaints()
            );

            statusLabel.setStyle(
                    "-fx-text-fill: #15803d;"
            );
            statusLabel.setText(
                    sourceItems.size()
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
