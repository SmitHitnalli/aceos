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
        save.getStyleClass().add("primary");
        Button logout = new Button("Sign out");
        save.setOnAction(
                e -> {
                    if (!next.getText().equals(confirmation.getText())) {
                        status.setText("The new passwords do not match");
                        return;
                    }
                    String old = current.getText(), password = next.getText();
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
                                save.setDisable(false);
                                logout.setDisable(false);
                            });
                });
        logout.setOnAction(e -> onLogout.run());
        getChildren()
                .addAll(
                        title,
                        help,
                        new Label(required ? "Temporary password" : "Current password"),
                        current,
                        new Label("New password"),
                        next,
                        new Label("Confirm new password"),
                        confirmation,
                        save,
                        logout,
                        status);
    }
}
