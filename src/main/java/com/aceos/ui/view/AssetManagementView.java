package com.aceos.ui.view;

import com.aceos.model.Asset;
import com.aceos.model.UserRole;
import com.aceos.service.AceOsService;
import com.aceos.service.AceOsServiceException;
import com.aceos.ui.FxTasks;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Administrator registration through the existing authorized service. */
public class AssetManagementView extends BorderPane {
    private final AceOsService service;
    private final ScrollPane form = new ScrollPane();
    private final TextField code = new TextField(), name = new TextField(), category = new TextField(), location = new TextField();
    private final Button register = new Button("Register asset"), open = new Button("Open asset profile"), another = new Button("Register another asset");
    private final Label feedback = new Label();
    private Asset saved;
    private boolean busy;

    public AssetManagementView(AceOsService service) { this(service, null); }

    public AssetManagementView(AceOsService service, Runnable returnToAssets) {
        this.service = service;
        com.aceos.ui.NavigationGuard.install(this, () -> busy, () -> saved == null && java.util.Arrays.stream(fields()).anyMatch(f -> !f.getText().isEmpty()));
        setId("asset-registration");
        VBox content = new VBox(12); content.setPadding(new Insets(24)); content.setMinWidth(0);
        form.getStyleClass().add("dashboard"); form.setFitToWidth(true); form.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        form.setContent(content); setCenter(form);
        if (returnToAssets != null) {
            Button back = new Button("Back to assets"); back.setId("registration-back");
            back.setOnAction(e -> { if (com.aceos.ui.NavigationGuard.allowLeave(this)) returnToAssets.run(); });
            content.getChildren().add(back);
        }
        content.getChildren().addAll(text("Register asset", "title"), text("All fields are required. New assets start with Active lifecycle and availability.", "muted"));
        try {
            if (service.signedInUser().getRole() != UserRole.ADMINISTRATOR) {
                content.getChildren().add(text("Only administrators can register assets.", "error")); return;
            }
        } catch (AceOsServiceException e) { content.getChildren().add(text(e.getMessage(), "error")); return; }
        feedback.setId("registration-feedback"); feedback.setWrapText(true); feedback.setMinHeight(Region.USE_PREF_SIZE);
        feedback.setAccessibleText("Registration feedback");
        VBox fields = new VBox(8); fields.getStyleClass().add("dashboard-card");
        field(fields, code, "code", "Asset code", "Unique code · up to 50 characters", "Example: MUJ-PROJ-001");
        field(fields, name, "name", "Asset name", "Up to 100 characters", "Example: Classroom projector");
        field(fields, category, "category", "Category", "Up to 100 characters", "Example: Projector");
        field(fields, location, "location", "Location", "Up to 150 characters", "Example: Academic Block 1 · Room 301");
        register.setId("registration-save"); register.getStyleClass().add("primary"); register.setOnAction(e -> save());
        code.setOnAction(e -> name.requestFocus()); name.setOnAction(e -> category.requestFocus());
        category.setOnAction(e -> location.requestFocus()); location.setOnAction(e -> save());
        open.setId("registration-open"); open.getStyleClass().add("primary");
        open.setOnAction(e -> {
            if (busy || saved == null) return;
            setCenter(new AssetProfileView(service, saved.getAssetId(), returnToAssets == null ? () -> setCenter(new AssetDirectoryView(service)) : returnToAssets));
        });
        another.setId("registration-another"); another.setOnAction(e -> {
            if (busy || saved == null) return;
            saved = null; for (TextField field : fields()) field.clear();
            message("Ready to register another asset.", false); update(); code.requestFocus();
        });
        content.getChildren().addAll(feedback, fields, register, new FlowPane(12, 8, open, another)); update();
    }
    private TextField[] fields() { return new TextField[] {code, name, category, location}; }
    private Label text(String value, String style) {
        Label label = new Label(value); label.setWrapText(true); label.setMinHeight(Region.USE_PREF_SIZE);
        if (!style.isEmpty()) label.getStyleClass().add(style); return label;
    }
    private void field(VBox box, TextField field, String id, String title, String hint, String prompt) {
        field.setId("registration-" + id); field.setPromptText(prompt); field.setMinWidth(0);
        Label label = text(title, ""); label.setLabelFor(field); field.setAccessibleHelp(hint);
        box.getChildren().addAll(label, field, text(hint, "muted"));
    }
    private void update() {
        for (TextField field : fields()) field.setDisable(busy || saved != null);
        register.setDisable(busy || saved != null);
        open.setVisible(saved != null); open.setManaged(saved != null); open.setDisable(busy);
        another.setVisible(saved != null); another.setManaged(saved != null); another.setDisable(busy);
    }
    private void message(String value, boolean error) {
        feedback.setText(value); feedback.getStyleClass().removeAll("error", "success");
        feedback.getStyleClass().add(error ? "error" : "success"); form.setVvalue(0);
    }
    private void save() {
        if (busy || saved != null) return;
        String c = code.getText(), n = name.getText(), cat = category.getText(), loc = location.getText();
        busy = true; update(); message("Registering asset…", false);
        FxTasks.run(() -> service.registerAsset(c, n, cat, loc), asset -> {
            saved = asset;
            message("Registered asset #" + asset.getAssetId() + " · " + asset.getAssetCode() + " · " + asset.getName()
                    + ". Open its profile to review availability and history.", false);
        }, error -> message(error.getMessage() + " Your entries are retained. Correct them or retry.", true),
                () -> { busy = false; update(); if (saved != null) open.requestFocus(); });
    }
}
