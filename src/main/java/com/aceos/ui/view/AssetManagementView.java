package com.aceos.ui.view;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class AssetManagementView extends VBox {

    public AssetManagementView() {
        setSpacing(20);
        setPadding(new Insets(40));
        setMaxWidth(720);

        Label title = new Label("Asset Management");
        title.setStyle(
                "-fx-font-size: 28px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-text-fill: #1e293b;"
        );

        Label description = new Label(
                "Register institutional equipment "
                        + "and other assets."
        );
        description.setStyle(
                "-fx-font-size: 15px;"
                        + "-fx-text-fill: #475569;"
        );

        TextField assetCodeField = new TextField();
        assetCodeField.setPromptText(
                "Example: MUJ-PROJ-001"
        );

        TextField nameField = new TextField();
        nameField.setPromptText(
                "Enter the asset name"
        );

        TextField categoryField = new TextField();
        categoryField.setPromptText(
                "Example: Projector"
        );

        TextField locationField = new TextField();
        locationField.setPromptText(
                "Example: Academic Block 1 - Room 301"
        );

        GridPane form = new GridPane();
        form.setHgap(16);
        form.setVgap(16);

        form.add(new Label("Asset Code"), 0, 0);
        form.add(assetCodeField, 1, 0);

        form.add(new Label("Name"), 0, 1);
        form.add(nameField, 1, 1);

        form.add(new Label("Category"), 0, 2);
        form.add(categoryField, 1, 2);

        form.add(new Label("Location"), 0, 3);
        form.add(locationField, 1, 3);

        GridPane.setHgrow(
                assetCodeField,
                Priority.ALWAYS
        );
        GridPane.setHgrow(
                nameField,
                Priority.ALWAYS
        );
        GridPane.setHgrow(
                categoryField,
                Priority.ALWAYS
        );
        GridPane.setHgrow(
                locationField,
                Priority.ALWAYS
        );

        Button registerButton =
                new Button("Register Asset");
        registerButton.setMinHeight(40);

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.setStyle(
                "-fx-text-fill: #b45309;"
        );

        registerButton.setOnAction(event ->
                statusLabel.setText(
                        "The form is working, but the asset "
                                + "has not been saved yet. "
                                + "The service connection is "
                                + "the next development chunk."
                )
        );

        getChildren().addAll(
                title,
                description,
                form,
                registerButton,
                statusLabel
        );
    }
}