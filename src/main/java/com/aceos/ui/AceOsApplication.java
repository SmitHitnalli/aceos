package com.aceos.ui;

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

public class AceOsApplication extends Application {

    @Override
    public void start(Stage primaryStage) {
        Label pageTitle = new Label("Dashboard");
        Label pageMessage = new Label(
                "Choose a section from the navigation menu."
        );

        BorderPane root = new BorderPane();
        root.setTop(createHeader());
        root.setLeft(
                createNavigation(pageTitle, pageMessage)
        );
        root.setCenter(
                createContent(pageTitle, pageMessage)
        );

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

    private VBox createNavigation(
            Label pageTitle,
            Label pageMessage) {
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
                        "Dashboard",
                        "Choose a section from "
                                + "the navigation menu.",
                        pageTitle,
                        pageMessage
                ),
                createNavigationButton(
                        "Users",
                        "User Management",
                        "User registration will be "
                                + "built in the next chunk.",
                        pageTitle,
                        pageMessage
                ),
                createNavigationButton(
                        "Assets",
                        "Asset Management",
                        "Asset controls will be added later.",
                        pageTitle,
                        pageMessage
                ),
                createNavigationButton(
                        "Complaints",
                        "Complaint Management",
                        "Complaint controls will be added later.",
                        pageTitle,
                        pageMessage
                ),
                createNavigationButton(
                        "Work Orders",
                        "Work Orders",
                        "Work-order controls will be added later.",
                        pageTitle,
                        pageMessage
                ),
                createNavigationButton(
                        "Maintenance",
                        "Maintenance History",
                        "Maintenance history will be added later.",
                        pageTitle,
                        pageMessage
                )
        );

        return navigation;
    }

    private VBox createContent(
            Label pageTitle,
            Label pageMessage) {
        pageTitle.setStyle(
                "-fx-font-size: 28px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        pageMessage.setStyle(
                "-fx-font-size: 15px;"
                        + "-fx-text-fill: #475569;"
        );
        pageMessage.setWrapText(true);

        VBox content = new VBox(
                14,
                pageTitle,
                pageMessage
        );
        content.setPadding(
                new Insets(40, 40, 40, 40)
        );

        return content;
    }

    private Button createNavigationButton(
            String buttonText,
            String title,
            String message,
            Label pageTitle,
            Label pageMessage) {
        Button button = new Button(buttonText);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(42);

        button.setOnAction(event -> {
            pageTitle.setText(title);
            pageMessage.setText(message);
        });

        return button;
    }

    public static void main(String[] args) {
        launch(args);
    }
}