package com.aceos.ui;

import com.aceos.service.AceOsService;
import com.aceos.ui.view.AssetDirectoryView;
import com.aceos.ui.view.AssetInspectionDirectoryView;
import com.aceos.ui.view.AssetInspectionManagementView;
import com.aceos.ui.view.AssetManagementView;
import com.aceos.ui.view.ComplaintDirectoryView;
import com.aceos.ui.view.ComplaintManagementView;
import com.aceos.ui.view.ComplaintReviewView;
import com.aceos.ui.view.DashboardView;
import com.aceos.ui.view.PreventiveMaintenanceDirectoryView;
import com.aceos.ui.view.PreventiveMaintenanceManagementView;
import com.aceos.ui.view.UserDirectoryView;
import com.aceos.ui.view.WorkOrderDirectoryView;
import com.aceos.ui.view.WorkOrderLifecycleView;
import com.aceos.ui.view.WorkOrderManagementView;
import com.aceos.ui.view.WorkOrderVerificationView;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class AceOsApplication extends Application {

    private AceOsService service = new AceOsService();

    private BorderPane root;

    @Override
    public void start(Stage primaryStage) {
        root = new BorderPane();
        showLogin();

        Scene scene = new Scene(root, 1000, 650);
        scene.getStylesheets().add(getClass().getResource("foundation.css").toExternalForm());

        primaryStage.setTitle("AceOS - Asset Operations");
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(520);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void showLogin() {
        root.setTop(null);
        root.setLeft(null);
        root.setCenter(new com.aceos.ui.view.LoginView(service, this::showWorkspace));
    }

    private void logout() {
        service.signOut();
        service = new AceOsService();
        showLogin();
    }

    private void showWorkspace() {
        if (service.passwordChangeRequired()) {
            root.setCenter(
                    new com.aceos.ui.view.AccountView(
                            service, true, this::showWorkspace, this::logout));
            return;
        }
        root.setTop(createHeader());
        root.setLeft(createNavigation());
        root.setCenter(new DashboardView(service));
    }

    private com.aceos.model.UserRole role() {
        try {
            return service.signedInUser().getRole();
        } catch (com.aceos.service.AceOsServiceException e) {
            return null;
        }
    }

    private HBox createHeader() {
        Label name = new Label("AceOS");
        name.setStyle("-fx-font-size: 24px;" + "-fx-font-weight: bold;" + "-fx-text-fill: white;");

        Label description = new Label("Asset, Compliance & Equipment " + "Operations System");
        description.setStyle("-fx-text-fill: #dbeafe;");

        HBox header = new HBox(16, name, description);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(18, 24, 18, 24));
        header.setStyle("-fx-background-color: #1e3a5f;");

        try {
            var user = service.signedInUser();
            Label identity = new Label(user.getName() + " · " + user.getRole());
            identity.setStyle("-fx-text-fill: white;");
            Button account = new Button("My account");
            account.setOnAction(
                    e ->
                            root.setCenter(
                                    new com.aceos.ui.view.AccountView(
                                            service, false, this::showWorkspace, this::logout)));
            Button logout = new Button("Sign out");
            logout.setOnAction(e -> logout());
            header.getChildren().setAll(name, identity, account, logout);
        } catch (com.aceos.service.AceOsServiceException e) {
            logout();
        }
        return header;
    }

    private ScrollPane createNavigation() {
        VBox navigation = new VBox(10);
        navigation.setPadding(new Insets(24, 16, 24, 16));
        navigation.setPrefWidth(210);
        navigation.setStyle("-fx-background-color: #e8eef5;");

        addNavigation(navigation, "Dashboard", () -> new DashboardView(service));
        if (role() == com.aceos.model.UserRole.ADMINISTRATOR) {
            addNavigation(
                    navigation,
                    "Accounts",
                    () -> new com.aceos.ui.view.AccountManagementView(service));
            addNavigation(navigation, "User Directory", () -> new UserDirectoryView(service));
            addNavigation(navigation, "Register Assets", () -> new AssetManagementView(service));
            addNavigation(navigation, "Review Complaints", () -> new ComplaintReviewView(service));
            addNavigation(navigation, "Assign Work", () -> new WorkOrderManagementView(service));
            addNavigation(
                    navigation, "Assignments", () -> new com.aceos.ui.view.AssignmentView(service));
            addNavigation(
                    navigation, "Verify Maintenance", () -> new WorkOrderVerificationView(service));
            addNavigation(
                    navigation,
                    "Manage Schedules",
                    () -> new PreventiveMaintenanceManagementView(service));
            addNavigation(
                    navigation,
                    "Schedule Directory",
                    () -> new PreventiveMaintenanceDirectoryView(service));
            addNavigation(
                    navigation,
                    "Schedule Inspections",
                    () -> new AssetInspectionManagementView(service));
        }
        if (role() == com.aceos.model.UserRole.REQUESTER)
            addNavigation(
                    navigation, "Report Complaint", () -> new ComplaintManagementView(service));
        addNavigation(navigation, "Asset Directory", () -> new AssetDirectoryView(service));
        addNavigation(navigation, "Complaint Directory", () -> new ComplaintDirectoryView(service));
        addNavigation(
                navigation, "Work Order Directory", () -> new WorkOrderDirectoryView(service));
        if (role() == com.aceos.model.UserRole.TECHNICIAN)
            addNavigation(navigation, "My Maintenance", () -> new WorkOrderLifecycleView(service));
        if (role() != com.aceos.model.UserRole.REQUESTER) {
            addNavigation(
                    navigation,
                    "Inspection Directory",
                    () -> new AssetInspectionDirectoryView(service));
            if (role() == com.aceos.model.UserRole.TECHNICIAN)
                addNavigation(
                        navigation,
                        "Complete Inspection",
                        () -> new AssetInspectionManagementView(service));
        }

        ScrollPane scrollPane = new ScrollPane(navigation);

        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPrefWidth(228);
        scrollPane.setStyle("-fx-background-color: #e8eef5;" + "-fx-background: #e8eef5;");

        return scrollPane;
    }

    private void addNavigation(
            VBox navigation, String text, java.util.function.Supplier<javafx.scene.Node> view) {
        navigation
                .getChildren()
                .add(createNavigationButton(text, () -> root.setCenter(view.get())));
    }

    private Button createNavigationButton(String buttonText, Runnable action) {
        Button button = new Button(buttonText);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(42);

        button.setOnAction(event -> action.run());

        return button;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
