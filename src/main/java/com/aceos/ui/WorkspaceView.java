package com.aceos.ui;

import com.aceos.model.UserRole;
import com.aceos.service.AceOsService;
import com.aceos.service.AceOsServiceException;
import com.aceos.ui.view.*;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Role navigation is an allow-list; all destinations still enforce service permissions. */
public final class WorkspaceView extends BorderPane {
    private record Destination(Button button, Supplier<Node> view) {}
    private record Shortcut(String parent, String title, Supplier<Node> view) {}
    private final Map<String, Shortcut> shortcuts = new LinkedHashMap<>();
    private final Map<String, Destination> destinations = new LinkedHashMap<>();
    private final VBox navigation = new VBox(8);
    private final ScrollPane navigationScroll = new ScrollPane(navigation);
    private final Label screen = new Label();
    private final CheckBox reducedMotion = new CheckBox("Reduce motion");
    private final Button menu = new Button("Hide menu");
    private boolean narrow;
    private FadeTransition transition;

    public WorkspaceView(AceOsService service, Runnable logout, Runnable passwordChanged) throws AceOsServiceException {
        setId("workspace");
        var user = service.signedInUser();
        var role = user.getRole();
        Label brand = new Label("AceOS");
        brand.getStyleClass().add("brand");
        Label identity = new Label(user.getName() + " · " + role.name().toLowerCase());
        identity.setWrapText(true);
        identity.setMinWidth(0);
        identity.getStyleClass().add("shell-identity");
        Button account = new Button("My account");
        account.setId("shell-account");
        account.setOnAction(e -> {
            if (!NavigationGuard.allowLeave(getCenter())) return;
            select(null, "My account", new AccountView(service, false, passwordChanged, logout));
        });
        Button signOut = new Button("Sign out");
        signOut.setId("shell-logout");
        signOut.setOnAction(e -> { if (NavigationGuard.allowLeave(getCenter())) logout.run(); });
        menu.setId("shell-menu");
        menu.setOnAction(e -> setMenuVisible(getLeft() == null));
        reducedMotion.setSelected(Boolean.getBoolean("aceos.reducedMotion"));
        reducedMotion.setId("shell-reduced-motion");
        reducedMotion.setOnAction(e -> {
            if (reducedMotion.isSelected() && transition != null) {
                transition.stop();
                if (getCenter() != null) getCenter().setOpacity(1);
            }
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox heading = new HBox(12, brand, identity, spacer, account, signOut);
        heading.setAlignment(Pos.CENTER_LEFT);
        HBox controls = new HBox(12, menu, screen, reducedMotion);
        controls.setAlignment(Pos.CENTER_LEFT);
        screen.setMinWidth(0);
        screen.getStyleClass().add("shell-identity");
        VBox header = new VBox(8, heading, controls);
        header.setPadding(new Insets(12, 16, 12, 16));
        header.getStyleClass().add("shell-header");
        setTop(header);
        navigation.setPadding(new Insets(16, 12, 16, 12));
        navigation.getStyleClass().add("shell-navigation");
        navigationScroll.getStyleClass().add("shell-navigation");
        navigationScroll.setFitToWidth(true);
        navigationScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        navigationScroll.setPrefWidth(216);
        navigationScroll.setMinWidth(216);
        add("dashboard", "Dashboard", () -> dashboard(service));
        if (role == UserRole.REQUESTER) {
            add("report", "Report complaint", () -> new ComplaintManagementView(service, id -> openRepair(service, false, id)));
            add("complaints", "My complaints", () -> new RepairFlowView(service, false, null));
            add("jobs", "Repair progress", () -> new RepairFlowView(service, true, null));
            add("assets", "Reporting assets", () -> new AssetDirectoryView(service));
        } else if (role == UserRole.TECHNICIAN) {
            add("maintenance", "My maintenance", () -> new RepairFlowView(service, true, null));
            add("jobs", "Assigned jobs", () -> new RepairFlowView(service, true, null));
            add("complete-inspection", "Complete inspection", () -> new AssetInspectionManagementView(service));
            add("inspections", "Assigned inspections", () -> new AssetInspectionDirectoryView(service));
            add("complaints", "Linked complaints", () -> new RepairFlowView(service, false, null));
            add("assets", "Assigned assets", () -> new AssetDirectoryView(service));
            add("schedule-directory", "Assigned asset schedules", () -> new PreventiveMaintenanceDirectoryView(service));
        } else {
            add("assets", "Assets", () -> new AssetDirectoryView(service));
            add("complaints", "Complaints", () -> new RepairFlowView(service, false, null));
            add("jobs", "Work orders", () -> new RepairFlowView(service, true, null));
            add("inspections", "Inspections", () -> new InspectionFlowView(service, null, null, false));
            add("schedules", "Preventive schedules", () -> new PreventiveScheduleFlowView(service, null, null, false));
            add("accounts", "Accounts", () -> new AccountManagementView(service));
            shortcut("review", "complaints", "Review complaints", () -> new RepairFlowView(service, false, "SUBMITTED"));
            shortcut("assign", "complaints", "Assign work", () -> new RepairFlowView(service, false, "ACCEPTED"));
            shortcut("verify", "jobs", "Verify maintenance", () -> new RepairFlowView(service, true, "COMPLETED"));
            shortcut("assignments", "inspections", "Inspection assignments", () -> new InspectionFlowView(service, "UNASSIGNED", null, false));
            shortcut("overdue-inspections", "inspections", "Overdue inspections", () -> new InspectionFlowView(service, "OVERDUE", null, false));
            shortcut("availability", "assets", "Assets", () -> new AssetDirectoryView(service));
            shortcut("register", "assets", "Assets", () -> new AssetDirectoryView(service, true));
            shortcut("overdue-schedules", "schedules", "Overdue schedules", () -> new PreventiveScheduleFlowView(service, "OVERDUE", null, false));
            shortcut("create-schedule", "schedules", "Preventive schedules", () -> new PreventiveScheduleFlowView(service, null, null, true));
            shortcut("schedule-inspection", "inspections", "Inspections", () -> new InspectionFlowView(service, null, null, true));
            shortcut("schedule-directory", "schedules", "Preventive schedules", () -> new PreventiveScheduleFlowView(service, null, null, false));
            shortcut("users", "accounts", "Accounts", () -> new AccountManagementView(service));
        }
        widthProperty().addListener((observable, before, after) -> {
            boolean small = after.doubleValue() < 960;
            if (small != narrow) {
                narrow = small;
                setMenuVisible(!small);
            }
        });
        setMenuVisible(true);
        navigate("dashboard");
    }

    private Node dashboard(AceOsService service) {
        try {
            return new DashboardView(service, this::navigate, (kind, id) -> {
                if (kind.equals("Inspection")) {
                    if (!NavigationGuard.allowLeave(getCenter())) return;
                    select("inspections", "Inspection #" + id, new InspectionFlowView(service, null, id, false));
                    if (narrow) setMenuVisible(false);
                } else openRepair(service, kind.equals("Work order"), id);
            });
        } catch (AceOsServiceException e) {
            Label error = new Label(e.getMessage() + " Sign out and sign in again.");
            error.setWrapText(true);
            error.getStyleClass().add("error");
            return new VBox(16, error);
        }
    }

    private void openRepair(AceOsService service, boolean work, int id) {
        if (!NavigationGuard.allowLeave(getCenter())) return;
        String route = work ? "jobs" : "complaints";
        select(route, work ? "Repair progress" : "Complaint progress", new RepairFlowView(service, work, null, id));
        if (narrow) setMenuVisible(false);
    }

    private void add(String key, String text, Supplier<Node> view) {
        Button button = new Button(text);
        button.setId("nav-" + key);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setWrapText(true);
        button.getStyleClass().add("nav-button");
        button.setOnAction(e -> navigate(key));
        destinations.put(key, new Destination(button, view));
        navigation.getChildren().add(button);
    }

    private void shortcut(String key, String parent, String title, Supplier<Node> view) {
        shortcuts.put(key, new Shortcut(parent, title, view));
    }

    public void navigate(String key) {
        Destination destination = destinations.get(key);
        Shortcut shortcut = shortcuts.get(key);
        if (destination == null && shortcut == null) throw new IllegalArgumentException("Screen unavailable for this role");
        if (!NavigationGuard.allowLeave(getCenter())) return;
        if (destination != null) select(key, destination.button().getText(), destination.view().get());
        else select(shortcut.parent(), shortcut.title(), shortcut.view().get());
        if (narrow) setMenuVisible(false);
    }

    private void select(String key, String title, Node view) {
        if (transition != null) transition.stop();
        destinations.forEach((route, destination) -> {
            destination.button().getStyleClass().remove("nav-active");
            boolean active = route.equals(key);
            if (active) destination.button().getStyleClass().add("nav-active");
            destination.button().setAccessibleText(destination.button().getText() + (active ? ", current screen" : ""));
        });
        screen.setText(title);
        screen.setAccessibleText("Current screen: " + title);
        // Connected views own their scrolling; plain forms use one shell wrapper.
        if (!(view instanceof ScrollPane) && !(view instanceof AssetDirectoryView) && !(view instanceof AssetManagementView) && !(view instanceof AccountManagementView)) {
            ScrollPane scroll = new ScrollPane(view);
            scroll.setFitToWidth(true);
            if (view instanceof AccountView) scroll.getStyleClass().add("dashboard");
            view = scroll;
        }
        setCenter(view);
        if (!reducedMotion.isSelected()) {
            transition = new FadeTransition(Duration.millis(120), view);
            transition.setFromValue(0.85);
            transition.setToValue(1);
            transition.play();
        }
    }

    private void setMenuVisible(boolean visible) {
        setLeft(visible ? navigationScroll : null);
        menu.setText(visible ? "Hide menu" : "Show menu");
        menu.setAccessibleText(menu.getText() + ", navigation " + (visible ? "expanded" : "collapsed"));
    }
}
