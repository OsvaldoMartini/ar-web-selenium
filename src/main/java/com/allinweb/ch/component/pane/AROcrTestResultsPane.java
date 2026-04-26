package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.model.OcrConfigParam;
import com.allinweb.ch.vision.OcrTestResultRow;
import com.google.gson.Gson;
import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Phase 4c+ test review grid. SplitPane: results table on the left, annotated screenshot on the right.
 * The Approved column is a per-row checkbox bound to {@link OcrTestResultRow#approvedProperty()};
 * approvals are kept in memory for the duration of the modal — persistence is a follow-up.
 */
@Slf4j
public class AROcrTestResultsPane extends ARPane {

    private static final Gson GSON = new Gson();

    private static volatile AROcrTestResultsPane instance;

    /** Reference to the {@code output.approved_xpaths} param of the editor's currently-loaded profile;
     * when a checkbox toggles we rewrite this param's JSON value so the user's QA persists across
     * Save / Save As New. Null when the editor has no profile loaded yet. */
    private OcrConfigParam approvalsParam;

    private VBox root;
    private Label headerLabel;
    private Label approvedCounterLabel;
    private TableView<OcrTestResultRow> resultsTable;
    private ImageView annotatedImage;
    private Label imageHint;
    private TextField xPathField;
    private Button markAllButton;
    private Button clearAllButton;
    private Button closeButton;

    private AROcrTestResultsPane() {
        super();
    }

    public static AROcrTestResultsPane getInstance() {
        if (instance == null) {
            synchronized (AROcrTestResultsPane.class) {
                if (instance == null) instance = new AROcrTestResultsPane();
            }
        }
        return instance;
    }

    /**
     * Populate the grid + image. Safe to call from any thread. {@code approvedXPathsParam}
     * is the editor's {@code output.approved_xpaths} param (nullable); when non-null,
     * checkbox toggles rewrite its {@code value} so saving the profile persists approvals.
     */
    public void populate(
            String headerSummary,
            List<OcrTestResultRow> rows,
            Path annotatedImagePath,
            OcrConfigParam approvedXPathsParam) {
        Platform.runLater(() -> {
            this.approvalsParam = approvedXPathsParam;
            headerLabel.setText(headerSummary == null ? "" : headerSummary);

            // 1. Pre-populate Approved state from persisted JSON before binding listeners
            //    (so the initial setApproved doesn't trigger a no-op write back).
            Set<String> approvedSet = parseApprovedSet(approvedXPathsParam);
            if (rows != null) {
                for (OcrTestResultRow r : rows) {
                    r.setApproved(approvedSet.contains(r.getxPath()));
                }
                // 2. Bind toggle → param.value JSON.
                for (OcrTestResultRow r : rows) {
                    r.approvedProperty().addListener((obs, ov, nv) -> persistToggle(r.getxPath(), nv));
                }
            }

            resultsTable.setItems(
                    rows == null ? FXCollections.observableArrayList() : FXCollections.observableArrayList(rows));
            updateApprovedCount();
            loadImage(annotatedImagePath);
            // Reset xPath field; auto-select first row so the user immediately sees something there.
            if (xPathField != null) xPathField.clear();
            if (rows != null && !rows.isEmpty()) {
                resultsTable.getSelectionModel().select(0);
            }
        });
    }

    private void persistToggle(String xpath, boolean approved) {
        if (approvalsParam == null || xpath == null || xpath.isBlank()) return;
        Set<String> set = parseApprovedSet(approvalsParam);
        if (approved) set.add(xpath);
        else set.remove(xpath);
        approvalsParam.setValue(GSON.toJson(set.toArray(new String[0])));
    }

    private static Set<String> parseApprovedSet(OcrConfigParam p) {
        Set<String> out = new HashSet<>();
        if (p == null || p.getValue() == null || p.getValue().isBlank()) return out;
        try {
            String[] arr = GSON.fromJson(p.getValue(), String[].class);
            if (arr != null) Collections.addAll(out, arr);
        } catch (Exception ex) {
            log.debug("approved_xpaths JSON parse failed: {}", ex.getMessage());
        }
        return out;
    }

    @Override
    public void initUIComponents() {
        headerLabel = new Label();
        headerLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1565c0;");
        headerLabel.setWrapText(true);

        resultsTable = new TableView<>();
        resultsTable.setEditable(true);
        resultsTable.setPlaceholder(
                new Label("No test results yet — run Test On Current Page from the OCR Config modal."));

        TableColumn<OcrTestResultRow, Boolean> approvedCol = new TableColumn<>("✓");
        approvedCol.setCellValueFactory(cd -> cd.getValue().approvedProperty());
        approvedCol.setCellFactory(CheckBoxTableCell.forTableColumn(approvedCol));
        approvedCol.setEditable(true);
        approvedCol.setMinWidth(40);
        approvedCol.setMaxWidth(50);

        TableColumn<OcrTestResultRow, String> definedNameCol = new TableColumn<>("definedName");
        definedNameCol.setCellValueFactory(
                cd -> new SimpleStringProperty(cd.getValue().getDefinedName()));
        definedNameCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-weight: bold; -fx-text-fill: #1565c0;");
                }
            }
        });
        definedNameCol.setMinWidth(160);

        TableColumn<OcrTestResultRow, String> qualityCol = new TableColumn<>("Quality");
        qualityCol.setCellValueFactory(
                cd -> new SimpleStringProperty(cd.getValue().getMatchQuality()));
        qualityCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-background-color: " + colorFor(item)
                            + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-alignment: CENTER;");
                }
            }
        });
        qualityCol.setMinWidth(100);
        qualityCol.setMaxWidth(140);

        TableColumn<OcrTestResultRow, String> tagCol = new TableColumn<>("Tag");
        tagCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getTag()));
        tagCol.setMinWidth(70);
        tagCol.setMaxWidth(120);

        TableColumn<OcrTestResultRow, String> domCol = new TableColumn<>("DOM Text");
        domCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getDomText()));
        domCol.setMinWidth(160);

        TableColumn<OcrTestResultRow, String> ocrCol = new TableColumn<>("OCR Text");
        ocrCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getOcrText()));
        ocrCol.setMinWidth(160);

        TableColumn<OcrTestResultRow, String> xpCol = new TableColumn<>("xPath (tail)");
        xpCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getxPathShort()));
        xpCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (!empty && getTableRow() != null && getTableRow().getItem() != null) {
                    setTooltip(new Tooltip(((OcrTestResultRow) getTableRow().getItem()).getxPath()));
                } else {
                    setTooltip(null);
                }
            }
        });
        xpCol.setMinWidth(200);

        resultsTable.getColumns().addAll(approvedCol, definedNameCol, qualityCol, tagCol, domCol, ocrCol, xpCol);
        resultsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Tint approved rows green so the "good to go" set stands out at a glance.
        resultsTable.setRowFactory(tv -> {
            TableRow<OcrTestResultRow> row = new TableRow<>();
            row.itemProperty().addListener((obs, old, item) -> bindRowStyle(row, item));
            return row;
        });

        annotatedImage = new ImageView();
        annotatedImage.setPreserveRatio(true);
        annotatedImage.setSmooth(true);
        annotatedImage.setFitWidth(560);

        imageHint =
                new Label("Annotated screenshot — green = OCR words · red = DOM rects · thick green = EXACT_CONTAIN.");
        imageHint.setWrapText(true);
        imageHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");

        ScrollPane imageScroll = new ScrollPane(new VBox(8, annotatedImage, imageHint));
        imageScroll.setFitToWidth(true);
        imageScroll.setPrefViewportWidth(580);

        SplitPane split = new SplitPane();
        split.getItems()
                .addAll(
                        new ScrollPane(resultsTable) {
                            {
                                setFitToWidth(true);
                                setFitToHeight(true);
                            }
                        },
                        imageScroll);
        split.setDividerPositions(0.55);
        VBox.setVgrow(split, Priority.ALWAYS);

        xPathField = new TextField();
        xPathField.setEditable(false);
        xPathField.setPromptText("Click any row above to see its full xPath here (selectable / copyable).");
        xPathField.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;");
        Label xpLabel = new Label("Full xPath:");
        xpLabel.setStyle("-fx-font-weight: bold;");
        HBox xpRow = new HBox(8, xpLabel, xPathField);
        xpRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(xPathField, Priority.ALWAYS);

        approvedCounterLabel = new Label("Approved: 0 / 0");
        approvedCounterLabel.setStyle("-fx-font-weight: bold;");

        markAllButton = new Button("Mark All Approved");
        clearAllButton = new Button("Clear All Approvals");
        closeButton = new Button("Close");

        HBox bottom = new HBox(8, approvedCounterLabel, markAllButton, clearAllButton, closeButton);
        bottom.setAlignment(Pos.CENTER_LEFT);

        root = new VBox(10, headerLabel, split, xpRow, bottom);
        root.setPadding(new Insets(14));
    }

    @Override
    public void initUIBehaviour() {
        resultsTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null && xPathField != null) {
                xPathField.setText(sel.getxPath());
                xPathField.positionCaret(0);
            }
        });
        markAllButton.setOnAction(e -> {
            for (OcrTestResultRow r : resultsTable.getItems()) r.setApproved(true);
            updateApprovedCount();
        });
        clearAllButton.setOnAction(e -> {
            for (OcrTestResultRow r : resultsTable.getItems()) r.setApproved(false);
            updateApprovedCount();
        });
        closeButton.setOnAction(e -> {
            if (root.getScene() != null && root.getScene().getWindow() != null) {
                root.getScene().getWindow().hide();
            }
        });
    }

    @Override
    public Pane getPaneReference() {
        return root;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void bindRowStyle(TableRow<OcrTestResultRow> row, OcrTestResultRow item) {
        if (item == null) {
            row.setStyle("");
            return;
        }
        Runnable apply = () -> {
            if (item.isApproved()) {
                row.setStyle("-fx-background-color: #c8e6c9;"); // light green
            } else {
                row.setStyle("");
            }
            updateApprovedCount();
        };
        item.approvedProperty().addListener((o, ov, nv) -> apply.run());
        apply.run();
    }

    private void updateApprovedCount() {
        if (resultsTable == null) return;
        int total = resultsTable.getItems().size();
        int approved = 0;
        for (OcrTestResultRow r : resultsTable.getItems()) {
            if (r.isApproved()) approved++;
        }
        approvedCounterLabel.setText("Approved: " + approved + " / " + total);
    }

    private void loadImage(Path imagePath) {
        if (imagePath == null) {
            annotatedImage.setImage(null);
            return;
        }
        File f = imagePath.toFile();
        if (!f.exists()) {
            annotatedImage.setImage(null);
            imageHint.setText(
                    "Annotated image not generated — enable output.save_annotated_png in the editor and re-run Test.");
            return;
        }
        try {
            annotatedImage.setImage(new Image(f.toURI().toString()));
            imageHint.setText(
                    "Annotated screenshot — green = OCR words · red = DOM rects · thick green = EXACT_CONTAIN.");
        } catch (Exception ex) {
            log.warn("Could not load annotated image {}: {}", imagePath, ex.getMessage());
        }
    }

    private static String colorFor(String quality) {
        switch (quality == null ? "" : quality) {
            case "EXACT_CONTAIN":
                return "#2e7d32"; // dark green
            case "OVERLAP":
                return "#689f38"; // olive-green
            case "PROXIMITY":
                return "#f57c00"; // orange
            case "NONE":
            default:
                return "#9e9e9e"; // gray
        }
    }
}
