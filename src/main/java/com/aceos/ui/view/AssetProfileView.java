package com.aceos.ui.view;

import com.aceos.model.*;
import com.aceos.service.AceOsService;
import com.aceos.ui.FxTasks;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import java.util.concurrent.Callable;

/** Asset details and administrator availability actions share the same selected asset. */
public final class AssetProfileView extends ScrollPane {
    private final AceOsService service;
    private final int assetId;
    private final VBox content = new VBox(16), details = new VBox(12), actions = new VBox(12);
    private final Label feedback = new Label(), heading = new Label("Asset profile");
    private final Button refresh = new Button("Refresh"), back = new Button("Back to assets");
    private final Button change = new Button("Save lifecycle"), clear = new Button("Clear selected hold");
    private final ComboBox<ManualLifecycle> lifecycle = new ComboBox<>();
    private final ComboBox<SafetyHold> hold = new ComboBox<>();
    private final ComboBox<AssetProfile.ClearanceEvidence> evidence = new ComboBox<>();
    private final TextArea reason = new TextArea();
    private final Label evidenceHint = new Label();
    private AssetProfile loaded;
    private boolean busy, saving, stale = true;
    private String savedFeedback;

    public AssetProfileView(AceOsService service, int assetId, Runnable returnToList) {
        this.service = service; this.assetId = assetId;
        com.aceos.ui.NavigationGuard.install(this, () -> saving, () -> !reason.getText().isEmpty() || evidence.getValue() != null || loaded != null && lifecycle.getValue() != null && lifecycle.getValue() != loaded.asset().getManualLifecycle());
        setId("asset-profile"); getStyleClass().add("dashboard"); setFitToWidth(true); setHbarPolicy(ScrollBarPolicy.NEVER);
        content.setPadding(new Insets(24)); content.setMinWidth(0);
        heading.getStyleClass().add("title"); heading.setWrapText(true);
        refresh.setId("asset-profile-refresh"); refresh.setOnAction(e -> load());
        back.setId("asset-profile-back"); back.setOnAction(e -> { if (com.aceos.ui.NavigationGuard.allowLeave(this)) returnToList.run(); });
        feedback.setId("asset-profile-feedback"); feedback.setWrapText(true);
        details.setId("asset-profile-details"); details.getStyleClass().add("dashboard-card");
        actions.setId("availability-actions"); actions.getStyleClass().add("dashboard-card");
        lifecycle.getItems().setAll(ManualLifecycle.values()); lifecycle.setId("availability-lifecycle");
        hold.setId("availability-hold"); evidence.setId("availability-evidence");
        for (ComboBox<?> box : new ComboBox<?>[] {lifecycle, hold, evidence}) {
            box.setMaxWidth(Double.MAX_VALUE); box.setMinWidth(0);
        }
        hold.setConverter(new StringConverter<>() {
            public String toString(SafetyHold h) { return h == null ? "" : "Hold #" + h.holdId() + " · failed inspection #" + h.failedInspectionId(); }
            public SafetyHold fromString(String s) { throw new UnsupportedOperationException(); }
        });
        evidence.setConverter(new StringConverter<>() {
            public String toString(AssetProfile.ClearanceEvidence e) { return e == null ? "" : e.label(); }
            public AssetProfile.ClearanceEvidence fromString(String s) { throw new UnsupportedOperationException(); }
        });
        hold.valueProperty().addListener((o, a, b) -> selectEvidence());
        evidence.valueProperty().addListener((o, a, b) -> updateButtons());
        lifecycle.valueProperty().addListener((o, a, b) -> updateButtons());
        reason.setId("availability-reason"); reason.setWrapText(true); reason.setPrefRowCount(3);
        reason.setPromptText("Required reason, up to 1000 characters");
        evidenceHint.setWrapText(true);
        change.setId("availability-change"); clear.setId("availability-clear");
        change.getStyleClass().add("primary"); clear.getStyleClass().add("primary");
        change.setOnAction(e -> {
            if (busy || stale || loaded == null) return;
            var expected = loaded.asset().getManualLifecycle(); var next = lifecycle.getValue(); String why = reason.getText();
            write(() -> service.changeAssetLifecycle(assetId, expected, next, why), "Lifecycle saved.");
        });
        clear.setOnAction(e -> {
            if (busy || stale || hold.getValue() == null || evidence.getValue() == null) return;
            int selectedHold = hold.getValue().holdId(); var proof = evidence.getValue(); String why = reason.getText();
            write(() -> service.clearSafetyHold(assetId, selectedHold, why,
                    proof.inspection() ? proof.id() : null, proof.inspection() ? null : proof.id()),
                    "Hold cleared. Other restrictions remain in force.");
        });
        actions.getChildren().addAll(text("Availability controls", "section-title"),
                label("Reason for the selected action", reason), reason,
                label("Manual lifecycle", lifecycle), lifecycle, change,
                label("Active safety hold", hold), hold, label("Eligible same-asset evidence", evidence), evidence,
                evidenceHint, clear,
                text("Retirement requires finished jobs/inspections and paused schedules. Retired assets cannot be reopened. "
                        + "Clearing one hold preserves every other hold, manual stop and unverified repair.", "muted"));
        content.getChildren().addAll(new FlowPane(12, 8, heading, refresh, back), feedback, details);
        setContent(content); load();
    }

