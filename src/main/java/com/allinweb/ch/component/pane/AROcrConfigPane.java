package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.AROcrTestResultsScene;
import com.allinweb.ch.facade.LocatorCleanupService;
import com.allinweb.ch.facade.OcrConfigRepository;
import com.allinweb.ch.facade.OcrConfigService;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.model.HomeUrlDTO;
import com.allinweb.ch.model.OcrConfig;
import com.allinweb.ch.model.OcrConfigDefaults;
import com.allinweb.ch.model.OcrConfigMeta;
import com.allinweb.ch.model.OcrConfigParam;
import com.allinweb.ch.model.OcrConfigProfile;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.PageDiagnosticDumper;
import com.allinweb.ch.vision.AnnotatedImageRenderer;
import com.allinweb.ch.vision.OcrCorrelationResult;
import com.allinweb.ch.vision.OcrDomCorrelator;
import com.allinweb.ch.vision.OcrTestResultRow;
import com.allinweb.ch.vision.WebPageOcrService;
import com.allinweb.ch.vision.ocr.OcrResult;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;

/**
 * Phase 4b polished editor: per-param type-aware controls (CheckBox / Spinner / ComboBox /
 * TextField / TextArea), help tooltips sourced from {@link OcrConfigMeta#DESCRIPTIONS},
 * and a colour-mapping "Add template op" helper. Phase 4c "Test On Current Page" runs
 * the OCR pipeline offline against cached artifacts.
 */
@Slf4j
public class AROcrConfigPane extends ARPane {

    private static final String COLOR_MAPPING_CAT = "color_mapping";
    private static final String COLOR_MAPPING_OPS = "ops";
    private static final String COLOR_OP_TEMPLATE = "{\"name\":\"new-op\",\"mode\":\"replace_in_hsv_range\","
            + "\"hsv_lower\":[0,80,80],\"hsv_upper\":[10,255,255],\"replacement_bgr\":[0,0,0]}";

    private static volatile AROcrConfigPane instance;

    private static final OcrConfigRepository repo = OcrConfigRepository.getInstance();
    private static final PerformLists performLists = PerformLists.getInstance();
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Gson GSON = new Gson();
    private static final org.slf4j.Logger logScanner = org.slf4j.LoggerFactory.getLogger("com.allinweb.scanner");

    private VBox root;
    private TableView<OcrConfigProfile> profilesTable;
    private TextField nameField;
    private TextField descriptionField;
    private Label scopeLabel;
    private TabPane paramsTabs;
    /** One VBox per category, rebuilt on every profile load. Preserves insertion order from OcrConfigDefaults.CATEGORIES_IN_ORDER. */
    private final Map<String, VBox> categoryBoxes = new LinkedHashMap<>();
    /** Mutable live params; controls rewrite their OcrConfigParam.value on change. */
    private List<OcrConfigParam> currentParams = new ArrayList<>();

    private Button saveButton;
    private Button saveAsNewButton;
    private Button testButton;
    private Button cleanOrphansButton;
    private Button deleteButton;
    private Button closeButton;
    private Label statusLabel;

    private Integer currentHomebankingId;
    private Integer currentHomeUrlId;
    private String currentOrgName;
    private OcrConfigProfile currentProfile;

    private AROcrConfigPane() {
        super();
    }

    public static AROcrConfigPane getInstance() {
        if (instance == null) {
            synchronized (AROcrConfigPane.class) {
                if (instance == null) instance = new AROcrConfigPane();
            }
        }
        return instance;
    }

    public void bindScope(Integer homebankingId, Integer homeUrlId) {
        this.currentHomebankingId = homebankingId;
        this.currentHomeUrlId = homeUrlId;
        this.currentOrgName = resolveOrgName(homebankingId, homeUrlId);
        Platform.runLater(this::refreshForScope);
    }

