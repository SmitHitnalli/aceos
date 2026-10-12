package com.aceos.ui.view;

import com.aceos.model.Asset;
import com.aceos.model.AssetStatus;
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

public class AssetDirectoryView extends VBox {

    public AssetDirectoryView(AceOsService service) {
        setSpacing(16);
        setPadding(new Insets(24));
        setMinHeight(0);

        Label title = new Label("Asset Directory");
        title.setStyle(
                "-fx-font-size: 28px;" + "-fx-font-weight: bold;" + "-fx-text-fill: #1e293b;");

        Label description =
                new Label("Operational availability includes all started unverified repairs and safety holds. Inspection compliance is separate.");
        description.setWrapText(true);
        description.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        description.setStyle("-fx-font-size: 15px;" + "-fx-text-fill: #475569;");

        TextField searchField = new TextField();
        searchField.setPromptText("Search ID, code, name, category, or location");

        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().add("ALL");
        for (AssetStatus status : AssetStatus.values()) {
            statusFilter.getItems().add(status.name());
        }
        statusFilter.setValue("ALL");

        HBox filters =
                new HBox(10, new Label("Search"), searchField, new Label("Status"), statusFilter);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        ObservableList<Asset> sourceItems = FXCollections.observableArrayList();
        FilteredList<Asset> filteredItems = new FilteredList<>(sourceItems, asset -> true);

        TableView<Asset> table = new TableView<>();
        table.setItems(filteredItems);
        table.setPlaceholder(new Label("No assets found"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Asset, Number> idColumn = new TableColumn<>("ID");
        idColumn.setMinWidth(44); idColumn.setPrefWidth(44); idColumn.setMaxWidth(60);
        idColumn.setCellValueFactory(
                data -> new ReadOnlyIntegerWrapper(data.getValue().getAssetId()));

        TableColumn<Asset, String> codeColumn = new TableColumn<>("Asset Code");
        codeColumn.setMinWidth(80); codeColumn.setPrefWidth(80);
        codeColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().getAssetCode()));

        TableColumn<Asset, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setMinWidth(90); nameColumn.setPrefWidth(100);
        nameColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().getName()));

        TableColumn<Asset, String> categoryColumn = new TableColumn<>("Category");
        categoryColumn.setMinWidth(65); categoryColumn.setPrefWidth(65);
        categoryColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().getCategory()));

        TableColumn<Asset, String> locationColumn = new TableColumn<>("Location");
        locationColumn.setMinWidth(70); locationColumn.setPrefWidth(70);
        locationColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().getLocation()));

        TableColumn<Asset, String> lifecycleColumn = new TableColumn<>("Manual lifecycle");
        lifecycleColumn.setMinWidth(140); lifecycleColumn.setPrefWidth(140);
        lifecycleColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().getManualLifecycle().name()));
        TableColumn<Asset, String> statusColumn = new TableColumn<>("Availability");
        statusColumn.setMinWidth(180); statusColumn.setPrefWidth(180);
        statusColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().getStatus().name()));

        table.getColumns().add(idColumn);
        table.getColumns().add(codeColumn);
        table.getColumns().add(nameColumn);
        table.getColumns().add(categoryColumn);
        table.getColumns().add(locationColumn);
        table.getColumns().add(lifecycleColumn);
        table.getColumns().add(statusColumn);
        table.setPrefHeight(420);
        table.setMinHeight(120);
        VBox.setVgrow(table, Priority.ALWAYS);

        Button refreshButton = new Button("Refresh Assets");
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
                            asset -> {
                                boolean matchesSearch =
                                        query.isEmpty()
                                                || String.valueOf(asset.getAssetId())
                                                        .contains(query)
                                                || asset.getAssetCode()
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(query)
                                                || asset.getName()
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(query)
                                                || asset.getCategory()
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(query)
                                                || asset.getLocation()
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(query);

                                boolean matchesStatus =
                                        "ALL".equals(selectedStatus)
                                                || asset.getStatus().name().equals(selectedStatus);

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
                event -> loadAssets(service, sourceItems, refreshButton, statusLabel));

        getChildren().addAll(title, description, filters, table, refreshButton, statusLabel);

        loadAssets(service, sourceItems, refreshButton, statusLabel);
    }

    private void loadAssets(
            AceOsService service,
            ObservableList<Asset> sourceItems,
            Button refreshButton,
            Label statusLabel) {
        refreshButton.setDisable(true);

        com.aceos.ui.FxTasks.run(
                () -> service.getAllAssets(),
                loaded -> {
                    sourceItems.setAll(loaded);

                    statusLabel.setStyle("-fx-text-fill: #15803d;");
                    statusLabel.setText(sourceItems.size() + " assets loaded");
                },
                exception -> {
                    statusLabel.setStyle("-fx-text-fill: #b91c1c;");
                    statusLabel.setText(exception.getMessage());
                },
                () -> {
                    refreshButton.setDisable(false);
                });
    }
}