    private Label label(String value, Control target) { Label l = text(value, ""); l.setLabelFor(target); return l; }
    private Label text(String value, String style) {
        Label l = new Label(value); l.setWrapText(true); l.setMinHeight(Region.USE_PREF_SIZE);
        if (!style.isEmpty()) l.getStyleClass().add(style); return l;
    }
    private void message(String value, boolean error) {
        feedback.setText(value); feedback.getStyleClass().removeAll("error", "success");
        feedback.getStyleClass().add(error ? "error" : "success");
    }
    private void setBusy(boolean value) {
        busy = value; refresh.setDisable(value); back.setDisable(value); actions.setDisable(value || stale); updateButtons();
    }
    private void updateButtons() {
        change.setDisable(busy || stale || loaded == null || loaded.asset().getManualLifecycle() == ManualLifecycle.RETIRED
                || lifecycle.getValue() == null || lifecycle.getValue() == loaded.asset().getManualLifecycle());
        clear.setDisable(busy || stale || hold.getValue() == null || evidence.getValue() == null);
    }
    private void selectEvidence() {
        var previous = evidence.getValue(); var h = hold.getValue();
        evidence.getItems().setAll(loaded == null || h == null ? java.util.List.of() : loaded.evidence().stream()
                .filter(e -> e.holdId() == h.holdId()).toList());
        evidence.setValue(previous == null ? null : evidence.getItems().stream()
                .filter(e -> e.id() == previous.id() && e.inspection() == previous.inspection()).findFirst().orElse(null));
        evidenceHint.setText(h == null ? "No active safety holds." : evidence.getItems().isEmpty()
                ? "No eligible evidence. Complete a later passing reinspection or verify a corrective repair on this asset, then refresh."
                : "Choose later evidence for this hold. The service rechecks it when saving.");
        updateButtons();
    }
    private void load() {
        if (busy) return;
        stale = true; setBusy(true); message(savedFeedback == null ? "Loading authorized asset profile…" : savedFeedback + " Refreshing profile…", false);
        FxTasks.run(() -> service.getAssetProfile(assetId), profile -> {
            render(profile); message(savedFeedback == null ? "Current asset profile loaded." : savedFeedback, false); refresh.setText("Refresh");
        }, error -> {
            loaded = null; details.getChildren().clear(); content.getChildren().remove(actions);
            message((savedFeedback == null ? error.getMessage() : savedFeedback + " Profile could not be refreshed.") + " Retry to reload.", true); refresh.setText("Retry");
        }, () -> setBusy(false));
    }
    private void write(Callable<?> action, String success) {
        saving = true; savedFeedback = null; setBusy(true); message("Saving…", false);
        FxTasks.run(action, result -> {
            saving = false; savedFeedback = success; reason.clear(); evidence.setValue(null); setBusy(false); load(); setVvalue(0);
        }, error -> {
            saving = false; stale = true; actions.setDisable(true);
            message(error.getMessage() + " Input retained. Refresh before another action.", true); setVvalue(0); setBusy(false);
        }, () -> {});
    }
    private void render(AssetProfile profile) {
        loaded = profile; stale = false; var a = profile.asset();
        heading.setText(a.getAssetCode() + " · " + a.getName());
        details.getChildren().setAll(text("Asset #" + a.getAssetId() + " · " + a.getCategory() + " · " + a.getLocation(), "muted"),
                text("Manual lifecycle: " + a.getManualLifecycle(), ""), text("Effective availability: " + a.getStatus(), ""),
                text("Availability follows: retired → manual/safety hold → started unverified repair → active. Inspection compliance is separate.", "muted"),
                text(profile.role() == UserRole.REQUESTER ? "Your complaint completion history" : "Technical maintenance history", "section-title"));
        if (profile.maintenance().isEmpty()) details.getChildren().add(text("No authorized maintenance records for this asset.", "muted"));
        for (var m : profile.maintenance()) details.getChildren().add(text("Record #" + m.recordId() + " · repair #" + m.workOrderId()
                + " · " + m.recordedAt() + "\n" + m.actionTaken(), ""));
        if (profile.role() == UserRole.ADMINISTRATOR) {
            details.getChildren().add(text("Safety hold history", "section-title"));
            if (profile.holds().isEmpty()) details.getChildren().add(text("No safety holds recorded.", "muted"));
            for (var h : profile.holds()) details.getChildren().add(text("Hold #" + h.holdId() + " · failed inspection #" + h.failedInspectionId()
                    + " · created " + h.createdAt() + (h.clearedAt() == null ? " · ACTIVE" : "\nCleared " + h.clearedAt()
                    + " by account #" + h.clearedBy() + " · " + h.clearanceReason() + "\nEvidence: "
                    + (h.passingInspectionId() != null ? "inspection #" + h.passingInspectionId() : "verified repair #" + h.verifiedWorkOrderId())), ""));
            details.getChildren().add(text("Manual lifecycle history", "section-title"));
            if (profile.lifecycle().isEmpty()) details.getChildren().add(text("No lifecycle changes recorded.", "muted"));
            for (var event : profile.lifecycle()) details.getChildren().add(text(event.at() + " · " + event.before() + " → "
                    + event.after() + " · account #" + event.actorId() + "\n" + event.reason(), ""));
            lifecycle.setValue(a.getManualLifecycle());
            var old = hold.getValue(); hold.getItems().setAll(profile.holds().stream().filter(h -> h.clearedAt() == null).toList());
            hold.setValue(old == null ? null : hold.getItems().stream().filter(h -> h.holdId() == old.holdId()).findFirst().orElse(null));
            if (hold.getValue() == null) hold.getSelectionModel().selectFirst(); selectEvidence();
            if (!content.getChildren().contains(actions)) content.getChildren().add(actions);
        } else content.getChildren().remove(actions);
        actions.setDisable(false); updateButtons();
    }
}
