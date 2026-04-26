package com.allinweb.ch.component.widget;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Roadmap 2 follow-on: replaces the raw-JSON TextArea for {@code color_mapping.ops}
 * with a visual HSV picker. Each op renders as a collapsible card with sliders
 * for HSV bounds + live preview swatches, plus replacement-BGR sliders for
 * {@code replace_in_hsv_range}. Bidirectional: editing the sliders rewrites
 * the JSON; loading existing JSON populates the sliders.
 *
 * <p>Schema (matches {@code COLOR_OP_TEMPLATE} in {@code AROcrConfigPane}):
 * <pre>{@code
 *   { "name": "label",
 *     "mode": "replace_in_hsv_range" | "keep_in_range_binarize"
 *           | "desaturate_to_black_white" | "invert",
 *     "hsv_lower": [H, S, V],
 *     "hsv_upper": [H, S, V],
 *     "replacement_bgr": [B, G, R] }
 * }</pre>
 *
 * <p>OpenCV ranges: H 0-179, S/V 0-255, BGR 0-255. The sliders use these directly.
 */
public final class HsvColorOpEditor extends VBox {

    private static final Gson GSON = new Gson();
    private static final List<String> MODES = List.of(
            "replace_in_hsv_range", "keep_in_range_binarize", "desaturate_to_black_white", "invert");

    private final VBox cardsBox = new VBox(6);
    private final Consumer<String> onChange;
    private boolean suppress = false;

    public HsvColorOpEditor(String initialJson, Consumer<String> onChange) {
        super(8);
        this.onChange = onChange;
        setPadding(new Insets(4));

        Button addOp = new Button("+ Add HSV op");
        addOp.setOnAction(e -> {
            cardsBox.getChildren().add(buildCard(defaultOp("new-op")));
            emit();
        });

        getChildren().addAll(cardsBox, addOp);
        loadFromJson(initialJson);
    }

