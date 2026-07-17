package com.allinweb.ch.vision;

import com.allinweb.ch.model.OcrConfig;
import com.allinweb.ch.ocr.bridge.OcrResult;
import com.allinweb.ch.ocr.bridge.OcrWord;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Word;

@Slf4j
final class Tess4jRasterOcrAdapter {

    private Tess4jRasterOcrAdapter() {}

    static OcrResult recognize(RasterImage image, OcrConfig cfg) {
        if (image == null) return new OcrResult("", new ArrayList<>());
        try {
            ITesseract tess = WebPageOcrService.createEngine(cfg);
            List<Word> words = tess.getWords(toBufferedImage(image), ITessAPI.TessPageIteratorLevel.RIL_WORD);
            List<OcrWord> out = new ArrayList<>();
            StringBuilder full = new StringBuilder();
            for (Word word : words) {
                String text = word.getText() == null ? "" : word.getText().trim();
                if (text.isEmpty()) continue;
                out.add(new OcrWord(text, Tess4jOcrBoxAdapter.boundsOf(word), word.getConfidence()));
                full.append(text).append(' ');
            }
            return new OcrResult(full.toString().trim(), out);
        } catch (Exception e) {
            log.warn("OCR recognize failed: {}", e.getMessage(), e);
            return new OcrResult("", new ArrayList<>());
        }
    }

    private static BufferedImage toBufferedImage(RasterImage image) {
        BufferedImage buffered = new BufferedImage(image.width(), image.height(), BufferedImage.TYPE_INT_RGB);
        buffered.setRGB(0, 0, image.width(), image.height(), image.copyRgb(), 0, image.width());
        return buffered;
    }
}
