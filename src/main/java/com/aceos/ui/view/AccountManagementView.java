package com.aceos.ui.view;

import com.aceos.model.UserRole;
import com.aceos.security.AccountSummary;
import com.aceos.security.IssuedCredential;
import com.aceos.service.AceOsService;
import com.aceos.service.AceOsServiceException;
import com.aceos.ui.FxTasks;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/** Credential-free directory; existing services own authorization and transactions. */
public class AccountManagementView extends BorderPane {
    private final AceOsService service;
    private final ObservableList<AccountSummary> rows = FXCollections.observableArrayList();
    private final FilteredList<AccountSummary> filtered = new FilteredList<>(rows);
    private final TableView<AccountSummary> table = new TableView<>(filtered);
    private final TextField search = new TextField(), name = new TextField(), email = new TextField();
    private final ComboBox<String> state = new ComboBox<>();
    private final ComboBox<UserRole> createRole = new ComboBox<>(), newRole = new ComboBox<>();
    private final Label feedback = text("", ""), details = text("", "");
    private final Button refresh = button("Refresh accounts", "refresh"), open = button("Open selected account", "open"),
            createPage = button("Create account", "new"), create = button("Create account", "create"),
            back = button("Back to accounts", "back"), issue = button("Activate / reset password", "issue"),
            reactivate = button("Reactivate", "reactivate"), deactivate = button("Deactivate", "deactivate"),
            unlock = button("Unlock", "unlock"), changeRole = button("Change role", "role-save");
    private final VBox list = new VBox(12), detail = new VBox(12), form = new VBox(12);
    private final ScrollPane scroll = new ScrollPane();
    private AccountSummary selected;
    private int ownId;
    private Integer preferredId;
    private boolean busy, fresh;
    private String page = "list";

