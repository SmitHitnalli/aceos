package com.aceos.ui.view;

import com.aceos.model.*;
import com.aceos.service.*;
import com.aceos.ui.FxTasks;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Function;

/** Inspection navigation and forms reuse service authorization and transactional actions. */
public class InspectionFlowView extends ScrollPane {
    private record Detail(AssetInspection inspection, List<User> technicians) {}
    private final AceOsService service;
    private final User user;
    private final VBox content = new VBox(16), list = new VBox(12), details = new VBox(12), actions = new VBox(12), scheduling = new VBox(12);
    private final Label feedback = text("", ""), heading = text("Inspections", "title");
    private final Button refresh = new Button("Refresh"), back = new Button("Back to inspections"), open = new Button("Open selected inspection"), create = new Button("Schedule inspection"), save = new Button();
    private final TableView<AssetInspection> table = new TableView<>();
    private final javafx.collections.ObservableList<AssetInspection> rows = FXCollections.observableArrayList();
    private final TextField search = new TextField(), type = new TextField();
    private final ComboBox<String> filter = new ComboBox<>();
    private final SearchSelector<Asset> assets = new SearchSelector<>("Asset", "inspection-asset",
            a -> a.getAssetCode() + " · " + a.getName() + " · " + a.getLocation() + " · " + a.getStatus(), Asset::getAssetId);
    private final SearchSelector<User> technicians = new SearchSelector<>("Active technician", "inspection-technician",
            u -> u.getName() + " · " + u.getEmail() + " · #" + u.getUserId(), User::getUserId);
    private final ComboBox<InspectionStatus> result = new ComboBox<>();
    private final TextArea notes = new TextArea();
    private final DatePicker scheduled = new DatePicker(LocalDate.now().plusDays(7)), completed = new DatePicker(LocalDate.now());
    private Integer selectedId;
    private boolean busy, saving, stale = true, scheduleMode;
    private String savedFeedback;
    private final LocalDate initialDate = LocalDate.now().plusDays(7);

