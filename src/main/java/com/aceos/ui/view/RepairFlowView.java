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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Function;

/** Connected complaint/repair queues. All actions revalidate through existing services. */
public final class RepairFlowView extends ScrollPane {
    private record Row(int id, String asset, String summary, String status, String person) {}
    private record Loaded(RepairDetail detail, List<TechnicianWorkload> technicians) {}
    private final AceOsService service;
    private final boolean jobs;
    private final UserRole role;
    private final VBox content = new VBox(16);
    private final VBox list = new VBox(12);
    private final VBox detail = new VBox(12);
    private final VBox actions = new VBox(12);
    private final Label feedback = new Label();
    private final Label heading = new Label();
    private final Button refresh = new Button("Refresh");
    private final Button back = new Button("Back to list");
    private final Button open = new Button("Open selected record");
    private final TableView<Row> table = new TableView<>();
    private final javafx.collections.ObservableList<Row> rows = FXCollections.observableArrayList();
    private final TextField search = new TextField();
    private final ComboBox<String> status = new ComboBox<>();
    private final TextArea completion = new TextArea(), reviewReason = new TextArea(), comment = new TextArea();
    private final TechnicianSelector technicians = new TechnicianSelector("repair-technician");
    private Integer selectedId;
    private boolean busy, saving;
    private String savedFeedback;

    public RepairFlowView(AceOsService service, boolean jobs, String filter) {
        this(service, jobs, filter, null);
    }