    @Override
    public void initUIComponents() {
        profilesTable = new TableView<>();
        profilesTable.setPrefHeight(240);
        profilesTable.setPlaceholder(new Label("No profiles saved yet — edit params below and click Save As New."));

        TableColumn<OcrConfigProfile, String> pName = new TableColumn<>("Profile");
        pName.setCellValueFactory(c -> new SimpleStringProperty(profileLabel(c.getValue())));
        pName.setMinWidth(260);

        TableColumn<OcrConfigProfile, String> pScope = new TableColumn<>("Scope");
        pScope.setCellValueFactory(c -> new SimpleStringProperty(scopeOf(c.getValue())));
        pScope.setMinWidth(130);

        TableColumn<OcrConfigProfile, String> pCreated = new TableColumn<>("Created");
        pCreated.setCellValueFactory(
                c -> new SimpleStringProperty(formatTs(c.getValue().getCreatedAt())));
        pCreated.setMinWidth(130);

        TableColumn<OcrConfigProfile, String> pModified = new TableColumn<>("Modified");
        pModified.setCellValueFactory(
                c -> new SimpleStringProperty(formatTs(c.getValue().getUpdatedAt())));
        pModified.setMinWidth(130);

        profilesTable.getColumns().addAll(pName, pScope, pCreated, pModified);
        profilesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        nameField = new TextField();
        nameField.setPromptText("Profile name (organization + page label)");

        descriptionField = new TextField();
        descriptionField.setPromptText("Optional description");

        scopeLabel = new Label();
        scopeLabel.setWrapText(true);
        scopeLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #555;");

        paramsTabs = new TabPane();
        paramsTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        for (String cat : OcrConfigDefaults.CATEGORIES_IN_ORDER) {
            VBox box = new VBox(6);
            box.setPadding(new Insets(10));
            ScrollPane sp = new ScrollPane(box);
            sp.setFitToWidth(true);
            categoryBoxes.put(cat, box);
            paramsTabs.getTabs().add(new Tab(cat, sp));
        }

        saveButton = new Button("Save");
        saveAsNewButton = new Button("Save As New");
        testButton = new Button("Test On Current Page");
        cleanOrphansButton = new Button("Clean Orphan Locators");
        cleanOrphansButton.setTooltip(new Tooltip(
                "Scan the element_locator table for this scope and remove rows that are clearly orphans"
                        + " (short slugs, <label> tags, CSRF-like random tokens, fuzzy duplicates with lower pick_count)."));
        deleteButton = new Button("Delete");
        closeButton = new Button("Close");
        statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #0a5; -fx-font-weight: bold;");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        HBox buttons =
                new HBox(8, saveButton, saveAsNewButton, testButton, cleanOrphansButton, deleteButton, closeButton, statusLabel);
        buttons.setAlignment(Pos.CENTER_LEFT);

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(6);
        form.add(new Label("Name:"), 0, 0);
        form.add(nameField, 1, 0);
        form.add(new Label("Description:"), 0, 1);
        form.add(descriptionField, 1, 1);
        form.add(new Label("Scope:"), 0, 2);
        form.add(scopeLabel, 1, 2);
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setMinWidth(90);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(c0, c1);

        root = new VBox(
                10,
                new Label("OCR Configuration"),
                new Label("Profiles (click a row to load):"),
                profilesTable,
                form,
                new Label("Parameters (hover a name for help):"),
                paramsTabs,
                buttons);
        root.setPadding(new Insets(14));
        VBox.setVgrow(paramsTabs, Priority.ALWAYS);
    }

