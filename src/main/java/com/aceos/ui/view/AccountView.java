package com.aceos.ui.view;

import com.aceos.service.AceOsService;
import com.aceos.ui.FxTasks;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public final class AccountView extends VBox {
    public AccountView(
            AceOsService service, boolean required, Runnable onChanged, Runnable onLogout) {
        setSpacing(12);
        setPadding(new Insets(24));
        setMaxWidth(560);
        setId("own-account");
        Label title = new Label(required ? "Choose your password" : "Change password");
        title.getStyleClass().add("title");
        Label help = new Label("Use 15–128 characters. Passphrases and paste are supported.");
        help.setWrapText(true);
        PasswordField current = new PasswordField(),
                next = new PasswordField(),
                confirmation = new PasswordField();
        current.setAccessibleText(required ? "Temporary password" : "Current password");
        next.setAccessibleText("New password");
        confirmation.setAccessibleText("Confirm new password");
        current.setPromptText(required ? "Temporary password" : "Current password");
        next.setPromptText("New password");
        confirmation.setPromptText("Confirm new password");
        Label status = new Label();
        status.setWrapText(true);
        status.getStyleClass().add("error");
        Button save = new Button("Save password");
        save.setId("password-save");
        current.setId("password-current");
        next.setId("password-next");
        confirmation.setId("password-confirmation");
        status.setId("password-feedback");
        boolean[] busy = {false};
        save.getStyleClass().add("primary");
        Button logout = new Button("Sign out");
        save.setOnAction(
                e -> {
                    if (busy[0]) return;
                    if (!next.getText().equals(confirmation.getText())) {
                        status.setText("The new passwords do not match");
                        return;
                    }
                    String old = current.getText(), password = next.getText();
                    busy[0] = true;
                    current.setDisable(true);
                    next.setDisable(true);
                    confirmation.setDisable(true);
                    save.setDisable(true);
                    logout.setDisable(true);
                    status.setText("Saving…");
                    FxTasks.run(
                            () -> {
                                service.changePassword(old, password);
                                return true;
                            },
                            ok -> {
                                current.clear();
                                next.clear();
                                confirmation.clear();
                                onChanged.run();
                            },
                            error -> status.setText(error.getMessage()),
                            () -> {
                                busy[0] = false;
                                current.setDisable(false);
                                next.setDisable(false);
                                confirmation.setDisable(false);
                                save.setDisable(false);
                                logout.setDisable(false);
                            });
                });
        logout.setOnAction(e -> onLogout.run());
        current.setOnAction(e -> next.requestFocus());
        next.setOnAction(e -> confirmation.requestFocus());
        confirmation.setOnAction(e -> save.fire());
        Label currentLabel = new Label(required ? "Temporary password" : "Current password");
        currentLabel.setLabelFor(current);
        Label nextLabel = new Label("New password");
        nextLabel.setLabelFor(next);
        Label confirmationLabel = new Label("Confirm new password");
        confirmationLabel.setLabelFor(confirmation);
        if (!required) {
            try {
                var user = service.signedInUser();
                Label identity = new Label("Account #" + user.getUserId() + " · " + user.getName() + "\n" + user.getEmail() + " · " + user.getRole());
                identity.setId("own-account-identity");
                identity.setWrapText(true);
                identity.getStyleClass().add("muted");
                getChildren().add(identity);
            } catch (com.aceos.service.AceOsServiceException error) {
                status.setText(error.getMessage());
                save.setDisable(true);
            }
        }
        getChildren()
                .addAll(
                        title,
                        help,
                        currentLabel,
                        current,
                        nextLabel,
                        next,
                        confirmationLabel,
                        confirmation,
                        save,
                        logout,
                        status);
    }
}
