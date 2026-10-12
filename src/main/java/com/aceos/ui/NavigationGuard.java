package com.aceos.ui;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import java.util.function.BooleanSupplier;

/** Views own their form state; the shell checks it before replacing a screen. */
public final class NavigationGuard {
    private static final Object KEY = new Object();
    private record State(BooleanSupplier saving, BooleanSupplier dirty) {}
    private NavigationGuard() {}

    public static void install(Node view, BooleanSupplier saving, BooleanSupplier dirty) {
        view.getProperties().put(KEY, new State(saving, dirty));
    }

    private static boolean matches(Node node, boolean saving) {
        if (node == null) return false;
        var state = (State) node.getProperties().get(KEY);
        if (state != null && (saving ? state.saving() : state.dirty()).getAsBoolean()) return true;
        if (node instanceof ScrollPane scroll && matches(scroll.getContent(), saving)) return true;
        return node instanceof Parent parent && parent.getChildrenUnmodifiable().stream().anyMatch(n -> matches(n, saving));
    }

    public static boolean allowLeave(Node view) {
        boolean saving = matches(view, true);
        if (!saving && !matches(view, false)) return true;
        ButtonType stay = new ButtonType("Stay", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType discard = new ButtonType("Discard and leave", ButtonBar.ButtonData.OK_DONE);
        Alert dialog = new Alert(saving ? Alert.AlertType.INFORMATION : Alert.AlertType.CONFIRMATION);
        dialog.setTitle("AceOS");
        dialog.setHeaderText(saving ? "Save in progress" : "Leave unfinished form?");
        dialog.setContentText(saving ? "Wait for the save to finish so you can confirm its result." : "Your unsaved entries will be discarded if you leave this screen.");
        dialog.getButtonTypes().setAll(saving ? new ButtonType[]{stay} : new ButtonType[]{stay, discard});
        dialog.getDialogPane().setId("navigation-guard");
        // Enter must never discard by default. Escape and window close mean Stay.
        if (!saving) ((javafx.scene.control.Button) dialog.getDialogPane().lookupButton(discard)).setDefaultButton(false);
        ((javafx.scene.control.Button) dialog.getDialogPane().lookupButton(stay)).setDefaultButton(true);
        if (view != null && view.getScene() != null && view.getScene().getWindow() != null) dialog.initOwner(view.getScene().getWindow());
        return dialog.showAndWait().orElse(stay) == discard && !saving;
    }
}
