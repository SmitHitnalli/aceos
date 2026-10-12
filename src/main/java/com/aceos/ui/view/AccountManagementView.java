package com.aceos.ui.view;

import com.aceos.model.UserRole;
import com.aceos.security.*;
import com.aceos.service.*;
import com.aceos.ui.FxTasks;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.concurrent.Callable;

public final class AccountManagementView extends ScrollPane {
    private final AceOsService service;
    private final ComboBox<AccountSummary> accounts = new ComboBox<>();
    private final Label status = new Label();
    private final VBox content = new VBox(12);
    private int pending;

    public AccountManagementView(AceOsService service) {
        this.service = service;
        content.setPadding(new Insets(24));
        setContent(content);
        setFitToWidth(true);
        Label heading = new Label("Accounts");
        heading.getStyleClass().add("title");
        TextField name = new TextField(), email = new TextField();
        ComboBox<UserRole> role = new ComboBox<>();
        role.getItems().addAll(UserRole.values());
        role.setValue(UserRole.REQUESTER);
        Button create = new Button("Create account");
        create.getStyleClass().add("primary");
        create.setOnAction(
                e -> {
                    String n = name.getText(), mail = email.getText();
                    UserRole r = role.getValue();
                    run(
                            () -> service.registerUser(n, mail, r),
                            u -> {
                                status.setText(
                                        "Account created. Select it below to issue its temporary"
                                            + " credential.");
                                name.clear();
                                email.clear();
                                refresh();
                            });
                });
        accounts.setMaxWidth(Double.MAX_VALUE);
        accounts.setButtonCell(cell());
        accounts.setCellFactory(x -> cell());
        Button refresh = new Button("Refresh accounts"),
                issue = new Button("Activate / reset password"),
                disable = new Button("Deactivate"),
                reactivate = new Button("Reactivate"),
                unlock = new Button("Unlock"),
                changeRole = new Button("Change role");
        refresh.setOnAction(e -> refresh());
        issue.setOnAction(
                e -> selected(a -> run(() -> service.issueCredential(a.userId()), this::deliver)));
        reactivate.setOnAction(
                e ->
                        selected(
                                a ->
                                        run(
                                                () -> service.reactivateAccount(a.userId()),
                                                this::deliver)));
        disable.setOnAction(
                e ->
                        selected(
                                a -> {
                                    if (confirm(
                                            "Deactivate " + a.name() + "?",
                                            "Their sessions will be invalidated. History is"
                                                + " retained."))
                                        run(
                                                () -> {
                                                    service.deactivateAccount(a.userId());
                                                    return true;
                                                },
                                                ok -> {
                                                    status.setText("Account deactivated");
                                                    refresh();
                                                });
                                }));
        unlock.setOnAction(
                e ->
                        selected(
                                a ->
                                        run(
                                                () -> {
                                                    service.unlockAccount(a.userId());
                                                    return true;
                                                },
                                                ok -> {
                                                    status.setText("Account unlocked");
                                                    refresh();
                                                })));
        changeRole.setOnAction(
                e ->
                        selected(
                                a -> {
                                    UserRole r = role.getValue();
                                    if (confirm(
                                            "Change role for " + a.name() + "?",
                                            "New role: "
                                                    + r
                                                    + ". Existing sessions will be invalidated."))
                                        run(
                                                () -> {
                                                    service.changeAccountRole(a.userId(), r);
                                                    return true;
                                                },
                                                ok -> {
                                                    status.setText("Role changed");
                                                    refresh();
                                                });
                                }));
        FlowPane actions =
                new FlowPane(8, 8, refresh, issue, reactivate, disable, unlock, changeRole);
        status.setWrapText(true);
        content.getChildren()
                .addAll(
                        heading,
                        new Label("Administrator-created accounts. No public signup."),
                        new Label("Full name"),
                        name,
                        new Label("Email"),
                        email,
                        new Label("Role (also used for Change role)"),
                        role,
                        create,
                        new Separator(),
                        new Label("Select an account"),
                        accounts,
                        actions,
                        status);
        refresh();
    }

    private ListCell<AccountSummary> cell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(AccountSummary a, boolean empty) {
                super.updateItem(a, empty);
                setText(
                        empty || a == null
                                ? null
                                : a.userId()
                                        + " · "
                                        + a.name()
                                        + " · "
                                        + a.role()
                                        + " · "
                                        + a.state()
                                        + (a.locked() ? " · Locked" : ""));
            }
        };
    }

    private void selected(java.util.function.Consumer<AccountSummary> action) {
        AccountSummary a = accounts.getValue();
        if (a == null) status.setText("Select an account first");
        else action.accept(a);
    }

    private boolean confirm(String title, String text) {
        Alert alert =
                new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(title);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private <T> void run(Callable<T> call, java.util.function.Consumer<T> success) {
        pending++;
        content.setDisable(true);
        status.setText("Working…");
        FxTasks.run(
                call,
                success,
                error -> status.setText(error.getMessage()),
                () -> {
                    pending--;
                    content.setDisable(pending > 0);
                });
    }

    private void refresh() {
        run(
                service::getAccounts,
                rows -> {
                    accounts.getItems().setAll(rows);
                    status.setText("Accounts refreshed");
                });
    }

    private void deliver(IssuedCredential credential) {
        TextField secret = new TextField(credential.secret());
        secret.setEditable(false);
        secret.setPrefColumnCount(35);
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Temporary credential — shown once");
        dialog.getDialogPane()
                .setContent(
                        new VBox(
                                12,
                                new Label(
                                        "User #"
                                                + credential.userId()
                                                + " · expires "
                                                + credential.expiresAt()),
                                secret,
                                new Label(
                                        "Deliver privately. First login requires a new"
                                            + " password.")));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
        secret.clear();
        status.setText("Credential issued. It cannot be displayed again.");
        refresh();
    }
}
