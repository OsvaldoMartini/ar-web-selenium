// package com.allinweb.ch.ai;
//
// import java.awt.image.BufferedImage;
// import java.io.File;
// import java.io.FileWriter;
// import javax.imageio.ImageIO;
// import net.sourceforge.tess4j.ITessAPI;
// import net.sourceforge.tess4j.ITesseract;
// import net.sourceforge.tess4j.Tesseract;
// import org.json.JSONArray;
// import org.json.JSONObject;
//
// public class OCRExtractor {
//
//    public static void main(String[] args) throws Exception {
//        File imageFile = new File("screenshot.png");
//
//        ITesseract tesseract = new Tesseract();
//        tesseract.setDatapath("tessdata"); // cartella contenente i file .traineddata
//        tesseract.setLanguage("ita+eng");
//
//        JSONArray elements = new JSONArray();
//
//        try {
//            BufferedImage image = ImageIO.read(imageFile);
//            var words = tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD);
//
//            for (var word : words) {
//                String text = word.getText().trim();
//                if (text.isEmpty()) continue;
//
//                int x = word.getBoundingBox().x;
//                int y = word.getBoundingBox().y;
//                int w = word.getBoundingBox().width;
//                int h = word.getBoundingBox().height;
//
//                String type = classifyText(text.toLowerCase());
//
//                JSONObject obj = new JSONObject();
//                obj.put("type", type);
//                obj.put("label", text);
//                obj.put("x", x);
//                obj.put("y", y);
//                obj.put("width", w);
//                obj.put("height", h);
//
//                elements.put(obj);
//            }
//
//            try (FileWriter fw = new FileWriter("elements.json")) {
//                fw.write(elements.toString(2));
//                System.out.println("✅ Estratti " + elements.length() + " elementi in elements.json");
//            }
//
//        } catch (Exception e) {
//            System.err.println("Errore OCR: " + e.getMessage());
//        }
//    }
//
//    private static String classifyText(String text) {
//        if (text.contains("utente") || text.contains("id")) return "input";
//        if (text.contains("password")) return "input";
//        if (text.contains("login") || text.contains("accedi") || text.contains("entra")) return "button";
//        return "text";
//    }
// }
