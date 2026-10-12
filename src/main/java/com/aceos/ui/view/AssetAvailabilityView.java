package com.aceos.ui.view;

import com.aceos.model.*;
import com.aceos.service.AceOsService;
import com.aceos.ui.FxTasks;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.List;

/** Minimal administrator controls; connected asset profiles belong to Phase 3. */
public class AssetAvailabilityView extends VBox {
    private Asset loaded;
    private final TextField assetId = new TextField();
    private final Label summary = new Label("Load an asset to review its lifecycle and availability.");
    private final Label feedback = new Label();
    private final ComboBox<ManualLifecycle> lifecycle = new ComboBox<>();
    private final ComboBox<SafetyHold> hold = new ComboBox<>();
    private final ComboBox<String> evidenceType = new ComboBox<>();
    private final TextField evidenceId = new TextField();
    private final TextField reason = new TextField();
    private final Button change = new Button("Save lifecycle");
    private final Button clear = new Button("Clear selected hold");

    public AssetAvailabilityView(AceOsService service) {
        setPadding(new Insets(16)); setSpacing(8);
        Label title = new Label("Asset availability & safety holds");
        title.setWrapText(true);
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #1e3a5f;");
        assetId.setPromptText("Asset ID"); assetId.setId("availability-asset-id");
        Button load = new Button("Load asset");
        load.setId("availability-load");
        HBox lookup = new HBox(8, new Label("Asset ID"), assetId, load);
        HBox.setHgrow(assetId, Priority.ALWAYS);
        summary.setWrapText(true); summary.setId("availability-summary");
        lifecycle.getItems().setAll(ManualLifecycle.values()); lifecycle.setMaxWidth(Double.MAX_VALUE);
        hold.setMaxWidth(Double.MAX_VALUE); hold.setId("availability-hold");
        evidenceType.getItems().setAll("Passing reinspection", "Verified corrective repair");
        evidenceType.setValue("Passing reinspection"); evidenceType.setMaxWidth(Double.MAX_VALUE);
        evidenceId.setPromptText("Supporting inspection or work-order ID");
        reason.setPromptText("Required reason (up to 1000 characters)"); reason.setId("availability-reason");
        GridPane form = new GridPane(); form.setHgap(8); form.setVgap(8);
        ColumnConstraints labels = new ColumnConstraints(); labels.setMinWidth(140);
        form.getColumnConstraints().add(labels);
        form.addRow(0, new Label("Manual lifecycle"), lifecycle);
        form.addRow(1, new Label("Safety hold"), hold);
        form.addRow(2, new Label("Clearance evidence"), evidenceType);
        form.addRow(3, new Label("Evidence ID"), evidenceId);
        form.addRow(4, new Label("Reason"), reason);
        for (var node : List.of(lifecycle, hold, evidenceType, evidenceId, reason)) GridPane.setHgrow(node, Priority.ALWAYS);
        Label rules = new Label("Retired → manual/safety hold → started unverified repair → Active. "
                + "Inspection compliance is separate. Clearing one hold preserves all others.");
        rules.setWrapText(true);
        feedback.setWrapText(true); feedback.setId("availability-feedback");
        change.setId("availability-change"); clear.setId("availability-clear");
        assetId.textProperty().addListener((o, before, after) -> {
            loaded = null; hold.getItems().clear(); change.setDisable(true); clear.setDisable(true);
            summary.setText("Load the asset before making changes.");
        });
        change.setDisable(true); clear.setDisable(true);
        load.setOnAction(e -> {
            try { reload(service, Integer.parseInt(assetId.getText().trim()), ""); }
            catch (NumberFormatException error) { failed("Enter a numeric asset ID"); }
        });
        change.setOnAction(e -> {
            if (loaded == null) return;
            int id = loaded.getAssetId(); ManualLifecycle expected = loaded.getManualLifecycle();
            ManualLifecycle next = lifecycle.getValue(); String why = reason.getText();
            setDisable(true);
            FxTasks.run(() -> {
                Asset asset = service.changeAssetLifecycle(id, expected, next, why);
                return new State(asset, service.getSafetyHolds(id));
            }, state -> show(state, "Lifecycle saved"), error -> failed(error.getMessage()), () -> setDisable(false));
        });
        clear.setOnAction(e -> {
            if (loaded == null || hold.getValue() == null) { failed("Select an active hold"); return; }
            try {
                int id = loaded.getAssetId(), holdId = hold.getValue().holdId();
                int proof = Integer.parseInt(evidenceId.getText().trim());
                boolean passing = evidenceType.getValue().equals("Passing reinspection");
                String why = reason.getText(); setDisable(true);
                FxTasks.run(() -> {
                    Asset asset = service.clearSafetyHold(id, holdId, why, passing ? proof : null, passing ? null : proof);
                    return new State(asset, service.getSafetyHolds(id));
                }, state -> show(state, "Hold cleared; other restrictions remain in force"),
                        error -> failed(error.getMessage()), () -> setDisable(false));
            } catch (NumberFormatException error) { failed("Enter a numeric evidence ID"); }
        });
        getChildren().addAll(title, lookup, summary, form, new HBox(8, change, clear), rules, feedback);
    }

    private record State(Asset asset, List<SafetyHold> holds) {}
    private void reload(AceOsService service, int id, String message) {
        setDisable(true);
        FxTasks.run(() -> new State(service.getAsset(id), service.getSafetyHolds(id)),
                state -> show(state, message), error -> failed(error.getMessage()), () -> setDisable(false));
    }
    private void show(State state, String message) {
        loaded = state.asset(); lifecycle.setValue(loaded.getManualLifecycle());
        hold.getItems().setAll(state.holds().stream().filter(h -> h.clearedAt() == null).toList());
        hold.getSelectionModel().selectFirst();
        summary.setText(loaded.getAssetCode() + " · " + loaded.getName() + " · lifecycle: "
                + loaded.getManualLifecycle() + " · availability: " + loaded.getStatus()
                + " · active safety holds: " + hold.getItems().size());
        change.setDisable(loaded.getManualLifecycle() == ManualLifecycle.RETIRED);
        clear.setDisable(hold.getItems().isEmpty());
        feedback.setStyle("-fx-text-fill: #15803d;"); feedback.setText(message);
    }
    private void failed(String message) {
        feedback.setStyle("-fx-text-fill: #b91c1c;"); feedback.setText(message);
    }
}
