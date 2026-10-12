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
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Function;

/** Connected schedule navigation reuses scoped reads and existing recurrence services. */
public class PreventiveScheduleFlowView extends ScrollPane {
    private final AceOsService service;
    private final User user;
    private final VBox content = new VBox(16), list = new VBox(12), details = new VBox(12), actions = new VBox(12), creation = new VBox(12);
    private final Label feedback = text("", ""), heading = text("Preventive schedules", "title");
    private final Button refresh = new Button("Refresh"), back = new Button("Back to schedules"), open = new Button("Open selected schedule"), create = new Button("Create schedule"), toggle = new Button(), complete = new Button("Record recurring completion");
    private final TableView<PreventiveMaintenanceSchedule> table = new TableView<>();
    private final javafx.collections.ObservableList<PreventiveMaintenanceSchedule> rows = FXCollections.observableArrayList();
    private final TextField search = new TextField(), task = new TextField(), frequency = new TextField();
    private final ComboBox<String> filter = new ComboBox<>();
    private final SearchSelector<Asset> assets = new SearchSelector<>("Asset", "schedule-asset",
            a -> a.getAssetCode() + " · " + a.getName() + " · " + a.getLocation() + " · " + a.getStatus(), Asset::getAssetId);
    private final DatePicker due = new DatePicker(LocalDate.now().plusDays(30)), completed = new DatePicker(LocalDate.now());
    private Integer selectedId;
    private PreventiveMaintenanceSchedule selected;
    private boolean busy, saving, stale = true, createMode;
    private String savedFeedback;
    private final LocalDate initialDate = LocalDate.now().plusDays(30);

