package com.aceos.ui.view;

import com.aceos.model.AssetInspection;
import com.aceos.model.InspectionStatus;
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

public class AssetInspectionDirectoryView extends VBox {

    public AssetInspectionDirectoryView(
            AceOsService service) {
        setSpacing(18);
        setPadding(new Insets(40));

        Label title = new Label("Inspection Directory");
        title.setStyle(
                "-fx-font-size: 28px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        Label description = new Label(
                "View scheduled, overdue, compliant, and "
                        + "non-compliant asset inspections."
        );
        description.setWrapText(true);
        description.setStyle(
                "-fx-font-size: 15px;"
                        + "-fx-text-fill: #475569;"
        );

        TableView<AssetInspection> table =
                new TableView<>();
        table.setPlaceholder(
                new Label("No inspections found")
        );
        table.setColumnResizePolicy(
                TableView
                        .CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        );

        TableColumn<AssetInspection, Number> idColumn =
                new TableColumn<>("ID");
        idColumn.setCellValueFactory(data ->
                new ReadOnlyIntegerWrapper(
                        data.getValue().getInspectionId()
                )
        );

        TableColumn<AssetInspection, String> assetColumn =
                new TableColumn<>("Asset");
        assetColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        data.getValue()
                                .getAsset()
                                .getAssetCode()
                )
        );

        TableColumn<AssetInspection, String> typeColumn =
                new TableColumn<>("Type");
        typeColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        data.getValue().getInspectionType()
                )
        );

        TableColumn<AssetInspection, String>
                scheduledColumn =
                new TableColumn<>("Scheduled");
        scheduledColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        data.getValue()
                                .getScheduledDate()
                                .toString()
                )
        );

        TableColumn<AssetInspection, String> statusColumn =
                new TableColumn<>("Status");
        statusColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        data.getValue().getStatus().name()
                )
        );

        TableColumn<AssetInspection, String>
                complianceColumn =
                new TableColumn<>("Compliance");
        complianceColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        getComplianceState(data.getValue())
                )
        );

        TableColumn<AssetInspection, String>
                inspectorColumn =
                new TableColumn<>("Inspector");
        inspectorColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        data.getValue().getInspectedBy() == null
                        ? "-"
                        : data.getValue()
                                .getInspectedBy()
                                .getName()
                )
        );

        TableColumn<AssetInspection, String> notesColumn =
                new TableColumn<>("Notes");
        notesColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        data.getValue().getNotes() == null
                        ? "-"
                        : data.getValue().getNotes()
                )
        );

        table.getColumns().add(idColumn);
        table.getColumns().add(assetColumn);
        table.getColumns().add(typeColumn);
        table.getColumns().add(scheduledColumn);
        table.getColumns().add(statusColumn);
        table.getColumns().add(complianceColumn);
        table.getColumns().add(inspectorColumn);
        table.getColumns().add(notesColumn);
        table.setPrefHeight(420);

        Button refreshButton =
                new Button("Refresh Inspections");
        refreshButton.setMinHeight(40);

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);

        refreshButton.setOnAction(event ->
                loadInspections(
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

        loadInspections(
                service,
                table,
                refreshButton,
                statusLabel
        );
    }

    private void loadInspections(
            AceOsService service,
            TableView<AssetInspection> table,
            Button refreshButton,
            Label statusLabel) {
        refreshButton.setDisable(true);

        try {
            table.getItems().setAll(
                    service.getAllAssetInspections()
            );
            statusLabel.setStyle(
                    "-fx-text-fill: #15803d;"
            );
            statusLabel.setText(
                    table.getItems().size()
                            + " inspections loaded"
            );
        } catch (AceOsServiceException exception) {
            statusLabel.setStyle(
                    "-fx-text-fill: #b91c1c;"
            );
            statusLabel.setText(exception.getMessage());
        } finally {
            refreshButton.setDisable(false);
        }
    }

    private String getComplianceState(
            AssetInspection inspection) {
        if (inspection.getStatus()
                == InspectionStatus.PASSED) {
            return "COMPLIANT";
        }

        if (inspection.getStatus()
                == InspectionStatus.FAILED) {
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
