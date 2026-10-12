package com.aceos.ui.view;

import com.aceos.service.AceOsService;
import com.aceos.ui.FxTasks;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class LoginView extends StackPane {
    public LoginView(AceOsService service, Runnable onSignedIn) {
        setPadding(new Insets(24));
        VBox form = new VBox(12);
        form.getStyleClass().add("login-card");
        form.setMaxWidth(420);
        form.setMaxHeight(Region.USE_PREF_SIZE);
        Label title = new Label("Welcome to AceOS");
        title.getStyleClass().add("title");
        Label subtitle = new Label("Sign in to your local workspace.");
        subtitle.getStyleClass().add("muted");
        TextField email = new TextField();
        email.setPromptText("you@institution.edu");
        email.setId("login-email");
        PasswordField hidden = new PasswordField();
        TextField visible = new TextField();
        hidden.setId("login-password");
        visible.textProperty().bindBidirectional(hidden.textProperty());
        email.setAccessibleText("Email");
        hidden.setAccessibleText("Password");
        visible.setAccessibleText("Password");
        CheckBox show = new CheckBox("Show password");
        hidden.visibleProperty().bind(show.selectedProperty().not());
        hidden.managedProperty().bind(hidden.visibleProperty());
        visible.visibleProperty().bind(show.selectedProperty());
        visible.managedProperty().bind(visible.visibleProperty());
        VBox passwords = new VBox(hidden, visible);
        Label feedback = new Label();
        feedback.setWrapText(true);
        feedback.getStyleClass().add("error");
        Button signin = new Button("Sign in");
        signin.setId("login-submit");
        signin.getStyleClass().add("primary");
        signin.setDefaultButton(true);
        signin.setMaxWidth(Double.MAX_VALUE);
        signin.setOnAction(
                e -> {
                    String mail = email.getText(), password = hidden.getText();
                    signin.setDisable(true);
                    email.setDisable(true);
                    passwords.setDisable(true);
                    show.setDisable(true);
                    feedback.setText("Signing in…");
                    FxTasks.run(
                            () -> service.signIn(mail, password),
                            user -> {
                                hidden.clear();
                                onSignedIn.run();
                            },
                            error -> feedback.setText(error.getMessage()),
                            () -> {
                                signin.setDisable(false);
                                email.setDisable(false);
                                passwords.setDisable(false);
                                show.setDisable(false);
                            });
                });
        Label reset = new Label("Forgot your password? Contact your administrator.");
        reset.setWrapText(true);
        reset.getStyleClass().add("muted");
        form.getChildren()
                .addAll(
                        title,
                        subtitle,
                        new Label("Email"),
                        email,
                        new Label("Password"),
                        passwords,
                        show,
                        signin,
                        feedback,
                        reset);
        getChildren().add(form);
        StackPane.setAlignment(form, Pos.CENTER);
    }
}
