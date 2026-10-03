package com.aceos.ui.view;

import com.aceos.model.Asset;
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

public class AssetDirectoryView extends VBox {

    public AssetDirectoryView(
            AceOsService service) {
        setSpacing(18);
        setPadding(new Insets(40));

        Label title = new Label("Asset Directory");
        title.setStyle(
                "-fx-font-size: 28px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        Label description = new Label(
                "View asset IDs and current statuses "
                        + "for complaint submission."
        );
        description.setWrapText(true);
        description.setStyle(
                "-fx-font-size: 15px;"
                        + "-fx-text-fill: #475569;"
        );

        TableView<Asset> table = new TableView<>();
        table.setPlaceholder(
                new Label("No assets found")
        );
        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        );

        TableColumn<Asset, Number> idColumn =
                new TableColumn<>("ID");
        idColumn.setCellValueFactory(data ->
                new ReadOnlyIntegerWrapper(
                        data.getValue().getAssetId()
                )
        );

        TableColumn<Asset, String> codeColumn =
                new TableColumn<>("Asset Code");
        codeColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        data.getValue().getAssetCode()
                )
        );

        TableColumn<Asset, String> nameColumn =
                new TableColumn<>("Name");
        nameColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        data.getValue().getName()
                )
        );

        TableColumn<Asset, String> categoryColumn =
                new TableColumn<>("Category");
        categoryColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        data.getValue().getCategory()
                )
        );

        TableColumn<Asset, String> locationColumn =
                new TableColumn<>("Location");
        locationColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        data.getValue().getLocation()
                )
        );

        TableColumn<Asset, String> statusColumn =
                new TableColumn<>("Status");
        statusColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(
                        data.getValue()
                                .getStatus()
                                .name()
                )
        );

        table.getColumns().add(idColumn);
        table.getColumns().add(codeColumn);
        table.getColumns().add(nameColumn);
        table.getColumns().add(categoryColumn);
        table.getColumns().add(locationColumn);
        table.getColumns().add(statusColumn);
        table.setPrefHeight(420);

        Button refreshButton =
                new Button("Refresh Assets");
        refreshButton.setMinHeight(40);

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);

        refreshButton.setOnAction(event ->
                loadAssets(
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

        loadAssets(
                service,
                table,
                refreshButton,
                statusLabel
        );
    }

    private void loadAssets(
            AceOsService service,
            TableView<Asset> table,
            Button refreshButton,
            Label statusLabel) {
        refreshButton.setDisable(true);

        try {
            table.getItems().setAll(
                    service.getAllAssets()
            );

            statusLabel.setStyle(
                    "-fx-text-fill: #15803d;"
            );
            statusLabel.setText(
                    table.getItems().size()
                            + " assets loaded"
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