    public RepairFlowView(AceOsService service, boolean jobs, String filter, Integer recordId) {
        this.service = service; this.jobs = jobs;
        com.aceos.ui.NavigationGuard.install(this, () -> saving, () -> !completion.getText().isEmpty() || !reviewReason.getText().isEmpty() || !comment.getText().isEmpty() || technicians.selected() != null);
        UserRole current;
        try { current = service.signedInUser().getRole(); }
        catch (AceOsServiceException e) { current = null; }
        role = current;
        setId("repair-flow"); setFitToWidth(true); setHbarPolicy(ScrollBarPolicy.NEVER);
        content.setPadding(new Insets(24)); content.setMinWidth(0);
        heading.setText(jobs ? (role == UserRole.TECHNICIAN ? "Assigned repairs" : "Repair progress") : "Complaints");
        heading.getStyleClass().add("title"); heading.setWrapText(true);
        refresh.setId("repair-refresh"); refresh.setOnAction(e -> { if (selectedId == null) loadList(); else loadDetail(); });
        back.setId("repair-back"); back.setOnAction(e -> {
            if (!com.aceos.ui.NavigationGuard.allowLeave(this)) return;
            savedFeedback = null; selectedId = null; completion.clear(); reviewReason.clear(); comment.clear(); technicians.clear(); showList(); loadList();
        });
        feedback.setId("repair-feedback"); feedback.setWrapText(true);
        var header = new FlowPane(12, 8, heading, refresh, back);
        content.getChildren().addAll(header, feedback, list);
        search.setId("repair-search"); search.setPromptText("Search ID, asset, description or name");
        Label searchLabel = new Label("Search"); searchLabel.setLabelFor(search);
        status.setId("repair-status"); status.getItems().add("ALL");
        if (jobs) for (var s : WorkOrderStatus.values()) status.getItems().add(s.name());
        else for (var s : ComplaintStatus.values()) status.getItems().add(s.name());
        status.setValue(filter == null ? "ALL" : filter);
        Label statusLabel = new Label("Status"); statusLabel.setLabelFor(status);
        var filtered = new FilteredList<>(rows);
        Runnable apply = () -> {
            String q = search.getText().trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(r -> ("ALL".equals(status.getValue()) || r.status().equals(status.getValue()))
                    && (r.id() + " " + r.asset() + " " + r.summary() + " " + r.person()).toLowerCase(Locale.ROOT).contains(q));
        };
        search.textProperty().addListener((o, a, b) -> apply.run());
        status.valueProperty().addListener((o, a, b) -> apply.run());
        table.setId("repair-table"); table.setItems(filtered); apply.run();
        table.getStyleClass().add("repair-table");
        table.setAccessibleText(jobs ? "Authorized repairs. Select a row and press Enter to open details." : "Authorized complaints. Select a row and press Enter to open details.");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        column("ID", r -> "#" + r.id()); column("Asset", Row::asset);
        column(jobs ? "Complaint" : "Problem", Row::summary); column("Status", r -> readable(r.status()));
        column(jobs ? "Technician" : "Requester", Row::person);
        table.setPrefHeight(256); table.setMinHeight(180);
        table.setPlaceholder(new Label("No records match. Change search/status or refresh."));
        open.setId("repair-open"); open.getStyleClass().add("primary"); open.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> open.setDisable(b == null || busy));
        open.setOnAction(e -> openSelected());
        table.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) { openSelected(); e.consume(); } });
        table.setRowFactory(t -> {
            TableRow<Row> row = new TableRow<>();
            row.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && !row.isEmpty()) openSelected(); });
            return row;
        });
        list.getChildren().addAll(searchLabel, search, new FlowPane(8, 8, statusLabel, status), table, open);
        completion.setId("repair-completion"); completion.setPromptText("Describe the work performed");
        completion.setWrapText(true); completion.setPrefRowCount(3);
        for (var field : new TextArea[]{reviewReason,comment}) { field.setWrapText(true); field.setPrefRowCount(3); field.setPromptText("Up to 2000 characters; visible to authorized complaint participants"); }
        reviewReason.setId("repair-review-reason"); comment.setId("repair-comment");
        detail.setId("repair-detail"); detail.getStyleClass().add("dashboard-card");
        setContent(content);
        if (recordId == null) { showList(); loadList(); }
        else { selectedId = recordId; showDetail(); loadDetail(); }
    }

    private void column(String name, Function<Row, String> value) {
        TableColumn<Row, String> column = new TableColumn<>(name);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
        column.setMinWidth(name.equals("ID") ? 44 : 80);
        column.setPrefWidth(switch (name) { case "ID" -> 60; case "Asset" -> 116; case "Problem", "Complaint" -> 260; default -> 140; });
        if (name.equals("ID")) column.setMaxWidth(70);
        table.getColumns().add(column);
    }

    private void showList() {
        content.getChildren().remove(detail);
        if (!content.getChildren().contains(list)) content.getChildren().add(list);
        back.setVisible(false); back.setManaged(false); setVvalue(0);
    }

    private void showDetail() {
        content.getChildren().remove(list);
        if (!content.getChildren().contains(detail)) content.getChildren().add(detail);
        back.setVisible(true); back.setManaged(true); setVvalue(0);
    }

    private void openSelected() {
        if (busy || table.getSelectionModel().getSelectedItem() == null) return;
        selectedId = table.getSelectionModel().getSelectedItem().id();
        completion.clear(); reviewReason.clear(); comment.clear(); technicians.clear(); showDetail(); loadDetail();
    }

    private void loadList() {
        if (busy) return;
        setBusy(true); message("Loading authorized records…", false);
        FxTasks.run(() -> {
            if (jobs) return service.getAllWorkOrders().stream().map(w -> new Row(w.getWorkOrderId(),
                    w.getComplaint().getAsset().getAssetCode(), "#" + w.getComplaint().getComplaintId() + " · " + w.getComplaint().getDescription(),
                    w.getStatus().name(), w.getTechnician().getName())).toList();
            return service.getAllComplaints().stream().map(c -> new Row(c.getComplaintId(), c.getAsset().getAssetCode(),
                    c.getDescription(), c.getStatus().name(), c.getReportedBy().getName())).toList();
        }, loaded -> {
            rows.setAll(loaded); refresh.setText("Refresh");
            message(loaded.isEmpty() ? "No records available for your account." : loaded.size() + " records loaded. Select a row to open details.", false);
        }, error -> { rows.clear(); message(error.getMessage() + " Retry to reload.", true); refresh.setText("Retry"); },
                () -> setBusy(false));
    }

    private void loadDetail() {
        if (busy || selectedId == null) return;
        setBusy(true); actions.setDisable(true); message(savedFeedback == null ? "Loading current progress…" : savedFeedback + " Refreshing progress…", false);
        int id = selectedId;
        FxTasks.run(() -> {
            var snapshot = jobs ? service.getWorkDetail(id) : service.getRepairDetail(id);
            boolean choose = role == UserRole.ADMINISTRATOR && (snapshot.complaint().getStatus() == ComplaintStatus.ACCEPTED
                    || snapshot.work() != null && snapshot.work().getStatus() == WorkOrderStatus.ASSIGNED);
            return new Loaded(snapshot, choose ? service.getTechnicianWorkloads() : List.of());
        }, loaded -> { render(loaded); refresh.setText("Refresh"); message(savedFeedback == null ? "Current progress loaded." : savedFeedback, false); },
                error -> {
                    detail.getChildren().clear();
                    detail.getChildren().add(text("Details unavailable. Your unfinished input is retained; retry to reload.", "muted"));
                    message((savedFeedback == null ? error.getMessage() : savedFeedback + " Progress could not be refreshed.") + " Retry to reload.", true); refresh.setText("Retry");
                }, () -> setBusy(false));
    }

    private void render(Loaded loaded) {
        var data = loaded.detail(); var c = data.complaint(); var w = data.work();
        detail.getChildren().clear(); actions.getChildren().clear(); actions.setDisable(false);
        detail.getChildren().addAll(text("Complaint #" + c.getComplaintId() + (w == null ? "" : " · Repair #" + w.getWorkOrderId()), "section-title"),
                text(readable(c.getStatus().name()) + (w == null ? "" : " · " + readable(w.getStatus().name())), "status-label"),
                text(c.getAsset().getAssetCode() + " · " + c.getAsset().getName() + " · " + c.getAsset().getLocation(), "muted"),
                text("Asset availability: " + readable(c.getAsset().getStatus().name()) + " · Priority: " + c.getPriority(), "muted"),
                text(c.getDescription(), ""));
        if (role == UserRole.ADMINISTRATOR || role == UserRole.TECHNICIAN)
            detail.getChildren().add(text("Reported by " + c.getReportedBy().getName(), "muted"));
        if (w != null) detail.getChildren().add(text("Assigned technician: " + w.getTechnician().getName(), "muted"));
        if (data.actionTaken() != null) detail.getChildren().addAll(text("Work performed", "section-title"), text(data.actionTaken(), ""));
        reviewReason.setEditable(c.getStatus()==ComplaintStatus.SUBMITTED);
        if (role==UserRole.ADMINISTRATOR && c.getStatus()!=ComplaintStatus.SUBMITTED && !reviewReason.getText().isEmpty()) {
            Label retained = new Label("Retained review draft (already reviewed; copy before leaving)"); retained.setLabelFor(reviewReason);
            actions.getChildren().addAll(retained,reviewReason);
        }
        if (role == UserRole.ADMINISTRATOR && c.getStatus() == ComplaintStatus.SUBMITTED) {
            Label reasonLabel = new Label("Review reason (required)"); reasonLabel.setLabelFor(reviewReason);
            actions.getChildren().addAll(reasonLabel,reviewReason,new FlowPane(8,8,
                    reviewAction("Accept complaint","repair-accept",c.getComplaintId(),true),
                    reviewAction("Reject complaint","repair-reject",c.getComplaintId(),false)));
        }
        if (role == UserRole.ADMINISTRATOR && (c.getStatus() == ComplaintStatus.ACCEPTED || w != null && w.getStatus() == WorkOrderStatus.ASSIGNED)) {
            technicians.setItems(loaded.technicians(), w == null ? 0 : w.getTechnician().getUserId());
            var assign = new Button(w == null ? "Assign repair" : "Reassign unstarted repair");
            assign.setId("repair-assign"); assign.getStyleClass().add("primary"); assign.setWrapText(true);
            assign.setOnAction(e -> {
                var technician = technicians.selected();
                if (technician == null) { message("Choose an active technician.", true); return; }
                int technicianId = technician.getUserId();
                mutate(() -> {
                    if (w == null) return service.assignWorkOrder(c.getComplaintId(), technicianId);
                    service.reassignWorkOrder(w.getWorkOrderId(), technicianId); return null;
                });
            });
            assign.setDisable(technicians.isEmpty());
            actions.getChildren().addAll(technicians, text(technicians.isEmpty() ? "No active technicians. Activate a technician account, then refresh." : "Choose an active technician. Reassignment is allowed only before work starts.", "muted"), assign);
        }
        if (role == UserRole.TECHNICIAN && w != null && w.getStatus() == WorkOrderStatus.ASSIGNED)
            actions.getChildren().add(action("Start repair", "repair-start", () -> service.startWorkOrder(w.getWorkOrderId())));
        if (role == UserRole.TECHNICIAN && w != null && w.getStatus() == WorkOrderStatus.IN_PROGRESS) {
            Label label = new Label("Work performed"); label.setLabelFor(completion);
            var complete = action("Complete repair", "repair-complete", () -> service.completeWorkOrder(w.getWorkOrderId(), completion.getText()));
            // Capture the text on the UI thread before the background operation starts.
            complete.setOnAction(e -> {
                String input = completion.getText();
                if (input.isBlank()) { message("Describe the work performed before completing the repair.", true); return; }
                mutate(() -> service.completeWorkOrder(w.getWorkOrderId(), input));
            });
            actions.getChildren().addAll(label, completion, complete);
        }
        if (role == UserRole.ADMINISTRATOR && w != null && w.getStatus() == WorkOrderStatus.COMPLETED)
            actions.getChildren().add(action("Verify completed repair", "repair-verify", () -> service.verifyWorkOrder(w.getWorkOrderId())));
        detail.getChildren().add(text("Comments and review reasons", "section-title"));
        if (data.notes().isEmpty()) detail.getChildren().add(text("No comments or recorded review reasons.", "muted"));
        boolean hasReason = data.notes().stream().anyMatch(n -> n.kind()!=ComplaintNote.Kind.COMMENT);
        if (c.getStatus()!=ComplaintStatus.SUBMITTED && !hasReason)
            detail.getChildren().add(text("Review reason was not recorded for this historical complaint.", "muted"));
        for (var note : data.notes()) detail.getChildren().addAll(
                text(note.kind().name().replace('_',' ') + " · " + note.authorName() + " · " + note.authorRole() + " · "
                        + note.at().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss")),"muted"), text(note.body(),""));
        boolean closed = c.getStatus()==ComplaintStatus.REJECTED || c.getStatus()==ComplaintStatus.RESOLVED;
        comment.setEditable(!closed);
        if (!closed || !comment.getText().isEmpty()) {
            Label commentLabel = new Label(closed ? "Retained draft (closed complaint; copy before leaving)" : "Add comment"); commentLabel.setLabelFor(comment);
            actions.getChildren().addAll(commentLabel,comment);
            if (!closed) {
                Button post = new Button("Post comment"); post.setId("repair-comment-save"); post.getStyleClass().add("primary");
                post.setOnAction(e -> { String input=comment.getText(); if (!validNote(input,"Comment")) return; mutate(() -> service.addComplaintComment(c.getComplaintId(),input), comment::clear, "Comment saved successfully."); });
                actions.getChildren().add(post);
            }
        }
        if (closed) detail.getChildren().add(text("Closed complaint: comments and review reasons are read-only.","muted"));
        if (!actions.getChildren().isEmpty()) detail.getChildren().add(actions);
        detail.getChildren().add(text("Timeline", "section-title"));
        for (var event : data.timeline()) detail.getChildren().add(text(
                event.at().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss")) + " · " + event.description(), "muted"));
        if (c.getStatus() == ComplaintStatus.ACCEPTED && w == null) detail.getChildren().add(text("Awaiting technician assignment.", "muted"));
        if (w != null && w.getStatus() == WorkOrderStatus.COMPLETED) detail.getChildren().add(text("Completion is awaiting administrator verification.", "muted"));
    }

    private boolean validNote(String input, String field) {
        if(input.isBlank() || input.trim().codePointCount(0,input.trim().length())>2000) {
            message(field + " is required and must be at most 2000 characters.",true); setVvalue(0); return false;
        }
        return true;
    }

    private Button reviewAction(String name, String id, int complaintId, boolean accept) {
        Button button = new Button(name); button.setId(id); button.getStyleClass().add("primary");
        button.setOnAction(e -> {
            String reason = reviewReason.getText();
            if (!validNote(reason,"Review reason")) return;
            mutate(() -> accept ? service.acceptComplaint(complaintId,reason) : service.rejectComplaint(complaintId,reason),
                    reviewReason::clear, accept ? "Complaint accepted; reason saved." : "Complaint rejected; reason saved.");
        });
        return button;
    }

    private Button action(String name, String id, Callable<?> operation) {
        Button button = new Button(name); button.setId(id); button.setWrapText(true);
        button.getStyleClass().add("primary"); button.setOnAction(e -> mutate(operation)); return button;
    }

    private void mutate(Callable<?> operation) {
        mutate(operation, () -> { completion.clear(); technicians.clear(); }, "Action saved successfully.");
    }

    private void mutate(Callable<?> operation, Runnable clearSavedInput, String success) {
        if (busy || actions.isDisabled()) return;
        saving = true; savedFeedback = null; setBusy(true); actions.setDisable(true); message("Saving…", false);
        FxTasks.run(operation, result -> {
            clearSavedInput.run();
            saving = false; savedFeedback = success; setBusy(false); loadDetail(); setVvalue(0);
        }, error -> {
            saving = false; message(error.getMessage() + " Input retained. Refresh current progress before retrying.", true);
            setVvalue(0);
            refresh.setText("Refresh current progress");
            // A stale/failed write must not leave old actions enabled.
            setBusy(false);
        }, () -> {});
    }

    private void setBusy(boolean value) {
        busy = value; refresh.setDisable(value); back.setDisable(value); table.setDisable(value);
        open.setDisable(value || table.getSelectionModel().getSelectedItem() == null);
    }

    private void message(String text, boolean error) {
        feedback.setText(text); feedback.getStyleClass().removeAll("error", "success");
        if (error) feedback.getStyleClass().add("error");
    }
    private static Label text(String value, String style) {
        Label label = new Label(value); label.setWrapText(true); label.setMinWidth(0);
        if (!style.isEmpty()) label.getStyleClass().add(style); return label;
    }
    private static String readable(String status) {
        return switch (status) {
            case "SUBMITTED" -> "Awaiting review";
            case "ACCEPTED" -> "Accepted · awaiting assignment";
            case "WORK_ORDER_CREATED" -> "Repair assigned";
            case "COMPLETED" -> "Completed · awaiting verification";
            case "RESOLVED" -> "Resolved · repair verified";
            default -> status.replace('_', ' ').toLowerCase(Locale.ROOT);
        };
    }
}