    public InspectionFlowView(AceOsService service, String initialFilter, Integer inspectionId, boolean scheduleMode) {
        this.service = service;
        com.aceos.ui.NavigationGuard.install(this, () -> saving, () -> this.scheduleMode && (!type.getText().isEmpty() || assets.selected() != null || !java.util.Objects.equals(scheduled.getValue(), initialDate)) || selectedId != null && (!notes.getText().isEmpty() || technicians.selected() != null || result.getValue() != InspectionStatus.PASSED || !java.util.Objects.equals(completed.getValue(), LocalDate.now())));
        User current;
        try { current = service.signedInUser(); } catch (AceOsServiceException e) { current = null; }
        user = current;
        setId("inspection-flow"); getStyleClass().add("dashboard"); setFitToWidth(true); setHbarPolicy(ScrollBarPolicy.NEVER);
        content.setPadding(new Insets(24)); content.setMinWidth(0);
        refresh.setId("inspection-refresh"); back.setId("inspection-back"); feedback.setId("inspection-feedback");
        refresh.setOnAction(e -> load());
        back.setOnAction(e -> { if (!com.aceos.ui.NavigationGuard.allowLeave(this)) return; savedFeedback = null; type.clear(); assets.clear(); scheduled.setValue(initialDate); notes.clear(); technicians.clear(); result.setValue(InspectionStatus.PASSED); completed.setValue(LocalDate.now()); selectedId = null; this.scheduleMode = false; load(); setVvalue(0); });
        Button newInspection = new Button("Schedule new inspection"); newInspection.setId("inspection-new");
        newInspection.setOnAction(e -> { if (!busy) { selectedId = null; this.scheduleMode = true; load(); setVvalue(0); } });
        content.getChildren().addAll(new FlowPane(12, 8, heading, refresh, back), feedback);
        if (user == null || user.getRole() == UserRole.REQUESTER) {
            feedback.setText("Inspection access requires an administrator or assigned technician."); setContent(content); return;
        }
        search.setId("inspection-search"); search.setPromptText("Search ID, asset, location, type or notes");
        filter.setId("inspection-filter"); filter.getItems().addAll("ALL", "SCHEDULED", "UNASSIGNED", "OVERDUE", "PASSED", "FAILED");
        filter.setValue(initialFilter == null ? "ALL" : initialFilter);
        var filtered = new FilteredList<>(rows);
        Runnable apply = () -> {
            String q = search.getText().trim().toLowerCase(Locale.ROOT), f = filter.getValue();
            filtered.setPredicate(i -> ("ALL".equals(f) || i.getStatus().name().equals(f)
                    || "UNASSIGNED".equals(f) && i.getStatus() == InspectionStatus.SCHEDULED && i.getAssignedTechnicianId() == 0
                    || "OVERDUE".equals(f) && i.isOverdueOn(LocalDate.now()))
                    && (i.getInspectionId() + " " + i.getAsset().getAssetCode() + " " + i.getAsset().getName() + " "
                    + i.getAsset().getLocation() + " " + i.getInspectionType() + " " + (i.getNotes() == null ? "" : i.getNotes())).toLowerCase(Locale.ROOT).contains(q));
        };
        search.textProperty().addListener((o,a,b) -> apply.run()); filter.valueProperty().addListener((o,a,b) -> apply.run()); apply.run();
        table.setId("inspection-table"); table.getStyleClass().add("repair-table"); table.setItems(filtered); table.setPrefHeight(280); table.setMinHeight(180);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setAccessibleText("Authorized inspections. Select a row and press Enter to open details.");
        table.setPlaceholder(text("No inspections match. Change search/filter or refresh.", "muted"));
        column("ID", i -> "#" + i.getInspectionId()); column("Asset", i -> i.getAsset().getAssetCode());
        column("Type", AssetInspection::getInspectionType); column("Scheduled", i -> i.getScheduledDate().toString());
        column("Result / due", i -> i.isOverdueOn(LocalDate.now()) ? "OVERDUE" : i.getStatus().name());
        column("Assigned", i -> i.getAssignedTechnicianId() == 0 ? "Unassigned" : "#" + i.getAssignedTechnicianId());
        open.setId("inspection-open"); open.getStyleClass().add("primary"); open.setOnAction(e -> openSelected());
        table.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> updateButtons());
        table.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) { openSelected(); e.consume(); } });
        table.setRowFactory(t -> { TableRow<AssetInspection> row = new TableRow<>(); row.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && !row.isEmpty() && !busy && !stale) openRecord(row.getItem().getInspectionId());
        }); return row; });
        list.getChildren().addAll(label("Search inspections", search), search, new FlowPane(8, 8, label("Filter", filter), filter), table, open);
        if (user.getRole() == UserRole.ADMINISTRATOR) list.getChildren().add(newInspection);
        details.setId("inspection-detail"); details.getStyleClass().add("dashboard-card");
        actions.getStyleClass().add("dashboard-card"); scheduling.getStyleClass().add("dashboard-card");
        type.setId("inspection-type"); type.setPromptText("Inspection type, up to 150 characters");
        scheduled.setId("inspection-scheduled-date"); completed.setId("inspection-completed-date");
        result.setId("inspection-result"); result.getItems().addAll(InspectionStatus.PASSED, InspectionStatus.FAILED); result.setValue(InspectionStatus.PASSED);
        result.setMaxWidth(Double.MAX_VALUE); notes.setId("inspection-notes"); notes.setWrapText(true); notes.setPrefRowCount(3);
        notes.setPromptText("Required findings, up to 5000 characters");
        create.setId("inspection-create"); create.getStyleClass().add("primary");
        create.setOnAction(e -> {
            if (busy || stale) return;
            var a = assets.selected(); if (a == null) { message("Choose an available asset.", true); return; }
            String value = type.getText(); LocalDate date = scheduled.getValue();
            write(() -> service.scheduleAssetInspection(a.getAssetId(), value, date), "Inspection scheduled. Choose a technician to assign it.", true);
        });
        scheduling.getChildren().addAll(assets, label("Inspection type", type), type, label("Scheduled date", scheduled), scheduled, create);
        save.setId("inspection-save"); save.getStyleClass().add("primary"); save.setOnAction(e -> {
            if (busy || stale || selectedId == null) return;
            int id = selectedId;
            if (user.getRole() == UserRole.ADMINISTRATOR) {
                var t = technicians.selected(); if (t == null) { message("Choose an active technician.", true); return; }
                write(() -> { service.assignInspection(id, t.getUserId()); return null; }, "Assignment saved.", false);
            } else {
                var value = result.getValue(); String findings = notes.getText(); LocalDate date = completed.getValue();
                write(() -> service.completeAssetInspection(id, value, findings, date),
                        value == InspectionStatus.FAILED ? "Inspection failed; an individual safety hold was recorded." : "Inspection passed. Existing safety holds still require administrator clearance.", false);
            }
        });
        selectedId = inspectionId; this.scheduleMode = scheduleMode && user.getRole() == UserRole.ADMINISTRATOR;
        setContent(content); load();
    }

    private Label label(String value, Control target) { Label l = text(value, ""); l.setLabelFor(target); return l; }
    private static Label text(String value, String style) { Label l = new Label(value); l.setWrapText(true); l.setMinHeight(Region.USE_PREF_SIZE); if (!style.isEmpty()) l.getStyleClass().add(style); return l; }
    private void column(String name, Function<AssetInspection,String> display) { TableColumn<AssetInspection,String> c = new TableColumn<>(name); c.setCellValueFactory(d -> new ReadOnlyStringWrapper(display.apply(d.getValue()))); table.getColumns().add(c); }
    private void message(String value, boolean error) { feedback.setText(value); feedback.getStyleClass().removeAll("success", "error"); feedback.getStyleClass().add(error ? "error" : "success"); }
    private void updateButtons() {
        refresh.setDisable(busy); back.setDisable(busy); list.setDisable(busy); scheduling.setDisable(busy || stale); actions.setDisable(busy || stale);
        open.setDisable(busy || stale || table.getSelectionModel().getSelectedItem() == null);
        create.setDisable(busy || stale || assets.isEmpty()); save.setDisable(busy || stale || user != null && user.getRole() == UserRole.ADMINISTRATOR && technicians.isEmpty());
    }
    private void openSelected() { var i = table.getSelectionModel().getSelectedItem(); if (i != null && !busy && !stale) openRecord(i.getInspectionId()); }
    private void openRecord(int id) { selectedId = id; scheduleMode = false; notes.clear(); technicians.clear(); load(); setVvalue(0); }
    private void load() { load(savedFeedback); }
    private void load(String success) {
        if (busy) return;
        busy = true; stale = true; updateButtons();
        content.getChildren().removeAll(list, details, actions, scheduling);
        back.setVisible(selectedId != null || scheduleMode); back.setManaged(back.isVisible());
        heading.setText(scheduleMode ? "Schedule inspection" : selectedId == null ? "Inspections" : "Inspection #" + selectedId);
        message(success == null ? "Loading inspections…" : success + " Refreshing details…", false);
        if (scheduleMode) {
            content.getChildren().add(scheduling);
            FxTasks.run(() -> service.getAllAssets().stream().filter(a -> a.getManualLifecycle() != ManualLifecycle.RETIRED).toList(),
                    loaded -> { assets.setItems(loaded); stale = false; message(success == null ? loaded.isEmpty() ? "No non-retired assets available." : "Choose an asset to schedule." : success, false); },
                    e -> message(e.getMessage() + " Refresh to retry.", true), () -> { busy = false; updateButtons(); });
        } else if (selectedId == null) {
            rows.clear(); content.getChildren().add(list);
            FxTasks.run(service::getAllAssetInspections, loaded -> { rows.setAll(loaded); stale = false; message(loaded.size() + " authorized inspections loaded.", false); },
                    e -> message(e.getMessage() + " Refresh to retry.", true), () -> { busy = false; updateButtons(); });
        } else {
            details.getChildren().clear(); actions.getChildren().clear(); content.getChildren().add(details);
            int id = selectedId;
            FxTasks.run(() -> new Detail(service.getAssetInspection(id), user.getRole() == UserRole.ADMINISTRATOR ? service.getActiveTechnicians() : List.of()),
                    loaded -> { render(loaded); stale = false; message(success == null ? "Inspection details updated." : success, false); },
                    e -> message((success == null ? e.getMessage() : success + " Details could not be refreshed.") + " Refresh or return to the list.", true), () -> { busy = false; updateButtons(); });
        }
    }
    private void render(Detail loaded) {
        var i = loaded.inspection(); var a = i.getAsset();
        details.getChildren().setAll(text(a.getAssetCode() + " · " + a.getName(), "section-title"), text(a.getLocation() + " · Availability: " + a.getStatus(), "muted"),
                text("Type: " + i.getInspectionType(), ""), text("Scheduled: " + i.getScheduledDate() + (i.isOverdueOn(LocalDate.now()) ? " · OVERDUE" : ""), ""),
                text("Result: " + i.getStatus(), "status-label"), text(i.getAssignedTechnicianId() == 0 ? "Unassigned" : "Assigned technician: #" + i.getAssignedTechnicianId(), ""));
        if (i.getCompletedDate() != null) details.getChildren().addAll(text("Completed: " + i.getCompletedDate() + " · Inspector: " + i.getInspectedBy().getName(), ""), text("Findings: " + i.getNotes(), ""));
        details.getChildren().add(text("A failed inspection creates its own safety hold. A passing result does not clear existing holds.", "muted"));
        if (i.getStatus() != InspectionStatus.SCHEDULED || a.getManualLifecycle() == ManualLifecycle.RETIRED) return;
        if (user.getRole() == UserRole.ADMINISTRATOR) {
            technicians.setItems(loaded.technicians()); save.setText(i.getAssignedTechnicianId() == 0 ? "Assign inspection" : "Reassign inspection");
            actions.getChildren().setAll(text("Assignment", "section-title"), technicians, save);
            if (technicians.isEmpty()) actions.getChildren().add(text("No active technicians available. Refresh after an administrator activates one.", "muted"));
        } else if (i.getAssignedTechnicianId() == user.getUserId()) {
            save.setText("Complete inspection"); actions.getChildren().setAll(text("Record inspection result", "section-title"), label("Result", result), result, label("Findings", notes), notes, label("Completion date", completed), completed, save);
        } else return;
        content.getChildren().add(actions);
    }
    private void write(Callable<AssetInspection> operation, String success, boolean created) {
        if (busy || stale) return;
        saving = true; savedFeedback = null; busy = true; updateButtons(); message("Saving…", false);
        FxTasks.run(operation, saved -> {
            if (created) { selectedId = saved.getInspectionId(); scheduleMode = false; type.clear(); assets.clear(); }
            else if (user.getRole() == UserRole.ADMINISTRATOR) technicians.clear();
            else if (user.getRole() == UserRole.TECHNICIAN) { notes.clear(); result.setValue(InspectionStatus.PASSED); completed.setValue(LocalDate.now()); }
            saving = false; savedFeedback = success; busy = false; load(success); setVvalue(0);
        }, e -> { saving = false; stale = true; busy = false; updateButtons(); message(e.getMessage() + " Input retained. Refresh before retrying.", true); setVvalue(0); }, () -> {});
    }
}
