package com.aceos.ui.view;

import com.aceos.model.User;
import com.aceos.service.AceOsService;

import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Locale;

public class UserDirectoryView extends VBox {

    public UserDirectoryView(AceOsService service) {
        setSpacing(18);
        setPadding(new Insets(40));

        Label title = new Label("User Directory");
        title.setStyle(
                "-fx-font-size: 28px;" + "-fx-font-weight: bold;" + "-fx-text-fill: #1e293b;");

        Label description =
                new Label(
                        "View user IDs and roles for "
                                + "complaints, assignments, and verification.");
        description.setWrapText(true);
        description.setStyle("-fx-font-size: 15px;" + "-fx-text-fill: #475569;");

        TextField searchField = new TextField();
        searchField.setPromptText("Search ID, name, email, or role");

        HBox filters = new HBox(10, new Label("Search"), searchField);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        ObservableList<User> sourceItems = FXCollections.observableArrayList();
        FilteredList<User> filteredItems = new FilteredList<>(sourceItems, user -> true);

        TableView<User> table = new TableView<>();
        table.setItems(filteredItems);
        table.setPlaceholder(new Label("No users found"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<User, Number> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(
                data -> new ReadOnlyIntegerWrapper(data.getValue().getUserId()));

        TableColumn<User, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().getName()));

        TableColumn<User, String> emailColumn = new TableColumn<>("Email");
        emailColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().getEmail()));

        TableColumn<User, String> roleColumn = new TableColumn<>("Role");
        roleColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().getRole().name()));

        table.getColumns().add(idColumn);
        table.getColumns().add(nameColumn);
        table.getColumns().add(emailColumn);
        table.getColumns().add(roleColumn);
        table.setPrefHeight(420);

        Button refreshButton = new Button("Refresh Users");
        refreshButton.setMinHeight(40);

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);

        searchField
                .textProperty()
                .addListener(
                        (observable, oldValue, newValue) -> {
                            String query =
                                    newValue == null
                                            ? ""
                                            : newValue.trim().toLowerCase(Locale.ROOT);

                            filteredItems.setPredicate(
                                    user ->
                                            query.isEmpty()
                                                    || String.valueOf(user.getUserId())
                                                            .contains(query)
                                                    || user.getName()
                                                            .toLowerCase(Locale.ROOT)
                                                            .contains(query)
                                                    || user.getEmail()
                                                            .toLowerCase(Locale.ROOT)
                                                            .contains(query)
                                                    || user.getRole()
                                                            .name()
                                                            .toLowerCase(Locale.ROOT)
                                                            .contains(query));
                        });

        refreshButton.setOnAction(
                event -> loadUsers(service, sourceItems, refreshButton, statusLabel));

        getChildren().addAll(title, description, filters, table, refreshButton, statusLabel);

        loadUsers(service, sourceItems, refreshButton, statusLabel);
    }

    private void loadUsers(
            AceOsService service,
            ObservableList<User> sourceItems,
            Button refreshButton,
            Label statusLabel) {
        refreshButton.setDisable(true);

        com.aceos.ui.FxTasks.run(
                () -> service.getAllUsers(),
                loaded -> {
                    sourceItems.setAll(loaded);

                    statusLabel.setStyle("-fx-text-fill: #15803d;");
                    statusLabel.setText(sourceItems.size() + " users loaded");
                },
                exception -> {
                    statusLabel.setStyle("-fx-text-fill: #b91c1c;");
                    statusLabel.setText(exception.getMessage());
                },
                () -> {
                    refreshButton.setDisable(false);
                });
    }
}
