package com.aceos.ui.view;

import com.aceos.model.UserRole;
import com.aceos.service.AceOsService;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class UserManagementView extends VBox {

    public UserManagementView(AceOsService service) {
        setSpacing(20);
        setPadding(new Insets(40));
        setMaxWidth(720);

        Label title = new Label("User Management");
        title.setStyle(
                "-fx-font-size: 28px;" + "-fx-font-weight: bold;" + "-fx-text-fill: #1e293b;");

        Label description = new Label("Register requesters, technicians, " + "and administrators.");
        description.setStyle("-fx-font-size: 15px;" + "-fx-text-fill: #475569;");

        TextField nameField = new TextField();
        nameField.setPromptText("Enter the user's name");

        TextField emailField = new TextField();
        emailField.setPromptText("Enter the user's email");

        ComboBox<UserRole> roleBox = new ComboBox<>();
        roleBox.getItems().addAll(UserRole.values());
        roleBox.setPromptText("Select a role");
        roleBox.setMaxWidth(Double.MAX_VALUE);

        GridPane form = new GridPane();
        form.setHgap(16);
        form.setVgap(16);

        form.add(new Label("Name"), 0, 0);
        form.add(nameField, 1, 0);

        form.add(new Label("Email"), 0, 1);
        form.add(emailField, 1, 1);

        form.add(new Label("Role"), 0, 2);
        form.add(roleBox, 1, 2);

        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(emailField, Priority.ALWAYS);
        GridPane.setHgrow(roleBox, Priority.ALWAYS);

        Button registerButton = new Button("Register User");
        registerButton.setMinHeight(40);

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);

        registerButton.setOnAction(
                event ->
                        registerUser(
                                service,
                                nameField,
                                emailField,
                                roleBox,
                                registerButton,
                                statusLabel));

        getChildren().addAll(title, description, form, registerButton, statusLabel);
    }

    private void registerUser(
            AceOsService service,
            TextField nameField,
            TextField emailField,
            ComboBox<UserRole> roleBox,
            Button registerButton,
            Label statusLabel) {
        registerButton.setDisable(true);

        var input1 = nameField.getText();
        var input2 = emailField.getText();
        var input3 = roleBox.getValue();
        com.aceos.ui.FxTasks.run(
                () -> service.registerUser(input1, input2, input3),
                registeredUser -> {
                    statusLabel.setStyle("-fx-text-fill: #15803d;");
                    statusLabel.setText(
                            "Registered user #"
                                    + registeredUser.getUserId()
                                    + ": "
                                    + registeredUser.getName());

                    nameField.clear();
                    emailField.clear();
                    roleBox.getSelectionModel().clearSelection();
                    nameField.requestFocus();
                },
                exception -> {
                    statusLabel.setStyle("-fx-text-fill: #b91c1c;");
                    statusLabel.setText(exception.getMessage());
                },
                () -> {
                    registerButton.setDisable(false);
                });
    }
}
