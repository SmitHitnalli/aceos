package com.aceos.ui.view;

import com.aceos.model.*;
import com.aceos.service.AceOsService;
import com.aceos.ui.FxTasks;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import java.util.Locale;
import java.util.function.Function;

/** Searchable authorized assets with exact-record profile navigation. */
public class AssetDirectoryView extends BorderPane {
    private final AceOsService service;
    private final ScrollPane list = new ScrollPane();
    private final javafx.collections.ObservableList<Asset> assets = FXCollections.observableArrayList();
    private final TableView<Asset> table = new TableView<>();
    private final Button refresh = new Button("Refresh assets"), open = new Button("Open selected asset");
    private final Label feedback = new Label();
    private boolean busy;

    public AssetDirectoryView(AceOsService service) { this(service, false); }

    public AssetDirectoryView(AceOsService service, boolean registration) {
        this.service = service; setId("asset-directory");
        VBox content = new VBox(12); content.setPadding(new Insets(24)); content.setMinWidth(0);
        Label title = new Label("Assets"); title.getStyleClass().add("title");
        Label description = new Label("Open an asset to review its availability and authorized history.");
        description.setWrapText(true); description.getStyleClass().add("muted");
        TextField search = new TextField(); search.setId("asset-search");
        search.setPromptText("Search ID, code, name, category or location");
        Label searchLabel = new Label("Search assets"); searchLabel.setLabelFor(search);
        ComboBox<String> status = new ComboBox<>(); status.setId("asset-status"); status.getItems().add("ALL");
        for (var value : AssetStatus.values()) status.getItems().add(value.name()); status.setValue("ALL");
        Label statusLabel = new Label("Effective availability"); statusLabel.setLabelFor(status);
        var filtered = new FilteredList<>(assets);
        Runnable apply = () -> {
            String query = search.getText().trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(a -> ("ALL".equals(status.getValue()) || a.getStatus().name().equals(status.getValue()))
                    && (a.getAssetId() + " " + a.getAssetCode() + " " + a.getName() + " " + a.getCategory() + " " + a.getLocation())
                    .toLowerCase(Locale.ROOT).contains(query));
        };
        search.textProperty().addListener((o, a, b) -> apply.run()); status.valueProperty().addListener((o, a, b) -> apply.run());
        table.setId("asset-table"); table.setItems(filtered); table.getStyleClass().add("repair-table");
        table.setAccessibleText("Authorized assets. Select a row and press Enter to open its profile.");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        column("Asset", a -> "#" + a.getAssetId() + " · " + a.getAssetCode(), 120);
        column("Name / category", a -> a.getName() + " · " + a.getCategory(), 220);
        column("Location", Asset::getLocation, 100);
        column("Manual lifecycle", a -> a.getManualLifecycle().name(), 130);
        column("Availability", a -> a.getStatus().name(), 160);
        table.setPrefHeight(300); table.setMinHeight(180);
        table.setPlaceholder(new Label("No assets match. Change search/availability or refresh."));
        open.setId("asset-open"); open.getStyleClass().add("primary"); open.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> open.setDisable(b == null || busy));
        open.setOnAction(e -> openSelected());
        table.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) { openSelected(); e.consume(); } });
        table.setRowFactory(t -> {
            TableRow<Asset> row = new TableRow<>();
            row.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && !row.isEmpty()) openSelected(); }); return row;
        });
        refresh.setId("asset-refresh"); refresh.setOnAction(e -> load());
        feedback.setId("asset-feedback"); feedback.setWrapText(true);
        content.getChildren().addAll(new FlowPane(12, 8, title, refresh), description, feedback,
                searchLabel, search, new FlowPane(8, 8, statusLabel, status), table, open);
        try {
            if (service.signedInUser().getRole() == UserRole.ADMINISTRATOR) {
                Button register = new Button("Register asset"); register.setId("asset-register");
                register.getStyleClass().add("primary"); register.setOnAction(e -> showRegistration());
                content.getChildren().add(2, register);
            }
        } catch (com.aceos.service.AceOsServiceException ignored) { /* Authorized reads still report session errors. */ }
        list.getStyleClass().add("dashboard"); list.setFitToWidth(true); list.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); list.setContent(content);
        setCenter(list);
        if (registration && content.lookup("#asset-register") != null) showRegistration(); else load();
    }
    private void showRegistration() {
        setCenter(new AssetManagementView(service, () -> { setCenter(list); load(); }));
    }
    private void column(String title, Function<Asset, String> display, int width) {
        TableColumn<Asset, String> column = new TableColumn<>(title); column.setMinWidth(80); column.setPrefWidth(width);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(display.apply(data.getValue()))); table.getColumns().add(column);
    }
    private void openSelected() {
        if (busy || table.getSelectionModel().getSelectedItem() == null) return;
        int id = table.getSelectionModel().getSelectedItem().getAssetId();
        setCenter(new AssetProfileView(service, id, () -> { setCenter(list); load(); }));
    }
    private void load() {
        if (busy) return;
        busy = true; refresh.setDisable(true); open.setDisable(true); feedback.setText("Loading authorized assets…");
        FxTasks.run(service::getAllAssets, loaded -> {
            assets.setAll(loaded); feedback.setText(loaded.isEmpty() ? "No assets available for your account." : loaded.size() + " assets loaded.");
            feedback.getStyleClass().remove("error"); refresh.setText("Refresh assets");
        }, error -> {
            assets.clear(); feedback.setText(error.getMessage() + " Retry to reload.");
            if (!feedback.getStyleClass().contains("error")) feedback.getStyleClass().add("error"); refresh.setText("Retry");
        }, () -> { busy = false; refresh.setDisable(false); open.setDisable(table.getSelectionModel().getSelectedItem() == null); });
    }
}