    public PreventiveScheduleFlowView(AceOsService service, String initialFilter, Integer scheduleId, boolean createMode) {
        this.service = service;
        com.aceos.ui.NavigationGuard.install(this, () -> saving, () -> this.createMode && (!task.getText().isEmpty() || !frequency.getText().isEmpty() || assets.selected() != null || !java.util.Objects.equals(due.getValue(), initialDate)) || selectedId != null && !java.util.Objects.equals(completed.getValue(), LocalDate.now()));
        User current;
        try { current = service.signedInUser(); } catch (AceOsServiceException e) { current = null; }
        user = current;
        setId("schedule-flow"); getStyleClass().add("dashboard"); setFitToWidth(true); setHbarPolicy(ScrollBarPolicy.NEVER);
        content.setPadding(new Insets(24)); content.setMinWidth(0);
        refresh.setId("schedule-refresh"); back.setId("schedule-back"); feedback.setId("schedule-feedback");
        refresh.setOnAction(e -> load());
        back.setOnAction(e -> { if (!com.aceos.ui.NavigationGuard.allowLeave(this)) return; savedFeedback = null; task.clear(); frequency.clear(); assets.clear(); due.setValue(initialDate); completed.setValue(LocalDate.now()); selectedId = null; this.createMode = false; load(); setVvalue(0); });
        Button newSchedule = new Button("Create new schedule"); newSchedule.setId("schedule-new");
        newSchedule.setOnAction(e -> { if (!busy) { selectedId = null; this.createMode = true; load(); setVvalue(0); } });
        content.getChildren().addAll(new FlowPane(12, 8, heading, refresh, back), feedback);
        if (user == null || user.getRole() == UserRole.REQUESTER) {
            refresh.setDisable(true); back.setVisible(false); back.setManaged(false);
            feedback.setText("Schedule access requires an administrator or a technician with an assigned asset."); setContent(content); return;
        }
        search.setId("schedule-search"); search.setPromptText("Search ID, asset, location, task, frequency or due date");
        filter.setId("schedule-filter"); filter.getItems().addAll("ALL", "ACTIVE", "PAUSED", "DUE", "OVERDUE", "UPCOMING");
        filter.setValue(initialFilter == null ? "ALL" : initialFilter);
        var filtered = new FilteredList<>(rows);
        Runnable apply = () -> {
            String q = search.getText().trim().toLowerCase(Locale.ROOT), f = filter.getValue();
            filtered.setPredicate(s -> ("ALL".equals(f) || s.getStatus().name().equals(f) || timing(s).equals(f)
                    || "DUE".equals(f) && s.isDueOn(LocalDate.now()))
                    && (s.getMaintenanceScheduleId() + " " + s.getAsset().getAssetCode() + " " + s.getAsset().getName() + " "
                    + s.getAsset().getLocation() + " " + s.getTaskDescription() + " " + s.getFrequencyDays() + " " + s.getNextDueDate() + " " + timing(s)).toLowerCase(Locale.ROOT).contains(q));
        };
        search.textProperty().addListener((o,a,b) -> apply.run()); filter.valueProperty().addListener((o,a,b) -> apply.run()); apply.run();
        table.setId("schedule-table"); table.getStyleClass().add("repair-table"); table.setItems(filtered); table.setPrefHeight(280); table.setMinHeight(180);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setAccessibleText("Authorized preventive schedules. Select a row and press Enter to open details.");
        table.setPlaceholder(text("No schedules match. Change search/filter or refresh.", "muted"));
        column("ID", s -> "#" + s.getMaintenanceScheduleId()); column("Asset", s -> s.getAsset().getAssetCode());
        column("Task", PreventiveMaintenanceSchedule::getTaskDescription); column("Every (days)", s -> "" + s.getFrequencyDays());
        column("Next due", s -> s.getNextDueDate().toString()); column("Status / timing", s -> s.getStatus() + " · " + timing(s));
        open.setId("schedule-open"); open.getStyleClass().add("primary"); open.setOnAction(e -> openSelected());
        table.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> updateButtons());
        table.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) { openSelected(); e.consume(); } });
        table.setRowFactory(t -> { TableRow<PreventiveMaintenanceSchedule> row = new TableRow<>(); row.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && !row.isEmpty() && !busy && !stale) openRecord(row.getItem().getMaintenanceScheduleId());
        }); return row; });
        list.getChildren().addAll(label("Search schedules", search), search, new FlowPane(8, 8, label("Filter", filter), filter), table, open);
        if (user.getRole() == UserRole.ADMINISTRATOR) list.getChildren().add(newSchedule);
        details.setId("schedule-detail"); details.getStyleClass().add("dashboard-card");
        actions.getStyleClass().add("dashboard-card"); creation.getStyleClass().add("dashboard-card");
        task.setId("schedule-task"); task.setPromptText("Maintenance task, up to 255 characters");
        frequency.setId("schedule-frequency"); frequency.setPromptText("Positive number of days, for example 30");
        due.setId("schedule-due-date"); completed.setId("schedule-completed-date");
        create.setId("schedule-create"); create.getStyleClass().add("primary");
        create.setOnAction(e -> {
            if (busy || stale) return;
            var a = assets.selected(); if (a == null) { message("Choose a non-retired asset.", true); setVvalue(0); return; }
            int days;
            try { days = Integer.parseInt(frequency.getText().trim()); } catch (NumberFormatException ex) { message("Frequency must be a positive whole number of days.", true); setVvalue(0); return; }
            String value = task.getText(); LocalDate date = due.getValue();
            write(() -> service.createPreventiveMaintenanceSchedule(a.getAssetId(), value, days, date), "Schedule created.", true);
        });
        creation.getChildren().addAll(assets, label("Maintenance task", task), task, label("Frequency (days)", frequency), frequency, label("Next due date", due), due, create);
        toggle.setId("schedule-toggle"); toggle.setOnAction(e -> {
            if (busy || stale || selected == null) return;
            int id = selectedId; boolean active = selected.getStatus() == MaintenanceScheduleStatus.ACTIVE;
            write(() -> active ? service.pausePreventiveMaintenanceSchedule(id) : service.activatePreventiveMaintenanceSchedule(id), active ? "Schedule paused." : "Schedule activated.", false);
        });
        complete.setId("schedule-complete"); complete.getStyleClass().add("primary"); complete.setOnAction(e -> {
            if (busy || stale || selectedId == null) return;
            int id = selectedId; LocalDate date = completed.getValue();
            write(() -> service.completePreventiveMaintenanceSchedule(id, date), "Completion recorded; next due date updated.", false);
        });
        selectedId = scheduleId; this.createMode = createMode && user.getRole() == UserRole.ADMINISTRATOR;
        setContent(content); load();
    }

    private static String timing(PreventiveMaintenanceSchedule s) {
        if (s.getStatus() == MaintenanceScheduleStatus.PAUSED) return "PAUSED";
        return s.getNextDueDate().isBefore(LocalDate.now()) ? "OVERDUE" : s.isDueOn(LocalDate.now()) ? "DUE" : "UPCOMING";
    }
    private Label label(String value, Control target) { Label l = text(value, ""); l.setLabelFor(target); return l; }
    private static Label text(String value, String style) { Label l = new Label(value); l.setWrapText(true); l.setMinHeight(Region.USE_PREF_SIZE); if (!style.isEmpty()) l.getStyleClass().add(style); return l; }
    private void column(String name, Function<PreventiveMaintenanceSchedule,String> display) { TableColumn<PreventiveMaintenanceSchedule,String> c = new TableColumn<>(name); c.setCellValueFactory(d -> new ReadOnlyStringWrapper(display.apply(d.getValue()))); table.getColumns().add(c); }
    private void message(String value, boolean error) { feedback.setText(value); feedback.getStyleClass().removeAll("success", "error"); feedback.getStyleClass().add(error ? "error" : "success"); }
    private void updateButtons() {
        refresh.setDisable(busy); back.setDisable(busy); list.setDisable(busy); creation.setDisable(busy || stale); actions.setDisable(busy || stale);
        open.setDisable(busy || stale || table.getSelectionModel().getSelectedItem() == null); create.setDisable(busy || stale || assets.isEmpty());
    }
    private void openSelected() { var s = table.getSelectionModel().getSelectedItem(); if (s != null && !busy && !stale) openRecord(s.getMaintenanceScheduleId()); }
    private void openRecord(int id) { selectedId = id; createMode = false; completed.setValue(LocalDate.now()); load(); setVvalue(0); }
    private void load() { load(savedFeedback); }
    private void load(String success) {
        if (busy) return;
        busy = true; stale = true; selected = null; updateButtons();
        content.getChildren().removeAll(list, details, actions, creation);
        back.setVisible(selectedId != null || createMode); back.setManaged(back.isVisible());
        heading.setText(createMode ? "Create preventive schedule" : selectedId == null ? "Preventive schedules" : "Schedule #" + selectedId);
        message(success == null ? "Loading schedules…" : success + " Refreshing details…", false);
        if (createMode) {
            content.getChildren().add(creation);
            FxTasks.run(() -> service.getAllAssets().stream().filter(a -> a.getManualLifecycle() != ManualLifecycle.RETIRED).toList(),
                    loaded -> { assets.setItems(loaded); stale = false; message(success == null ? loaded.isEmpty() ? "No non-retired assets available." : "Choose an asset and recurrence interval." : success, false); },
                    e -> message(e.getMessage() + " Refresh to retry.", true), () -> { busy = false; updateButtons(); });
        } else if (selectedId == null) {
            rows.clear(); content.getChildren().add(list);
            FxTasks.run(service::getAllPreventiveMaintenanceSchedules, loaded -> { rows.setAll(loaded); stale = false; message(loaded.size() + " authorized schedules loaded.", false); },
                    e -> message(e.getMessage() + " Refresh to retry.", true), () -> { busy = false; updateButtons(); });
        } else {
            details.getChildren().clear(); actions.getChildren().clear(); content.getChildren().add(details);
            int id = selectedId;
            FxTasks.run(() -> service.getPreventiveMaintenanceSchedule(id), loaded -> { selected = loaded; render(loaded); stale = false; message(success == null ? "Schedule details updated." : success, false); },
                    e -> message((success == null ? e.getMessage() : success + " Details could not be refreshed.") + " Refresh or return to the list.", true), () -> { busy = false; updateButtons(); });
        }
    }
    private void render(PreventiveMaintenanceSchedule s) {
        var a = s.getAsset();
        details.getChildren().setAll(text(a.getAssetCode() + " · " + a.getName(), "section-title"), text(a.getLocation() + " · Availability: " + a.getStatus(), "muted"),
                text("Task: " + s.getTaskDescription(), ""), text("Frequency: every " + s.getFrequencyDays() + " days", ""),
                text("Next due: " + s.getNextDueDate(), ""), text("Status: " + s.getStatus() + " · " + timing(s), "status-label"),
                text("Recurring completion sets the next due date to the completion date plus frequency. The schedule remains active.", "muted"));
        if (user.getRole() != UserRole.ADMINISTRATOR) { details.getChildren().add(text("Read-only schedule for an assigned asset. Administrators manage recurrence.", "muted")); return; }
        if (a.getManualLifecycle() == ManualLifecycle.RETIRED) { details.getChildren().add(text("Retired asset: schedule actions are unavailable.", "muted")); return; }
        boolean active = s.getStatus() == MaintenanceScheduleStatus.ACTIVE;
        toggle.setText(active ? "Pause schedule" : "Activate schedule");
        actions.getChildren().setAll(text("Manage schedule", "section-title"), toggle);
        if (active) actions.getChildren().addAll(label("Completion date", completed), completed, complete);
        content.getChildren().add(actions);
    }
    private void write(Callable<PreventiveMaintenanceSchedule> operation, String success, boolean created) {
        if (busy || stale) return;
        saving = true; savedFeedback = null; busy = true; updateButtons(); message("Saving…", false);
        FxTasks.run(operation, saved -> {
            if (created) { selectedId = saved.getMaintenanceScheduleId(); createMode = false; task.clear(); frequency.clear(); assets.clear(); }
            completed.setValue(LocalDate.now()); saving = false; savedFeedback = success; busy = false; load(success); setVvalue(0);
        }, e -> { saving = false; stale = true; busy = false; updateButtons(); message(e.getMessage() + " Input retained. Refresh before retrying.", true); setVvalue(0); }, () -> {});
    }
}
