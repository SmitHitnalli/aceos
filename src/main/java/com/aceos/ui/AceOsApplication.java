package com.aceos.ui;

import com.aceos.ui.view.AssetManagementView;
import com.aceos.service.AceOsService;
import com.aceos.ui.view.UserManagementView;
import com.aceos.ui.view.ComplaintManagementView;
import com.aceos.ui.view.ComplaintReviewView;
import com.aceos.ui.view.WorkOrderManagementView;
import com.aceos.ui.view.WorkOrderLifecycleView;
import com.aceos.ui.view.WorkOrderVerificationView;
import com.aceos.ui.view.DashboardView;
import com.aceos.ui.view.UserDirectoryView;
import com.aceos.ui.view.AssetDirectoryView;
import com.aceos.ui.view.ComplaintDirectoryView;
import com.aceos.ui.view.WorkOrderDirectoryView;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.control.ScrollPane;

public class AceOsApplication extends Application {

    private final AceOsService service
            = new AceOsService();

    private BorderPane root;

    @Override
    public void start(Stage primaryStage) {
        root = new BorderPane();
        root.setTop(createHeader());
        root.setLeft(createNavigation());

        root.setCenter(new DashboardView());

        Scene scene = new Scene(root, 1000, 650);

        primaryStage.setTitle(
                "AceOS - Asset Operations"
        );
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(520);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private HBox createHeader() {
        Label name = new Label("AceOS");
        name.setStyle(
                "-fx-font-size: 24px;"
                + "-fx-font-weight: bold;"
                + "-fx-text-fill: white;"
        );

        Label description = new Label(
                "Asset, Compliance & Equipment "
                + "Operations System"
        );
        description.setStyle(
                "-fx-text-fill: #dbeafe;"
        );

        HBox header = new HBox(
                16,
                name,
                description
        );
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(18, 24, 18, 24));
        header.setStyle("-fx-background-color: #1e3a5f;");

        return header;
    }

    private ScrollPane createNavigation() {
        VBox navigation = new VBox(10);
        navigation.setPadding(
                new Insets(24, 16, 24, 16)
        );
        navigation.setPrefWidth(210);
        navigation.setStyle(
                "-fx-background-color: #e8eef5;"
        );

        navigation.getChildren().addAll(
                createNavigationButton(
                        "Dashboard",
                        () -> root.setCenter(
                                new DashboardView()
                        )
                ),
                createNavigationButton(
                        "Users",
                        () -> root.setCenter(
                                new UserManagementView(service)
                        )
                ),
                createNavigationButton(
                        "User Directory",
                        () -> root.setCenter(
                                new UserDirectoryView(service)
                        )
                ),
                createNavigationButton(
                        "Assets",
                        () -> root.setCenter(
                                new AssetManagementView(service)
                        )
                ),
                createNavigationButton(
                        "Asset Directory",
                        () -> root.setCenter(
                                new AssetDirectoryView(service)
                        )
                ),
                createNavigationButton(
                        "Complaints",
                        () -> root.setCenter(
                                new ComplaintManagementView(service)
                        )
                ),
                createNavigationButton(
                        "Complaint Directory",
                        () -> root.setCenter(
                                new ComplaintDirectoryView(service)
                        )
                ),
                createNavigationButton(
                        "Review Complaints",
                        () -> root.setCenter(
                                new ComplaintReviewView(service)
                        )
                ),
                createNavigationButton(
                        "Work Orders",
                        () -> root.setCenter(
                                new WorkOrderManagementView(service)
                        )
                ),
                createNavigationButton(
                        "Work Order Directory",
                        () -> root.setCenter(
                                new WorkOrderDirectoryView(service)
                        )
                ),
                createNavigationButton(
                        "Maintenance",
                        () -> root.setCenter(
                                new WorkOrderLifecycleView(service)
                        )
                ),
                createNavigationButton(
                        "Verify Maintenance",
                        () -> root.setCenter(
                                new WorkOrderVerificationView(service)
                        )
                )
        );

        ScrollPane scrollPane
                = new ScrollPane(navigation);

        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER
        );
        scrollPane.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED
        );
        scrollPane.setPrefWidth(228);
        scrollPane.setStyle(
                "-fx-background-color: #e8eef5;"
                + "-fx-background: #e8eef5;"
        );

        return scrollPane;
    }

    private Button createNavigationButton(
            String buttonText,
            Runnable action) {
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