    public AccountManagementView(AceOsService service) {
        this.service = service; setId("account-management"); getStyleClass().add("dashboard");
        try {
            var actor = service.signedInUser();
            if (actor.getRole() != UserRole.ADMINISTRATOR) throw new AceOsServiceException("Only administrators can manage accounts.");
            ownId = actor.getUserId();
        } catch (AceOsServiceException error) { setCenter(text(error.getMessage(), "error")); return; }
        feedback.setId("accounts-feedback"); feedback.setAccessibleText("Account feedback");
        feedback.setMinWidth(0); feedback.setMaxWidth(Double.MAX_VALUE);
        HBox activity = new HBox(12, refresh, feedback); activity.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(feedback, Priority.ALWAYS);
        VBox header = new VBox(8, text("Accounts", "title"), text("Create and manage local accounts. Temporary credentials are delivered privately.", "muted"), activity);
        header.setPadding(new Insets(24, 24, 12, 24)); setTop(header);
        list.setPadding(new Insets(12, 24, 24, 24));
        search.setId("accounts-search"); search.setPromptText("Search ID, name, email or role");
        state.setId("accounts-state"); state.getItems().addAll("ALL", "ACTIVE", "PENDING_ACTIVATION", "DISABLED", "LOCKED"); state.setValue("ALL");
        search.setPrefWidth(300); state.setPrefWidth(190);
        list.getChildren().addAll(new FlowPane(12, 8, new VBox(4, label("Search accounts", search), search), new VBox(4, label("Account state", state), state)), table, new FlowPane(8, 8, open, createPage));
        table.setId("accounts-table"); table.getStyleClass().add("repair-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        column("ID", a -> Integer.toString(a.userId())); column("Name", AccountSummary::name);
        column("Role", a -> a.role().name()); column("State", a -> a.state() + (a.locked() ? " · Locked" : ""));
        table.setPlaceholder(text("Loading accounts…", "muted")); table.setMinHeight(60); VBox.setVgrow(table, Priority.ALWAYS);
        search.textProperty().addListener((o,b,a) -> filter()); state.valueProperty().addListener((o,b,a) -> filter());
        table.getSelectionModel().selectedItemProperty().addListener((o,b,a) -> update());
        newRole.valueProperty().addListener((o,b,a) -> update());
        table.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) showSelected(); });
        table.setOnMouseClicked(e -> { if (e.getClickCount() == 2) showSelected(); });
        open.setOnAction(e -> showSelected()); open.getStyleClass().add("primary");
        createPage.setOnAction(e -> { if (!busy) { page = "create"; showPage(); name.requestFocus(); } });
        scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.getStyleClass().add("dashboard");
        for (VBox box : new VBox[]{detail, form}) { box.setPadding(new Insets(12, 24, 24, 24)); box.setMinWidth(0); }
        details.setId("accounts-details"); details.getStyleClass().add("dashboard-card");
        newRole.getItems().addAll(UserRole.values()); newRole.setId("accounts-role");
        detail.getChildren().addAll(back, details, text("Account actions", "section-title"),
                text("Reset, reactivation, role changes and deactivation invalidate existing sessions. Assignment and administrator safeguards still apply.", "muted"),
                new FlowPane(8,8,issue,reactivate,deactivate,unlock), label("New role",newRole), newRole, changeRole);
        createRole.getItems().addAll(UserRole.values()); createRole.setValue(UserRole.REQUESTER); createRole.setId("accounts-create-role");
        name.setId("accounts-name"); email.setId("accounts-email"); name.setPromptText("Full name · up to 100 characters"); email.setPromptText("Email address");
        Button cancel = button("Back to accounts", "create-back"); cancel.setOnAction(e -> showList());
        VBox fields = new VBox(8, label("Full name", name), name, label("Email", email), email, label("Role", createRole), createRole, create);
        fields.getStyleClass().add("dashboard-card");
        form.getChildren().setAll(cancel, text("Create account", "section-title"), text("All fields are required. The account starts pending activation; open its details to issue a 24-hour temporary credential.", "muted"), fields);
        create.getStyleClass().add("primary"); name.setOnAction(e -> email.requestFocus()); email.setOnAction(e -> createRole.requestFocus());
        create.setOnAction(e -> create()); refresh.setOnAction(e -> refresh()); back.setOnAction(e -> showList());
        issue.setOnAction(e -> credential(false)); reactivate.setOnAction(e -> credential(true));
        deactivate.setOnAction(e -> { if (!canAct()) return; int id = selected.userId(); action("Deactivate", () -> { service.deactivateAccount(id); return true; }, "Account deactivated."); });
        unlock.setOnAction(e -> { if (!canAct()) return; int id = selected.userId(); action("Unlock", () -> { service.unlockAccount(id); return true; }, "Account unlocked."); });
        changeRole.setOnAction(e -> {
            if (!canAct() || newRole.getValue() == null) return;
            int id = selected.userId(); UserRole role = newRole.getValue();
            action("Change role to " + role, () -> { service.changeAccountRole(id, role); return true; }, "Role changed.");
        });
        showPage(); refresh();
    }
    private static Label text(String value, String style) {
        Label label = new Label(value); label.setWrapText(true); label.setMinHeight(Region.USE_PREF_SIZE);
        if (!style.isEmpty()) label.getStyleClass().add(style); return label;
    }
    private static Label label(String value, Node node) { Label label = text(value, ""); label.setLabelFor(node); return label; }
    private static Button button(String value, String id) { Button b = new Button(value); b.setId("accounts-" + id); return b; }
    private void column(String title, Function<AccountSummary,String> value) {
        TableColumn<AccountSummary,String> column = new TableColumn<>(title);
        column.setCellValueFactory(d -> new ReadOnlyStringWrapper(value.apply(d.getValue()))); table.getColumns().add(column);
    }
    private void filter() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT), choice = state.getValue();
        filtered.setPredicate(a -> ("ALL".equals(choice) || ("LOCKED".equals(choice) ? a.locked() : a.state().equals(choice)))
                && (a.userId() + " " + a.name() + " " + a.email() + " " + a.role()).toLowerCase(Locale.ROOT).contains(query));
        table.setPlaceholder(text(rows.isEmpty() ? "No accounts available" : "No matching accounts", "muted")); update();
    }
    private void showSelected() {
        if (busy || !fresh || table.getSelectionModel().getSelectedItem() == null) return;
        selected = table.getSelectionModel().getSelectedItem(); preferredId = selected.userId(); newRole.setValue(selected.role());
        page = "detail"; showPage();
    }
    private void showList() { if (busy) return; page = "list"; showPage(); refresh(); }
    private void showPage() {
        if (page.equals("list")) setCenter(list);
        else { scroll.setContent(page.equals("detail") ? detail : form); setCenter(scroll); scroll.setVvalue(0); }
        update();
    }
    private boolean canAct() { return !busy && fresh && selected != null && page.equals("detail"); }
    private void update() {
        refresh.setDisable(busy); list.setDisable(busy); form.setDisable(busy); back.setDisable(busy);
        open.setDisable(busy || !fresh || table.getSelectionModel().getSelectedItem() == null);
        boolean unavailable = !canAct(), disabled = selected != null && selected.state().equals("DISABLED"), self = selected != null && selected.userId() == ownId;
        issue.setDisable(unavailable || disabled); reactivate.setDisable(unavailable || !disabled);
        deactivate.setDisable(unavailable || disabled || self); unlock.setDisable(unavailable || selected == null || !selected.locked());
        newRole.setDisable(unavailable || self); changeRole.setDisable(unavailable || self || newRole.getValue() == null || (selected != null && selected.role() == newRole.getValue()));
        if (selected != null) details.setText("Account #" + selected.userId() + " · " + selected.name() + "\n" + selected.email() + "\nRole: " + selected.role() + "\nState: " + selected.state()
                + "\nPassword replacement required: " + (selected.passwordChangeRequired() ? "Yes" : "No") + "\nLogin cooldown: " + (selected.locked() ? "Locked" : "Not locked") + (self ? "\nThis is your signed-in account." : ""));
        else details.setText("Account no longer available. Return to the list or refresh.");
    }
    private void message(String value, boolean error) {
        feedback.setText(value); feedback.getStyleClass().removeAll("error", "success"); feedback.getStyleClass().add(error ? "error" : "success");
    }
    private void refresh() { load(null); }
    private void load(String success) {
        if (busy) return; busy = true; fresh = false; update();
        if (success == null) message("Loading accounts…", false);
        FxTasks.run(service::getAccounts, loaded -> {
            rows.setAll(loaded); fresh = true;
            if (preferredId != null) { selected = rows.stream().filter(a -> a.userId() == preferredId).findFirst().orElse(null); table.getSelectionModel().select(selected); }
            filter(); message(success == null ? rows.size() + " accounts loaded." : success, false);
        }, error -> { rows.clear(); message((success == null ? error.getMessage() : success + " Accounts could not be refreshed.") + " Refresh accounts to retry. Your form entries are retained.", true); },
                () -> { busy = false; update(); });
    }
    private void create() {
        if (busy) return; String n = name.getText(), mail = email.getText(); UserRole role = createRole.getValue();
        write(() -> service.registerUser(n,mail,role), user -> {
            preferredId = user.getUserId(); name.clear(); email.clear(); search.clear(); state.setValue("ALL");
            page = "list"; showPage();
        }, "Account created. Open the selected account to issue its temporary credential.", false);
    }
    private boolean confirm(String action) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Existing sessions may be invalidated. History is retained.", ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(action + " for account #" + selected.userId() + " · " + selected.name() + "?");
        if (getScene() != null && getScene().getWindow() != null) alert.initOwner(getScene().getWindow());
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
    private void credential(boolean reactivation) {
        if (!canAct() || !confirm(reactivation ? "Reactivate and issue credential" : "Issue activation / reset credential")) return;
        int id = selected.userId();
        write(() -> reactivation ? service.reactivateAccount(id) : service.issueCredential(id), this::deliver,
                "Credential issued once for account #" + id + ". It cannot be displayed again.", true);
    }
    private void action(String title, Callable<Boolean> call, String success) {
        if (!canAct() || !confirm(title)) return; write(call, ok -> {}, success, true);
    }
    private <T> void write(Callable<T> call, Consumer<T> success, String message, boolean accountAction) {
        if (busy) return; busy = true; update(); message("Saving…",false);
        boolean[] saved = {false};
        FxTasks.run(call, result -> { saved[0] = true; success.accept(result); }, error -> {
            if (accountAction) fresh = false;
            message(error.getMessage() + (accountAction ? " Refresh accounts before retrying. Your choices are retained." : " Your entries are retained. Correct them or retry."), true);
        }, () -> { busy = false; update(); if (saved[0]) load(message); });
    }
    private void deliver(IssuedCredential credential) {
        TextField secret = new TextField(credential.secret()); secret.setEditable(false); secret.setPrefColumnCount(35);
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle("Temporary credential — shown once");
        dialog.getDialogPane().setContent(new VBox(12, text("Account #" + credential.userId() + " · expires " + credential.expiresAt(), ""), secret,
                text("Deliver privately. First login requires a new password. Closing this dialog removes the displayed credential.", "muted")));
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/com/aceos/ui/foundation.css").toExternalForm());
        if (getScene() != null && getScene().getWindow() != null) dialog.initOwner(getScene().getWindow());
        secret.setAccessibleText("One-time temporary credential"); dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        try { dialog.showAndWait(); } finally { secret.clear(); dialog.getDialogPane().setContent(null); }
    }
}
