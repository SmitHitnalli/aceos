package com.aceos.ui.view;

import com.aceos.model.DashboardSummary;
import com.aceos.model.UserRole;
import com.aceos.service.AceOsService;
import com.aceos.service.AceOsServiceException;
import com.aceos.ui.FxTasks;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class DashboardView extends ScrollPane {
    private final AceOsService service;
    private final Consumer<String> navigate;
    private final java.util.function.BiConsumer<String, Integer> openRecord;
    private final Map<String, Label> values = new LinkedHashMap<>();
    private final VBox updates = new VBox(12);
    private final Label feedback = new Label();
    private final Button refresh = new Button("Refresh");
    private final UserRole role;

    public DashboardView(AceOsService service, Consumer<String> navigate) throws AceOsServiceException {
        this(service, navigate, null);
    }

    public DashboardView(AceOsService service, Consumer<String> navigate,
            java.util.function.BiConsumer<String, Integer> openRecord) throws AceOsServiceException {
        this.service = service;
        this.navigate = navigate;
        this.openRecord = openRecord;
        role = service.signedInUser().getRole();
        setId("role-dashboard");
        getStyleClass().add("dashboard");
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        VBox content = new VBox(16);
        content.setPadding(new Insets(24));
        content.setMinWidth(0);
        String heading = switch (role) {
            case REQUESTER -> "Your complaints";
            case TECHNICIAN -> "Your work today";
            case ADMINISTRATOR -> "Operations overview";
        };
        Label title = label(heading, "title");
        Label description = label(switch (role) {
            case REQUESTER -> "Report an issue and follow your complaint progress.";
            case TECHNICIAN -> "Start assigned repairs, record completion and carry out inspections.";
            case ADMINISTRATOR -> "Review requests, assign work and verify completed repairs.";
        }, "muted");
        refresh.setId("dashboard-refresh");
        refresh.setOnAction(e -> load());
        FlowPane header = new FlowPane(12, 8, title, refresh);
        feedback.setId("dashboard-feedback");
        feedback.setWrapText(true);
        feedback.setAccessibleRole(javafx.scene.AccessibleRole.TEXT);
        FlowPane queues = new FlowPane(12, 12);
        queues.setId("dashboard-queues");
        switch (role) {
            case REQUESTER -> {
                queues.getChildren().addAll(
                        card("open", "Open complaints", "Track progress", "complaints"),
                        card("resolved", "Resolved complaints", "View complaints", "complaints"));
                content.getChildren().addAll(header, description,
                        action("Report a complaint", "report", true));
            }
            case TECHNICIAN -> {
                queues.getChildren().addAll(
                        card("assigned", "Ready to start", "Open maintenance", "maintenance"),
                        card("started", "Repairs in progress", "Record completion", "maintenance"),
                        card("completed", "Awaiting verification", "View jobs", "jobs"),
                        card("inspections", "Scheduled inspections", "Complete inspection", "complete-inspection"),
                        card("overdueInspections", "Overdue inspections", "View inspections", "inspections"));
                content.getChildren().addAll(header, description);
            }
            case ADMINISTRATOR -> {
                queues.getChildren().addAll(
                        card("review", "Awaiting review", "Review complaints", "review"),
                        card("assignment", "Ready for assignment", "Assign work", "assign"),
                        card("verification", "Awaiting verification", "Verify repairs", "verify"),
                        card("unassignedInspections", "Unassigned inspections", "Assign inspections", "assignments"),
                        card("overdueInspections", "Overdue inspections", "View inspections", "inspections"),
                        card("overdueSchedules", "Overdue schedules", "Manage schedules", "schedules"),
                        card("holds", "Open safety holds", "Manage availability", "availability"));
                content.getChildren().addAll(header, description);
            }
        }
        content.getChildren().addAll(feedback, queues,
                label(role == UserRole.REQUESTER ? "Recent progress" : role == UserRole.TECHNICIAN ? "Assigned work and inspections" : "Requests awaiting review", "section-title"),
                updates);
        if (role == UserRole.ADMINISTRATOR) {
            content.getChildren().addAll(label("Management", "section-title"),
                    new FlowPane(8, 8, action("Accounts", "accounts", false),
                            action("Register asset", "register", false),
                            action("Schedule inspection", "schedule-inspection", false),
                            action("Asset directory", "assets", false)));
        }
        setContent(content);
        load();
    }

    private VBox card(String key, String heading, String action, String destination) {
        Label value = label("—", "queue-value");
        value.setId("dashboard-count-" + key);
        values.put(key, value);
        VBox card = new VBox(8, label(heading, "muted"), value, action(action, destination, false));
        card.getStyleClass().add("dashboard-card");
        card.setPrefWidth(224);
        card.setMinWidth(0);
        // Keep each card inside the viewport even with a narrow expanded navigation.
        card.maxWidthProperty().bind(widthProperty().subtract(56));
        return card;
    }

    private Button action(String text, String destination, boolean primary) {
        Button button = new Button(text);
        button.setId("dashboard-action-" + destination);
        button.setWrapText(true);
        button.setMaxWidth(Double.MAX_VALUE);
        if (primary) button.getStyleClass().add("primary");
        button.setOnAction(e -> navigate.accept(destination));
        return button;
    }

    private Label label(String text, String style) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add(style);
        label.setMinWidth(0);
        return label;
    }

    private void load() {
        if (refresh.isDisabled()) return;
        refresh.setDisable(true);
        feedback.getStyleClass().removeAll("error", "success");
        feedback.setText("Loading your dashboard…");
        refresh.setText("Loading…");
        FxTasks.run(service::getDashboardSummary, this::render, error -> {
            values.values().forEach(value -> value.setText("—"));
            updates.getChildren().clear();
            feedback.setText(error.getMessage() + " Use Retry to load again.");
            feedback.getStyleClass().add("error");
            refresh.setText("Retry");
        }, () -> {
            refresh.setDisable(false);
            if (!refresh.getText().equals("Retry")) refresh.setText("Refresh");
        });
    }

    private void render(DashboardSummary summary) {
        values.forEach((key, value) -> value.setText(String.valueOf(summary.counts().getOrDefault(key, 0))));
        updates.getChildren().clear();
        if (summary.items().isEmpty()) {
            updates.getChildren().add(label(switch (role) {
                case REQUESTER -> "No complaints yet. Report an issue when you need help.";
                case TECHNICIAN -> "No unfinished jobs or scheduled inspections assigned to you.";
                case ADMINISTRATOR -> "No complaints awaiting review. Other queues are shown above.";
            }, "muted"));
        }
        for (var item : summary.items()) {
            String route = item.kind().equals("Inspection") ? "complete-inspection"
                    : role == UserRole.TECHNICIAN ? "maintenance"
                    : role == UserRole.ADMINISTRATOR ? "review" : "complaints";
            if (item.status().equals("COMPLETED")) route = "jobs";
            Button open = action(role == UserRole.REQUESTER ? "View complaint progress" : "Open " + item.kind().toLowerCase(), route, false);
            open.setId("dashboard-record-" + item.kind().toLowerCase().replace(' ', '-') + "-" + item.id());
            if (openRecord != null && !item.kind().equals("Inspection"))
                open.setOnAction(e -> openRecord.accept(item.kind(), item.id()));
            VBox row = new VBox(8,
                    label(item.kind() + " #" + item.id() + " · " + item.asset(), "section-title"),
                    label(readableStatus(item.status()), "status-label"),
                    label(item.detail(), "muted"),
                    open);
            row.getStyleClass().add("dashboard-card");
            updates.getChildren().add(row);
        }
        feedback.setText("Dashboard updated. Open a screen to act; return here for fresh counts.");
        feedback.getStyleClass().add("success");
    }

    private String readableStatus(String status) {
        return switch (status) {
            case "SUBMITTED" -> "Awaiting administrator review";
            case "ACCEPTED" -> "Accepted · awaiting technician assignment";
            case "WORK_ORDER_CREATED" -> "Repair assigned · open progress for details";
            case "RESOLVED" -> "Repair verified · complaint resolved";
            case "REJECTED" -> "Complaint rejected";
            case "COMPLETED" -> "Repair completed · awaiting administrator verification";
            default -> status.replace('_', ' ').toLowerCase();
        };
    }
}
