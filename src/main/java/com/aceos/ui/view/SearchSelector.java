package com.aceos.ui.view;

import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** Search narrows authorized choices; selection is an object, never a typed ID. */
final class SearchSelector<T> extends VBox {
    private final javafx.collections.ObservableList<T> source = FXCollections.observableArrayList();
    private final ComboBox<T> choices = new ComboBox<>();
    private final TextField search = new TextField();
    private final Function<T, Integer> identity;

    SearchSelector(String name, String id, Function<T, String> display, Function<T, Integer> identity) {
        super(8);
        this.identity = identity;
        setMinWidth(0);
        search.setId(id + "-search"); search.setPromptText("Search " + name.toLowerCase());
        search.setAccessibleText("Search " + name.toLowerCase());
        choices.setId(id + "-choice"); choices.setPromptText("Choose " + name.toLowerCase());
        choices.setAccessibleText("Choose " + name.toLowerCase());
        choices.setMaxWidth(Double.MAX_VALUE); choices.setMinWidth(0);
        choices.setConverter(new StringConverter<>() {
            public String toString(T value) { return value == null ? "" : display.apply(value); }
            public T fromString(String ignored) { throw new UnsupportedOperationException(); }
        });
        var filtered = new FilteredList<>(source);
        choices.setItems(filtered);
        search.textProperty().addListener((o, a, b) -> filtered.setPredicate(item ->
                display.apply(item).toLowerCase(Locale.ROOT).contains(b.trim().toLowerCase(Locale.ROOT))));
        Label label = new Label(name); label.setLabelFor(choices);
        getChildren().addAll(label, search, choices);
    }

    void setItems(List<T> items) {
        T previous = choices.getValue(); source.setAll(items);
        if (previous != null) choices.setValue(items.stream()
                .filter(item -> identity.apply(item).equals(identity.apply(previous))).findFirst().orElse(null));
    }
    T selected() { return choices.getValue(); }
    void clear() { search.clear(); choices.setValue(null); }
    boolean isEmpty() { return source.isEmpty(); }
}