    @Override
    public void initUIBehaviour() {
        profilesTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) loadIntoForm(selected);
        });
        saveButton.setOnAction(e -> onSave(false));
        saveAsNewButton.setOnAction(e -> onSave(true));
        testButton.setOnAction(e -> onTest());
        cleanOrphansButton.setOnAction(e -> onCleanOrphans());
        deleteButton.setOnAction(e -> onDelete());
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

    // ── Load / render ────────────────────────────────────────────────────

    private void refreshForScope() {
        scopeLabel.setText(formatScope());
        List<OcrConfigProfile> profiles = repo.listProfiles();
        profilesTable.setItems(FXCollections.observableArrayList(profiles));

        OcrConfigProfile active = repo.resolveActive(currentHomebankingId, currentHomeUrlId);
        if (active != null) {
            for (OcrConfigProfile p : profiles) {
                if (p.getId() != null && p.getId().equals(active.getId())) {
                    profilesTable.getSelectionModel().select(p);
                    break;
                }
            }
            loadIntoForm(active);
        } else {
            currentProfile = null;
            nameField.setText(proposeName());
            descriptionField.setText("");
            // Seed editor with canonical defaults so the user has something sensible to Save As New.
            List<OcrConfigParam> seeded = new ArrayList<>();
            for (OcrConfigParam canon : OcrConfigDefaults.CANONICAL) {
                seeded.add(new OcrConfigParam(
                        null, null, canon.getCategory(), canon.getName(), canon.getValueType(), canon.getValue()));
            }
            currentParams = seeded;
            rebuildAllCategoryBoxes();
            updateButtonStates();
            statusLabel.setText("");
        }
    }

    private void loadIntoForm(OcrConfigProfile p) {
        currentProfile = p;
        nameField.setText(p.getName());
        descriptionField.setText(p.getDescription() == null ? "" : p.getDescription());
        List<OcrConfigParam> params = new ArrayList<>(repo.listParamsForProfile(p.getId()));

        // Auto-add canonical params missing from this profile — so code additions since
        // the profile was saved show up as editable rows and can be captured via Save As New.
        Set<String> have = new HashSet<>();
        for (OcrConfigParam q : params) have.add(q.getCategory() + "." + q.getName());
        List<String> missingKeys = new ArrayList<>();
        int added = 0;
        for (OcrConfigParam canon : OcrConfigDefaults.CANONICAL) {
            String key = canon.getCategory() + "." + canon.getName();
            if (have.contains(key)) continue;
            params.add(new OcrConfigParam(
                    null, p.getId(), canon.getCategory(), canon.getName(), canon.getValueType(), canon.getValue()));
            missingKeys.add(key);
            added++;
        }

        currentParams = params;
        rebuildAllCategoryBoxes();
        updateButtonStates();

        if (added > 0) {
            statusInfo("NEW PARAMS DETECTED (" + added + "): " + String.join(", ", missingKeys)
                    + " — click Save As New to create a new version with them.");
        } else {
            statusLabel.setText("");
        }
    }

    private void rebuildAllCategoryBoxes() {
        for (String cat : categoryBoxes.keySet()) rebuildCategoryBox(cat);
    }

    private void rebuildCategoryBox(String category) {
        VBox box = categoryBoxes.get(category);
        if (box == null) return;
        box.getChildren().clear();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        // col 0 = name, col 1 = type, col 2 = control, col 3 = hint icon
        ColumnConstraints nameCol = new ColumnConstraints();
        nameCol.setMinWidth(190);
        nameCol.setPrefWidth(210);
        ColumnConstraints typeCol = new ColumnConstraints();
        typeCol.setMinWidth(50);
        typeCol.setPrefWidth(60);
        ColumnConstraints ctrlCol = new ColumnConstraints();
        ctrlCol.setHgrow(Priority.ALWAYS);
        ColumnConstraints infoCol = new ColumnConstraints();
        infoCol.setMinWidth(20);
        grid.getColumnConstraints().addAll(nameCol, typeCol, ctrlCol, infoCol);

        int row = 0;
        for (OcrConfigParam p : currentParams) {
            if (!category.equals(p.getCategory())) continue;
            String key = p.getCategory() + "." + p.getName();
            String desc = OcrConfigMeta.DESCRIPTIONS.get(key);

            Label name = new Label(p.getName());
            name.setStyle("-fx-font-weight: bold;");
            if (desc != null) name.setTooltip(new Tooltip(desc));

            Label type = new Label(p.getValueType());
            type.setStyle("-fx-font-size: 10px; -fx-text-fill: #888;");

            Node control = buildControl(p);

            Label info = new Label(desc == null ? "" : "ⓘ");
            info.setStyle("-fx-text-fill: #1565c0; -fx-cursor: hand;");
            if (desc != null) info.setTooltip(new Tooltip(desc));

            grid.add(name, 0, row);
            grid.add(type, 1, row);
            grid.add(control, 2, row);
            grid.add(info, 3, row);
            row++;
        }

        box.getChildren().add(grid);

        if (COLOR_MAPPING_CAT.equals(category)) {
            Button addTemplate = new Button("+ Add template op");
            addTemplate.setTooltip(
                    new Tooltip(
                            "Appends a replace_in_hsv_range template to color_mapping.ops — tune its hsv_lower/hsv_upper/replacement_bgr."));
            addTemplate.setOnAction(e -> appendColorOpTemplate());
            VBox.setMargin(addTemplate, new Insets(8, 0, 0, 0));
            box.getChildren().add(addTemplate);
        }
    }

    private Node buildControl(OcrConfigParam p) {
        String type = p.getValueType() == null ? "string" : p.getValueType();
        String key = p.getCategory() + "." + p.getName();

        List<String> enums = OcrConfigMeta.ENUMS.get(key);
        if (enums != null) {
            ComboBox<String> cb = new ComboBox<>(FXCollections.observableArrayList(enums));
            cb.setEditable(true);
            cb.setValue(p.getValue());
            cb.setMaxWidth(Double.MAX_VALUE);
            cb.valueProperty().addListener((obs, ov, nv) -> p.setValue(nv == null ? "" : nv));
            cb.getEditor().textProperty().addListener((obs, ov, nv) -> p.setValue(nv));
            return cb;
        }

        switch (type) {
            case "bool": {
                CheckBox c = new CheckBox();
                c.setSelected(
                        "true".equalsIgnoreCase(String.valueOf(p.getValue()).trim()));
                c.selectedProperty().addListener((o, ov, nv) -> p.setValue(nv ? "true" : "false"));
                return c;
            }
            case "int": {
                OcrConfigMeta.Range r = OcrConfigMeta.RANGES.get(key);
                int min = r == null ? Integer.MIN_VALUE / 2 : (int) r.min;
                int max = r == null ? Integer.MAX_VALUE / 2 : (int) r.max;
                int step = r == null ? 1 : Math.max(1, (int) r.step);
                int initial = parseIntOr(p.getValue(), min);
                initial = Math.max(min, Math.min(max, initial));
                Spinner<Integer> s = new Spinner<>(min, max, initial, step);
                s.setEditable(true);
                s.setMaxWidth(Double.MAX_VALUE);
                s.valueProperty().addListener((o, ov, nv) -> {
                    if (nv != null) p.setValue(String.valueOf(nv));
                });
                s.getEditor().textProperty().addListener((o, ov, nv) -> {
                    try {
                        int v = Integer.parseInt(nv.trim());
                        p.setValue(String.valueOf(v));
                    } catch (NumberFormatException ignore) {
                        // partial input — skip
                    }
                });
                return s;
            }
            case "double": {
                OcrConfigMeta.Range r = OcrConfigMeta.RANGES.get(key);
                double min = r == null ? -1e9 : r.min;
                double max = r == null ? 1e9 : r.max;
                double step = r == null ? 0.1 : r.step;
                double initial = parseDoubleOr(p.getValue(), min);
                initial = Math.max(min, Math.min(max, initial));
                Spinner<Double> s = new Spinner<>(min, max, initial, step);
                s.setEditable(true);
                s.setMaxWidth(Double.MAX_VALUE);
                s.valueProperty().addListener((o, ov, nv) -> {
                    if (nv != null) p.setValue(String.valueOf(nv));
                });
                s.getEditor().textProperty().addListener((o, ov, nv) -> {
                    try {
                        double v = Double.parseDouble(nv.trim());
                        p.setValue(String.valueOf(v));
                    } catch (NumberFormatException ignore) {
                        // partial input
                    }
                });
                return s;
            }
            case "json": {
                TextArea ta = new TextArea(p.getValue() == null ? "" : p.getValue());
                ta.setWrapText(true);
                ta.setPrefRowCount(4);
                ta.textProperty().addListener((o, ov, nv) -> p.setValue(nv));
                return ta;
            }
            case "string":
            default: {
                TextField tf = new TextField(p.getValue() == null ? "" : p.getValue());
                tf.textProperty().addListener((o, ov, nv) -> p.setValue(nv));
                return tf;
            }
        }
    }

    private void appendColorOpTemplate() {
        OcrConfigParam target = null;
        for (OcrConfigParam p : currentParams) {
            if (COLOR_MAPPING_CAT.equals(p.getCategory()) && COLOR_MAPPING_OPS.equals(p.getName())) {
                target = p;
                break;
            }
        }
        if (target == null) return;

        String current = target.getValue() == null ? "" : target.getValue().trim();
        String next;
        if (current.isEmpty() || "[]".equals(current)) {
            next = "[\n  " + COLOR_OP_TEMPLATE + "\n]";
        } else if (current.endsWith("]")) {
            next = current.substring(0, current.length() - 1).trim() + ",\n  " + COLOR_OP_TEMPLATE + "\n]";
        } else {
            // Malformed JSON — just overwrite
            next = "[\n  " + COLOR_OP_TEMPLATE + "\n]";
        }
        target.setValue(next);
        rebuildCategoryBox(COLOR_MAPPING_CAT);
        statusInfo("Template op appended — tune hsv_lower / hsv_upper / replacement_bgr, then Save As New.");
    }

    // ── Save / test / delete ──────────────────────────────────────────────

    private void onSave(boolean asNew) {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isEmpty()) {
            status("Profile name is required.", false);
            return;
        }

        OcrConfigProfile clash = repo.findProfileByName(name);
        boolean isClash = clash != null
                && (asNew || currentProfile == null || !clash.getId().equals(currentProfile.getId()));

        if (isClash && asNew) {
            Matcher m = Pattern.compile("^(.*) v(\\d+)$").matcher(name);
            String base = m.matches() ? m.group(1) : name;
            name = base + " v" + nextVersionFor(base);
            nameField.setText(name);
        } else if (isClash) {
            status("Profile Name cannot be repeated.", false);
            return;
        }

        try {
            int profileId;
            if (asNew || currentProfile == null) {
                OcrConfigProfile p = new OcrConfigProfile();
                p.setName(name);
                p.setDescription(descriptionField.getText());
                p.setHomebankingId(currentHomebankingId);
                p.setHomeUrlId(currentHomeUrlId);
                p.setDefault(false);
                profileId = repo.insertProfile(p);

                List<OcrConfigParam> toWrite = currentParams;
                if (toWrite == null || toWrite.isEmpty()) {
                    OcrConfigProfile def = repo.findProfileByName("default");
                    if (def != null) toWrite = repo.listParamsForProfile(def.getId());
                }
                if (toWrite != null) {
                    for (OcrConfigParam src : toWrite) {
                        OcrConfigParam copy = new OcrConfigParam(
                                null, profileId, src.getCategory(), src.getName(), src.getValueType(), src.getValue());
                        repo.upsertParam(copy);
                    }
                }
            } else {
                currentProfile.setName(name);
                currentProfile.setDescription(descriptionField.getText());
                currentProfile.setHomebankingId(currentHomebankingId);
                currentProfile.setHomeUrlId(currentHomeUrlId);
                repo.updateProfile(currentProfile);
                profileId = currentProfile.getId();
                for (OcrConfigParam p : currentParams) {
                    p.setProfileId(profileId);
                    repo.upsertParam(p);
                }
            }
            repo.touchProfile(profileId);
            OcrConfigService.getInstance().invalidateAll();
            status("Saved. Cache invalidated — next pick/scan uses the new values.", true);
            refreshForScope();
        } catch (SQLException ex) {
            log.error("OCR config save failed", ex);
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            if (msg.toLowerCase().contains("unique")) {
                status("Profile Name cannot be repeated.", false);
            } else {
                status("Save failed: " + msg, false);
            }
        }
    }

    private void onDelete() {
        if (currentProfile == null) return;
        if (currentProfile.isDefault()) {
            status("Cannot delete the default profile.", false);
            return;
        }
        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Delete profile '" + currentProfile.getName() + "'?",
                ButtonType.YES,
                ButtonType.NO);
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                try {
                    repo.deleteProfile(currentProfile.getId());
                    OcrConfigService.getInstance().invalidateAll();
                    status("Deleted.", true);
                    refreshForScope();
                } catch (SQLException ex) {
                    status("Delete failed: " + ex.getMessage(), false);
                }
            }
        });
    }

    private void onCleanOrphans() {
        try {
            LocatorCleanupService svc = LocatorCleanupService.getInstance();
            LocatorCleanupService.ScanReport report = svc.scan(currentHomebankingId, currentHomeUrlId);
            if (report.candidates.isEmpty()) {
                statusInfo("No orphan locators found in this scope (" + report.totalRows + " rows scanned).");
                return;
            }
            StringBuilder body = new StringBuilder();
            body.append("Scanned ")
                    .append(report.totalRows)
                    .append(" locator row(s) in this scope.\n\nProposed deletions (")
                    .append(report.candidates.size())
                    .append("):\n\n");
            int shown = 0;
            for (LocatorCleanupService.OrphanCandidate c : report.candidates) {
                if (shown++ >= 30) {
                    body.append("\n… ").append(report.candidates.size() - 30).append(" more — full list in the log.");
                    break;
                }
                body.append("• [")
                        .append(c.reason)
                        .append("] ")
                        .append(c.locator.getDefinedName())
                        .append("\n      ")
                        .append(c.detail)
                        .append("\n");
            }
            body.append("\nDelete all ").append(report.candidates.size()).append(" row(s)?");

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "", ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText("Clean orphan locators");
            TextArea ta = new TextArea(body.toString());
            ta.setEditable(false);
            ta.setWrapText(false);
            ta.setPrefRowCount(20);
            ta.setPrefColumnCount(80);
            ta.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;");
            confirm.getDialogPane().setContent(ta);
            confirm.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.YES) {
                    int deleted = svc.deleteCandidates(report.candidates);
                    statusInfo("Cleanup complete — deleted " + deleted + " orphan locator(s).");
                } else {
                    statusInfo("Cleanup cancelled — " + report.candidates.size() + " candidate(s) untouched.");
                }
            });
        } catch (Exception ex) {
            log.warn("Locator cleanup failed", ex);
            status("Cleanup failed: " + ex.getMessage(), false);
        }
    }

    private void onTest() {
        try {
            String pathDb = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PATH_DB);
            Path diagDir = Paths.get(pathDb, PageDiagnosticDumper.SUBFOLDER);
            Path pngPath = diagDir.resolve("page-HP.png");
            if (!Files.exists(pngPath)) {
                status("No cached page-HP.png — run Page Scanner first.", false);
                return;
            }
            BufferedImage img = ImageIO.read(pngPath.toFile());
            if (img == null) {
                status("page-HP.png decoded as null.", false);
                return;
            }

            OcrConfigProfile ephemeral = new OcrConfigProfile();
            ephemeral.setName("__test__");
            OcrConfig cfg = new OcrConfig(ephemeral, new ArrayList<>(currentParams));

            OcrResult ocr = WebPageOcrService.recognizeMultiPass(img, cfg);

            // Use whichever DTO file is most recently modified — Page Scanner writes -PS,
            // hover-pick writes -HP. Either path produces the same diagnostic side-files.
            Path elementsPath =
                    chooseLatest(diagDir.resolve("elementDTO-HP.json"), diagDir.resolve("elementDTO-PS.json"));
            if (elementsPath == null) {
                status("No cached element DTOs — run Page Scanner or hover-pick first.", false);
                return;
            }
            String sourceLabel = elementsPath.getFileName().toString();
            ElementDTO[] elements = readElementDTOs(elementsPath);
            List<OcrDomCorrelator.RectEntry> rects = readRects(diagDir.resolve("page-HP-rects.json"));
            double dpr = readDprFromMeta(diagDir.resolve("page-HP-meta.json"));
            List<OcrCorrelationResult> corr = OcrDomCorrelator.correlate(elements, rects, ocr, dpr, cfg);

            int exact = 0, overlap = 0, prox = 0, none = 0;
            for (OcrCorrelationResult r : corr) {
                switch (r.matchQuality == null ? "NONE" : r.matchQuality) {
                    case "EXACT_CONTAIN":
                        exact++;
                        break;
                    case "OVERLAP":
                        overlap++;
                        break;
                    case "PROXIMITY":
                        prox++;
                        break;
                    default:
                        none++;
                        break;
                }
            }
            String summary = String.format(
                    "Test [%s]: %d OCR words · %d EXACT · %d OVERLAP · %d PROX · %d NONE (profile not saved)",
                    sourceLabel, ocr.getWords().size(), exact, overlap, prox, none);
            statusInfo(summary);
            logScanner.info(
                    "TEST_ON_CURRENT_PAGE — {} (profile name in editor: '{}')",
                    summary,
                    nameField.getText() == null
                            ? "<unset>"
                            : nameField.getText().trim());

            // Always render the annotated PNG for the review modal — independent of the
            // saved-profile output.save_annotated_png setting; this is a one-shot diagnostic.
            Path annotated = diagDir.resolve("page-HP-test-annotated.png");
            try {
                AnnotatedImageRenderer.render(img, ocr.getWords(), corr, dpr, annotated);
            } catch (Exception annotEx) {
                log.warn("Could not render test-annotated PNG: {}", annotEx.getMessage());
            }

            // Build review rows (one per ElementDTO with its correlation result).
            java.util.Map<String, OcrCorrelationResult> byXp = new java.util.HashMap<>();
            for (OcrCorrelationResult c : corr) {
                if (c != null && c.xpath != null) byXp.put(c.xpath, c);
            }
            java.util.List<OcrTestResultRow> rows = new java.util.ArrayList<>();
            if (elements != null) {
                for (ElementDTO e : elements) {
                    if (e == null || e.getXPath() == null) continue;
                    OcrCorrelationResult c = byXp.get(e.getXPath());
                    rows.add(new OcrTestResultRow(
                            e.getDefinedName(),
                            c == null ? "NONE" : c.matchQuality,
                            e.getTagName(),
                            e.getSomeText(),
                            c == null ? "" : c.ocrText,
                            e.getXPath()));
                }
            }
            // Sort: EXACT_CONTAIN first, OVERLAP, PROXIMITY, NONE — easier to scan good ones at the top.
            rows.sort((a, b) -> qualityRank(a.getMatchQuality()) - qualityRank(b.getMatchQuality()));

            // The output.approved_xpaths param holds the user's persisted QA ticks for this profile.
            // Passing it through means toggles in the review grid mutate this param's value, so the
            // next Save / Save As New persists the approvals to DB.
            OcrConfigParam approvalsParam = null;
            for (OcrConfigParam p : currentParams) {
                if ("output".equals(p.getCategory()) && "approved_xpaths".equals(p.getName())) {
                    approvalsParam = p;
                    break;
                }
            }

            AROcrTestResultsScene.getInstance().openWith(summary, rows, annotated, approvalsParam);
        } catch (Exception ex) {
            log.warn("Test On Current Page failed", ex);
            status("Test failed: " + ex.getMessage(), false);
        }
    }

    /** Among existing candidate paths, return the one with the most recent mtime. Null when none exist. */
    private static Path chooseLatest(Path... candidates) {
        Path latest = null;
        long latestMs = -1L;
        for (Path p : candidates) {
            if (p == null || !Files.exists(p)) continue;
            try {
                long ms = Files.getLastModifiedTime(p).toMillis();
                if (ms > latestMs) {
                    latestMs = ms;
                    latest = p;
                }
            } catch (Exception ignore) {
                // skip unreadable
            }
        }
        return latest;
    }

    private static int qualityRank(String q) {
        if (q == null) return 99;
        switch (q) {
            case "EXACT_CONTAIN":
                return 0;
            case "OVERLAP":
                return 1;
            case "PROXIMITY":
                return 2;
            case "NONE":
            default:
                return 3;
        }
    }

    // ── File I/O helpers for Test On Current Page ─────────────────────────

    private ElementDTO[] readElementDTOs(Path p) {
        if (!Files.exists(p)) return new ElementDTO[0];
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            ElementDTO[] arr = GSON.fromJson(r, ElementDTO[].class);
            return arr == null ? new ElementDTO[0] : arr;
        } catch (Exception e) {
            log.debug("readElementDTOs failed: {}", e.getMessage());
            return new ElementDTO[0];
        }
    }

    private List<OcrDomCorrelator.RectEntry> readRects(Path p) {
        if (!Files.exists(p)) return Collections.emptyList();
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            OcrDomCorrelator.RectEntry[] arr = GSON.fromJson(r, OcrDomCorrelator.RectEntry[].class);
            if (arr == null) return Collections.emptyList();
            List<OcrDomCorrelator.RectEntry> out = new ArrayList<>(arr.length);
            Collections.addAll(out, arr);
            return out;
        } catch (Exception e) {
            log.debug("readRects failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private double readDprFromMeta(Path p) {
        if (!Files.exists(p)) return 1.0;
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            if (el != null && el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("devicePixelRatio") && !obj.get("devicePixelRatio").isJsonNull()) {
                    return obj.get("devicePixelRatio").getAsDouble();
                }
            }
        } catch (Exception ignored) {
        }
        return 1.0;
    }

    // ── Misc helpers ──────────────────────────────────────────────────────

    private void updateButtonStates() {
        boolean editingExisting = currentProfile != null;
        deleteButton.setDisable(!editingExisting || currentProfile.isDefault());
        saveButton.setDisable(!editingExisting);
    }

    private void status(String msg, boolean success) {
        statusLabel.setStyle(
                success
                        ? "-fx-text-fill: #0a5; -fx-font-weight: bold;"
                        : "-fx-text-fill: #c30; -fx-font-weight: bold;");
        statusLabel.setText(msg);
    }

    private void statusInfo(String msg) {
        statusLabel.setStyle("-fx-text-fill: #1565c0; -fx-font-weight: bold;");
        statusLabel.setText(msg);
    }

    private String formatScope() {
        String hbName = "(none)";
        String urlLabel = "(none)";
        if (currentHomebankingId != null) {
            HomeBankingLoadDTO hb = performLists.getHomeBankingById(currentHomebankingId);
            if (hb != null) hbName = hb.getName() + " [id=" + hb.getId() + "]";
        }
        if (currentHomeUrlId != null && currentHomebankingId != null) {
            HomeUrlDTO homeUrl = performLists.getHomeUrlByBankId(currentHomebankingId, currentHomeUrlId);
            if (homeUrl != null) urlLabel = homeUrl.getUrl() + " (orgName=" + homeUrl.getOrgName() + ")";
        }
        return "HomeBanking: " + hbName + "\nHome URL: " + urlLabel;
    }

    private String resolveOrgName(Integer hbId, Integer homeUrlId) {
        if (hbId == null || homeUrlId == null) return "";
        HomeUrlDTO homeUrl = performLists.getHomeUrlByBankId(hbId, homeUrlId);
        return homeUrl == null || homeUrl.getOrgName() == null ? "" : homeUrl.getOrgName();
    }

    private String proposeName() {
        return baseName() + " v" + nextVersionFor(baseName());
    }

    private String baseName() {
        String b = currentOrgName == null || currentOrgName.isBlank() ? "OCR Profile" : currentOrgName;
        return b + " - Login Page";
    }

    private int nextVersionFor(String base) {
        Pattern p = Pattern.compile("^" + Pattern.quote(base) + " v(\\d+)$");
        int max = 0;
        for (OcrConfigProfile pr : repo.listProfiles()) {
            Matcher m = p.matcher(pr.getName() == null ? "" : pr.getName());
            if (m.matches()) {
                try {
                    int v = Integer.parseInt(m.group(1));
                    if (v > max) max = v;
                } catch (NumberFormatException ignore) {
                    // skip malformed
                }
            }
        }
        return max + 1;
    }

    private String profileLabel(OcrConfigProfile p) {
        if (p == null || p.getName() == null) return "";
        return p.getName();
    }

    private String scopeOf(OcrConfigProfile p) {
        if (p == null) return "";
        if (p.isDefault()) return "default";
        if (p.getHomeUrlId() != null) {
            return "hb=" + p.getHomebankingId() + " url=" + p.getHomeUrlId();
        }
        if (p.getHomebankingId() != null) return "hb=" + p.getHomebankingId();
        return "(global)";
    }

    private String formatTs(Timestamp ts) {
        if (ts == null) return "";
        try {
            return ts.toLocalDateTime().format(TS_FMT);
        } catch (Exception e) {
            return "";
        }
    }

    private static int parseIntOr(String s, int fallback) {
        if (s == null) return fallback;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            try {
                return (int) Double.parseDouble(s.trim());
            } catch (NumberFormatException ee) {
                return fallback;
            }
        }
    }

    private static double parseDoubleOr(String s, double fallback) {
        if (s == null) return fallback;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
