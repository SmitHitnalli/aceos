package com.aceos.ui.view;

import com.aceos.model.TechnicianWorkload;
import com.aceos.model.User;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import java.util.List;

/** Shared assignment choice and readable workload breakdown; never selects a recommendation. */
final class TechnicianSelector extends VBox {
    private final SearchSelector<TechnicianWorkload> choices;
    private final Label summary = new Label();
    private int currentId;

    TechnicianSelector(String id) {
        super(8);
        setMinWidth(0);
        choices = new SearchSelector<>("Active technician", id, this::display, w -> w.technician().getUserId(),
                w -> display(w) + " " + w.technician().getEmail());
        choices.wrapChoices(this::display);
        summary.setId(id + "-workload"); summary.setWrapText(true);
        summary.setMinHeight(Region.USE_PREF_SIZE);
        choices.onSelectionChanged(this::updateSummary);
        var hint = new Label("Counts from the last refresh. They do not reserve capacity. Overdue inspections are included in scheduled inspections.");
        hint.setWrapText(true); hint.setMinHeight(Region.USE_PREF_SIZE); hint.getStyleClass().add("muted");
        getChildren().addAll(choices, summary, hint);
        updateSummary();
    }

    private String display(TechnicianWorkload w) {
        return w.technician().getName() + " · #" + w.technician().getUserId()
                + (w.technician().getUserId() == currentId ? " · current" : "")
                + "\n" + w.awaitingStart() + " waiting · " + w.inProgress() + " started"
                + " · " + w.returnedForCorrection() + " corrections"
                + " · inspections " + w.scheduledInspections();
    }

    private void updateSummary() {
        var w = choices.selected();
        summary.setText(w == null ? "Choose a technician to see their workload."
                : w.technician().getName() + " · #" + w.technician().getUserId()
                + (w.technician().getUserId() == currentId ? " · Current assignee" : "")
                + "\nRepairs awaiting start: " + w.awaitingStart()
                + "\nRepairs in progress: " + w.inProgress()
                + "\nAwaiting verification: " + w.awaitingVerification()
                + "\nReturned for correction: " + w.returnedForCorrection()
                + "\nScheduled inspections: " + w.scheduledInspections()
                + "\nOverdue inspections: " + w.overdueInspections());
    }

    void setItems(List<TechnicianWorkload> items, int currentId) {
        this.currentId = currentId;
        boolean removed = choices.selected() != null && items.stream().noneMatch(w ->
                w.technician().getUserId() == choices.selected().technician().getUserId());
        choices.setItems(items); updateSummary();
        if (removed) summary.setText("Your selected technician is no longer eligible. Choose an active technician.");
    }

    User selected() { var w = choices.selected(); return w == null ? null : w.technician(); }
    void clear() { choices.clear(); updateSummary(); }
    boolean isEmpty() { return choices.isEmpty(); }
}