    public void loadFromJson(String json) {
        suppress = true;
        try {
            cardsBox.getChildren().clear();
            if (json == null || json.isBlank()) return;
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) return;
            for (JsonElement el : root.getAsJsonArray()) {
                if (el.isJsonObject()) cardsBox.getChildren().add(buildCard(el.getAsJsonObject()));
            }
        } catch (RuntimeException ignore) {
            // Malformed JSON — leave list empty; user can rebuild via "Add HSV op".
        } finally {
            suppress = false;
        }
    }

    private void emit() {
        if (suppress || onChange == null) return;
        JsonArray arr = new JsonArray();
        for (var node : cardsBox.getChildren()) {
            if (node instanceof TitledPane tp && tp.getUserData() instanceof JsonObject op) {
                arr.add(op);
            }
        }
        onChange.accept(GSON.toJson(arr));
    }

    private static JsonObject defaultOp(String name) {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("mode", "replace_in_hsv_range");
        JsonArray lower = new JsonArray();
        lower.add(0);
        lower.add(80);
        lower.add(80);
        o.add("hsv_lower", lower);
        JsonArray upper = new JsonArray();
        upper.add(10);
        upper.add(255);
        upper.add(255);
        o.add("hsv_upper", upper);
        JsonArray bgr = new JsonArray();
        bgr.add(0);
        bgr.add(0);
        bgr.add(0);
        o.add("replacement_bgr", bgr);
        return o;
    }

    private TitledPane buildCard(JsonObject op) {
        // Stash the live JSON object on the card so emit() can rebuild the array
        // without re-reading every slider value.
        TitledPane card = new TitledPane();
        card.setUserData(op);
        card.setExpanded(false);

        TextField nameField = new TextField(getString(op, "name", "op"));
        nameField.setPromptText("name");
        nameField.textProperty().addListener((o, ov, nv) -> {
            op.addProperty("name", nv == null ? "" : nv);
            card.setText(opTitle(op));
            emit();
        });

        ChoiceBox<String> modeBox = new ChoiceBox<>();
        modeBox.getItems().addAll(MODES);
        modeBox.setValue(getString(op, "mode", "replace_in_hsv_range"));
        modeBox.valueProperty().addListener((o, ov, nv) -> {
            op.addProperty("mode", nv);
            card.setText(opTitle(op));
            emit();
        });

        Region prevLow = swatch();
        Region prevHigh = swatch();
        Region prevReplace = swatch();
        prevReplace.setPrefSize(28, 16);

        VBox lowerRow = hsvRow("hsv_lower (H 0-179, S 0-255, V 0-255)", op, "hsv_lower", prevLow);
        VBox upperRow = hsvRow("hsv_upper", op, "hsv_upper", prevHigh);
        VBox bgrRow = bgrRow("replacement_bgr (B G R, 0-255)", op, "replacement_bgr", prevReplace);

        HBox previewRow = new HBox(8,
                labelled("lower", prevLow), labelled("upper", prevHigh), labelled("replace", prevReplace));
        previewRow.setAlignment(Pos.CENTER_LEFT);

        Button delete = new Button("Delete op");
        delete.setStyle("-fx-text-fill: #c62828;");
        delete.setOnAction(e -> {
            cardsBox.getChildren().remove(card);
            emit();
        });

        HBox header = new HBox(8, new Label("name:"), nameField, new Label("mode:"), modeBox);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(nameField, Priority.SOMETIMES);

        VBox body = new VBox(6, header, previewRow, lowerRow, upperRow, bgrRow, delete);
        body.setPadding(new Insets(6));
        card.setContent(body);
        card.setText(opTitle(op));

        // Initial swatches
        repaintSwatches(op, prevLow, prevHigh, prevReplace);
        return card;
    }

    private VBox hsvRow(String title, JsonObject op, String key, Region preview) {
        int[] vals = readTriple(op, key, key.endsWith("upper") ? new int[] {179, 255, 255} : new int[] {0, 0, 0});
        Slider hSlider = slider(0, 179, vals[0]);
        Slider sSlider = slider(0, 255, vals[1]);
        Slider vSlider = slider(0, 255, vals[2]);
        Label hVal = boundLabel(hSlider);
        Label sVal = boundLabel(sSlider);
        Label vVal = boundLabel(vSlider);

        Runnable push = () -> {
            JsonArray arr = new JsonArray();
            arr.add((int) hSlider.getValue());
            arr.add((int) sSlider.getValue());
            arr.add((int) vSlider.getValue());
            op.add(key, arr);
            preview.setStyle("-fx-background-color: " + hsvCss(
                    (int) hSlider.getValue(), (int) sSlider.getValue(), (int) vSlider.getValue())
                    + "; -fx-border-color: #555; -fx-border-width: 1;");
            emit();
        };
        hSlider.valueProperty().addListener((o, ov, nv) -> push.run());
        sSlider.valueProperty().addListener((o, ov, nv) -> push.run());
        vSlider.valueProperty().addListener((o, ov, nv) -> push.run());

        VBox box = new VBox(2,
                new Label(title),
                new HBox(6, new Label("H"), hSlider, hVal),
                new HBox(6, new Label("S"), sSlider, sVal),
                new HBox(6, new Label("V"), vSlider, vVal));
        return box;
    }

    private VBox bgrRow(String title, JsonObject op, String key, Region preview) {
        int[] vals = readTriple(op, key, new int[] {0, 0, 0});
        Slider bSlider = slider(0, 255, vals[0]);
        Slider gSlider = slider(0, 255, vals[1]);
        Slider rSlider = slider(0, 255, vals[2]);
        Label bVal = boundLabel(bSlider);
        Label gVal = boundLabel(gSlider);
        Label rVal = boundLabel(rSlider);

        Runnable push = () -> {
            JsonArray arr = new JsonArray();
            arr.add((int) bSlider.getValue());
            arr.add((int) gSlider.getValue());
            arr.add((int) rSlider.getValue());
            op.add(key, arr);
            preview.setStyle(String.format(
                    "-fx-background-color: rgb(%d,%d,%d); -fx-border-color: #555; -fx-border-width: 1;",
                    (int) rSlider.getValue(), (int) gSlider.getValue(), (int) bSlider.getValue()));
            emit();
        };
        bSlider.valueProperty().addListener((o, ov, nv) -> push.run());
        gSlider.valueProperty().addListener((o, ov, nv) -> push.run());
        rSlider.valueProperty().addListener((o, ov, nv) -> push.run());

        VBox box = new VBox(2,
                new Label(title),
                new HBox(6, new Label("B"), bSlider, bVal),
                new HBox(6, new Label("G"), gSlider, gVal),
                new HBox(6, new Label("R"), rSlider, rVal));
        return box;
    }

    private static Slider slider(double min, double max, double initial) {
        Slider s = new Slider(min, max, initial);
        s.setShowTickMarks(true);
        s.setShowTickLabels(false);
        s.setMajorTickUnit((max - min) / 4);
        s.setBlockIncrement(1);
        HBox.setHgrow(s, Priority.ALWAYS);
        return s;
    }

    private static Label boundLabel(Slider s) {
        Label l = new Label(String.valueOf((int) s.getValue()));
        l.setMinWidth(36);
        s.valueProperty().addListener((o, ov, nv) -> l.setText(String.valueOf(nv.intValue())));
        return l;
    }

    private static Region swatch() {
        Region r = new Region();
        r.setPrefSize(36, 24);
        r.setStyle("-fx-background-color: #fff; -fx-border-color: #555; -fx-border-width: 1;");
        return r;
    }

    private static HBox labelled(String text, Region body) {
        HBox h = new HBox(4, new Label(text + ":"), body);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private static void repaintSwatches(JsonObject op, Region low, Region high, Region replace) {
        int[] l = readTriple(op, "hsv_lower", new int[] {0, 0, 0});
        int[] u = readTriple(op, "hsv_upper", new int[] {179, 255, 255});
        int[] b = readTriple(op, "replacement_bgr", new int[] {0, 0, 0});
        low.setStyle("-fx-background-color: " + hsvCss(l[0], l[1], l[2]) + "; -fx-border-color: #555;");
        high.setStyle("-fx-background-color: " + hsvCss(u[0], u[1], u[2]) + "; -fx-border-color: #555;");
        replace.setStyle(String.format(
                "-fx-background-color: rgb(%d,%d,%d); -fx-border-color: #555;", b[2], b[1], b[0]));
    }

    /** OpenCV H is 0-179, S/V 0-255. JavaFX Color expects H 0-360, S/V 0-1. */
    private static String hsvCss(int h, int s, int v) {
        double hueDeg = (h / 179.0) * 360.0;
        double sat = s / 255.0;
        double val = v / 255.0;
        Color c = Color.hsb(hueDeg, sat, val);
        return String.format(
                "rgb(%d,%d,%d)",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    private static int[] readTriple(JsonObject op, String key, int[] fallback) {
        try {
            JsonElement el = op.get(key);
            if (el == null || !el.isJsonArray()) return fallback;
            JsonArray a = el.getAsJsonArray();
            if (a.size() < 3) return fallback;
            return new int[] {a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()};
        } catch (RuntimeException ignore) {
            return fallback;
        }
    }

    private static String getString(JsonObject op, String key, String fallback) {
        try {
            JsonElement el = op.get(key);
            return el == null || el.isJsonNull() ? fallback : el.getAsString();
        } catch (RuntimeException ignore) {
            return fallback;
        }
    }

    private static String opTitle(JsonObject op) {
        return getString(op, "name", "op") + " — " + getString(op, "mode", "replace_in_hsv_range");
    }

    /** Listing helper for tests. */
    public List<JsonObject> ops() {
        List<JsonObject> out = new ArrayList<>();
        for (var node : cardsBox.getChildren()) {
            if (node instanceof TitledPane tp && tp.getUserData() instanceof JsonObject op) out.add(op);
        }
        return out;
    }
}
