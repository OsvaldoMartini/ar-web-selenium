package com.allinweb.ch.facade;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.OcrConfig;
import com.allinweb.ch.model.OcrConfigParam;
import com.allinweb.ch.model.OcrConfigProfile;
import com.allinweb.ch.ocr.bridge.OcrResult;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.OcrCorrelationResult;
import com.allinweb.ch.util.OcrDomCorrelator;
import com.allinweb.ch.util.PageDiagnosticDumper;
import com.allinweb.ch.vision.AnnotatedImageRenderer;
import com.allinweb.ch.vision.RasterImage;
import com.allinweb.ch.vision.RasterImageIO;
import com.allinweb.ch.vision.WebPageOcrService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runs OCR against allowlisted cached scanner artifacts without presentation ownership. */
public final class OcrTestService {
    private static final OcrTestService INSTANCE = new OcrTestService();
    private static final Gson GSON = new Gson();
    private OcrTestService() {}
    public static OcrTestService getInstance() { return INSTANCE; }

    public Map<String, Object> run(JsonObject body) {
        try {
            Path directory = diagnosticDirectory();
            Path screenshot = latest(directory.resolve("page-BJ.png"), directory.resolve("page-HP.png"));
            if (screenshot == null) return failure("No cached scanner screenshot. Run Page Scanner first.");
            RasterImage image = RasterImageIO.read(screenshot);
            if (image == null) return failure("Cached page-HP.png could not be decoded.");
            List<OcrConfigParam> params = parameters(body);
            OcrConfig config;
            if (params.isEmpty()) {
                config = OcrConfigService.getInstance().resolveFor(positive(body, "homeBankingId"), positive(body, "homeUrlId"));
            } else {
                OcrConfigProfile ephemeral = new OcrConfigProfile();
                ephemeral.setName("__react_test__");
                config = new OcrConfig(ephemeral, params);
            }
            OcrResult ocr = WebPageOcrService.recognizeMultiPass(image, config);
            Path elementsFile = latest(
                    directory.resolve("elementDTO-PS-BJ.json"),
                    directory.resolve("elementDTO-HP.json"),
                    directory.resolve("elementDTO-PS.json"));
            if (elementsFile == null) return failure("No cached element DTOs. Run Page Scanner first.");
            ElementDTO[] elements = readElements(elementsFile);
            String screenshotBase = screenshot.getFileName().toString().replaceFirst("\\.png$", "");
            List<OcrDomCorrelator.RectEntry> rects = readRects(directory.resolve(screenshotBase + "-rects.json"));
            double dpr = readDpr(directory.resolve(screenshotBase + "-meta.json"));
            List<OcrCorrelationResult> correlations = OcrDomCorrelator.correlate(elements, rects, ocr, dpr, config);
            Map<String, OcrCorrelationResult> byXPath = new HashMap<>();
            for (OcrCorrelationResult correlation : correlations) if (correlation != null && correlation.xpath != null) byXPath.put(correlation.xpath, correlation);
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String quality : List.of("EXACT_CONTAIN", "OVERLAP", "PROXIMITY", "NONE")) counts.put(quality, 0);
            for (ElementDTO element : elements) {
                if (element == null || element.getXPath() == null) continue;
                OcrCorrelationResult correlation = byXPath.get(element.getXPath());
                String quality = correlation == null || correlation.matchQuality == null ? "NONE" : correlation.matchQuality;
                counts.put(quality, counts.getOrDefault(quality, 0) + 1);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("definedName", element.getDefinedName()); row.put("quality", quality);
                row.put("tag", element.getTagName()); row.put("domText", element.getSomeText());
                row.put("ocrText", correlation == null ? "" : correlation.ocrText); row.put("xPath", element.getXPath());
                rows.add(row);
            }
            rows.sort(Comparator.comparingInt(row -> rank(String.valueOf(row.get("quality")))));
            Path annotated = directory.resolve(screenshotBase + "-test-annotated.png");
            AnnotatedImageRenderer.render(image, ocr.getWords(), correlations, dpr, annotated);
            Map<String, Object> result = ok("OCR test complete.");
            result.put("source", elementsFile.getFileName().toString()); result.put("wordCount", ocr.getWords().size());
            result.put("counts", counts); result.put("rows", rows);
            result.put("annotatedImage", "data:image/png;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(annotated)));
            return result;
        } catch (Exception exception) {
            return failure("OCR test failed: " + exception.getMessage());
        }
    }

    private Path diagnosticDirectory() { return Paths.get(ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PATH_DB), PageDiagnosticDumper.SUBFOLDER); }
    private List<OcrConfigParam> parameters(JsonObject body) {
        List<OcrConfigParam> result = new ArrayList<>();
        if (body == null || !body.has("parameters") || !body.get("parameters").isJsonArray()) return result;
        for (JsonElement element : body.getAsJsonArray("parameters")) {
            if (!element.isJsonObject()) continue; JsonObject value = element.getAsJsonObject();
            result.add(new OcrConfigParam(null, 0, text(value,"category"), text(value,"name"), text(value,"valueType"), text(value,"value")));
        }
        return result;
    }
    private String text(JsonObject value,String key) { try { return value.get(key).getAsString(); } catch(Exception ignored) { return ""; } }
    private Integer positive(JsonObject value,String key) { try { int number=value.get(key).getAsInt();return number>0?number:null; } catch(Exception ignored) { return null; } }
    private Path latest(Path... paths) throws Exception { Path found=null; long modified=-1; for(Path path:paths) if(Files.isRegularFile(path)&&Files.getLastModifiedTime(path).toMillis()>modified){found=path;modified=Files.getLastModifiedTime(path).toMillis();} return found; }
    private ElementDTO[] readElements(Path path) throws Exception { try(Reader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){ElementDTO[] values=GSON.fromJson(reader,ElementDTO[].class);return values==null?new ElementDTO[0]:values;} }
    private List<OcrDomCorrelator.RectEntry> readRects(Path path) throws Exception { if(!Files.isRegularFile(path))return Collections.emptyList();try(Reader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){OcrDomCorrelator.RectEntry[] values=GSON.fromJson(reader,OcrDomCorrelator.RectEntry[].class);return values==null?Collections.emptyList():List.of(values);} }
    private double readDpr(Path path) { if(!Files.isRegularFile(path))return 1;try(Reader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){JsonObject value=JsonParser.parseReader(reader).getAsJsonObject();return value.has("devicePixelRatio")?value.get("devicePixelRatio").getAsDouble():1;}catch(Exception ignored){return 1;} }
    private int rank(String quality) { return "EXACT_CONTAIN".equals(quality)?0:"OVERLAP".equals(quality)?1:"PROXIMITY".equals(quality)?2:3; }
    private Map<String,Object> ok(String message){Map<String,Object> value=new LinkedHashMap<>();value.put("ok",true);value.put("message",message);return value;}
    private Map<String,Object> failure(String error){Map<String,Object> value=new LinkedHashMap<>();value.put("ok",false);value.put("error",error);return value;}
}
