package com.aceos.ui.view;

import com.aceos.model.*;
import com.aceos.service.AceOsService;
import com.aceos.ui.FxTasks;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.function.IntConsumer;

public class ComplaintManagementView extends ScrollPane {
    private final AceOsService service;
    private final SearchSelector<Asset> assets = new SearchSelector<>("Asset", "report-asset",
            a -> a.getAssetCode() + " · " + a.getName() + " · " + a.getLocation() + " · " + a.getStatus(), Asset::getAssetId);
    private final Button refresh = new Button("Refresh assets");
    private final Button submit = new Button("Submit complaint");
    private final TextArea description = new TextArea();
    private final ComboBox<ComplaintPriority> priority = new ComboBox<>();
    private final Label feedback = new Label();
    private final Button progress = new Button("View submitted complaint");
    private boolean busy, saving;

    public ComplaintManagementView(AceOsService service) { this(service, id -> {}); }

    public ComplaintManagementView(AceOsService service, IntConsumer openComplaint) {
        this.service = service;
        com.aceos.ui.NavigationGuard.install(this, () -> saving, () -> !description.getText().isEmpty() || assets.selected() != null || priority.getValue() != null);
        setId("complaint-report"); setFitToWidth(true);
        VBox content = new VBox(16); content.setPadding(new Insets(24)); content.setMinWidth(0);
        Label title = new Label("Report a complaint"); title.getStyleClass().add("title");
        Label help = new Label("Choose a non-retired asset and describe the problem. Assets under repair or on hold can still be reported.");
        help.setWrapText(true); help.getStyleClass().add("muted");
        refresh.setId("report-refresh"); refresh.setOnAction(e -> loadAssets());
        description.setId("report-description"); description.setPromptText("Describe the maintenance problem");
        description.setWrapText(true); description.setPrefRowCount(3);
        Label descriptionLabel = new Label("Problem description"); descriptionLabel.setLabelFor(description);
        priority.setId("report-priority"); priority.getItems().setAll(ComplaintPriority.values());
        priority.setPromptText("Choose priority"); priority.setMaxWidth(Double.MAX_VALUE);
        Label priorityLabel = new Label("Priority"); priorityLabel.setLabelFor(priority);
        VBox form = new VBox(8, assets, refresh, descriptionLabel, description, priorityLabel, priority);
        form.getStyleClass().add("dashboard-card");
        submit.setId("report-submit"); submit.getStyleClass().add("primary");
        feedback.setId("report-feedback"); feedback.setWrapText(true);
        progress.setId("report-progress"); progress.setVisible(false); progress.setManaged(false);
        submit.setOnAction(e -> {
            if (busy) return;
            Asset asset = assets.selected();
            if (asset == null || description.getText().isBlank() || priority.getValue() == null) {
                message("Choose an asset, describe the problem and select a priority.", true); return;
            }
            int id = asset.getAssetId(); String text = description.getText(); var selectedPriority = priority.getValue();
            busy = true; saving = true; form.setDisable(true); submit.setDisable(true); progress.setDisable(true);
            message("Submitting complaint…", false);
            FxTasks.run(() -> service.submitComplaint(id, text, selectedPriority), result -> {
                message("Complaint #" + result.getComplaintId() + " submitted. Awaiting administrator review.", false);
                description.clear(); priority.setValue(null); assets.clear();
                progress.setVisible(true); progress.setManaged(true);
                progress.setOnAction(event -> openComplaint.accept(result.getComplaintId()));
            }, error -> message(error.getMessage() + " Your input is retained; refresh assets if needed and retry.", true),
                    () -> { saving = false; busy = false; form.setDisable(false); progress.setDisable(false); submit.setDisable(assets.isEmpty()); });
        });
        content.getChildren().addAll(title, help, form, submit, feedback, progress);
        setContent(content); loadAssets();
    }

    private void loadAssets() {
        if (busy) return;
        busy = true; refresh.setDisable(true); submit.setDisable(true); assets.setDisable(true);
        message("Loading reporting assets…", false);
        FxTasks.run(service::getAllAssets, loaded -> {
            assets.setItems(loaded.stream().filter(a -> a.getStatus() != AssetStatus.RETIRED).toList());
            message(assets.isEmpty() ? "No reporting assets available. Ask an administrator to register an asset." : "Reporting assets updated.", false);
            refresh.setText("Refresh assets");
        }, error -> { message(error.getMessage() + " Retry to load assets.", true); refresh.setText("Retry assets"); },
                () -> { busy = false; refresh.setDisable(false); assets.setDisable(false); submit.setDisable(assets.isEmpty()); });
    }

    private void message(String text, boolean error) {
        feedback.setText(text); feedback.getStyleClass().removeAll("error", "success");
        feedback.getStyleClass().add(error ? "error" : "success");
    }
}
