package com.allinweb.ch.vision.ocr;

import java.util.ArrayList;
import java.util.List;
import org.opencv.core.*;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

public class OcrPreprocessorOpenCv {

    // OpenCV native is loaded via OpenCvNativeLoader; do not reload here.

    /** Main preprocessing pipeline for general OCR. */
    public static Mat preprocess(Mat input) {
        Mat img = new Mat();
        if (input.type() != CvType.CV_8UC3) {
            Imgproc.cvtColor(input, img, Imgproc.COLOR_BGRA2BGR);
        } else {
            input.copyTo(img);
        }

        Imgproc.resize(img, img, new Size(img.width() * 2, img.height() * 2), 0, 0, Imgproc.INTER_CUBIC);

        Mat bilateral = new Mat();
        Imgproc.bilateralFilter(img, bilateral, 9, 75, 75);
        bilateral.copyTo(img);

        Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2GRAY);

        Mat claheImg = new Mat();
        CLAHE clahe = Imgproc.createCLAHE();
        clahe.setClipLimit(3.0);
        clahe.apply(img, claheImg);

        Mat thresh = new Mat();
        Imgproc.adaptiveThreshold(
                claheImg, thresh, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 21, 10);

        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, 2));
        Imgproc.dilate(thresh, thresh, kernel);

        return thresh;
    }

    /** Preprocessing tuned for small button text. */
    public static Mat preprocessButton(Mat input) {
        Mat img = new Mat();
        if (input.channels() == 4) {
            Imgproc.cvtColor(input, img, Imgproc.COLOR_BGRA2GRAY);
        } else if (input.channels() == 3) {
            Imgproc.cvtColor(input, img, Imgproc.COLOR_BGR2GRAY);
        } else {
            input.copyTo(img);
        }

        Imgproc.resize(img, img, new Size(img.width() * 3, img.height() * 3), 0, 0, Imgproc.INTER_CUBIC);
        Imgproc.GaussianBlur(img, img, new Size(3, 3), 0);

        Mat bin = new Mat();
        Imgproc.threshold(img, bin, 0, 255, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU);

        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, 2));
        Imgproc.dilate(bin, bin, kernel);

        return bin;
    }

    public static Mat crop(Mat img, Rect rect) {
        return new Mat(img, rect);
    }

    /** Detects red-ish rectangular areas (buttons). */
    public static List<Rect> detectRedButtons(Mat srcBgr) {
        List<Rect> result = new ArrayList<>();
        if (srcBgr == null || srcBgr.empty()) return result;

        Mat hsv = new Mat();
        Imgproc.cvtColor(srcBgr, hsv, Imgproc.COLOR_BGR2HSV);

        Mat mask1 = new Mat();
        Mat mask2 = new Mat();
        Core.inRange(hsv, new Scalar(0, 80, 80), new Scalar(10, 255, 255), mask1);
        Core.inRange(hsv, new Scalar(160, 80, 80), new Scalar(179, 255, 255), mask2);

        Mat mask = new Mat();
        Core.add(mask1, mask2, mask);

        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel);
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        for (MatOfPoint contour : contours) {
            Rect rect = Imgproc.boundingRect(contour);
            double area = rect.area();
            if (area < 1000) continue;
            double aspect = rect.width / (double) rect.height;
            if (aspect < 1.5 || aspect > 10.0) continue;
            double contourArea = Imgproc.contourArea(contour);
            double solidity = contourArea / area;
            if (solidity < 0.8) continue;
            result.add(rect);
        }

        mask1.release();
        mask2.release();
        mask.release();
        hsv.release();
        kernel.release();
        hierarchy.release();
        return result;
    }

    /** Detects blue-ish rectangular areas (buttons). */
    public static List<Rect> detectBlueButtons(Mat srcBgr) {
        List<Rect> result = new ArrayList<>();
        if (srcBgr == null || srcBgr.empty()) return result;

        Mat hsv = new Mat();
        Imgproc.cvtColor(srcBgr, hsv, Imgproc.COLOR_BGR2HSV);

        Mat mask = new Mat();
        Core.inRange(hsv, new Scalar(90, 80, 80), new Scalar(130, 255, 255), mask);

        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel);
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        for (MatOfPoint contour : contours) {
            Rect rect = Imgproc.boundingRect(contour);
            double area = rect.area();
            if (area < 1000) continue;
            double aspect = rect.width / (double) rect.height;
            if (aspect < 1.5 || aspect > 10.0) continue;
            double contourArea = Imgproc.contourArea(contour);
            double solidity = contourArea / area;
            if (solidity < 0.8) continue;
            result.add(rect);
        }

        hsv.release();
        mask.release();
        kernel.release();
        hierarchy.release();
        return result;
    }

    /** Color-agnostic button detection via edges + morphology. */
    public static List<Rect> detectAnyButtons(Mat srcBgr) {
        List<Rect> result = new ArrayList<>();
        if (srcBgr == null || srcBgr.empty()) return result;

        Mat gray = new Mat();
        Imgproc.cvtColor(srcBgr, gray, Imgproc.COLOR_BGR2GRAY);

        Mat edges = new Mat();
        Imgproc.Canny(gray, edges, 60, 180);

        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 3));
        Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        for (MatOfPoint contour : contours) {
            Rect rect = Imgproc.boundingRect(contour);
            double area = rect.area();
            if (area < 1200) continue;
            double aspect = rect.width / (double) rect.height;
            if (aspect < 1.5 || aspect > 12.0) continue;
            double contourArea = Imgproc.contourArea(contour);
            double solidity = contourArea / area;
            if (solidity < 0.6) continue;
            result.add(rect);
        }

        gray.release();
        edges.release();
        kernel.release();
        hierarchy.release();
        return result;
    }
}
