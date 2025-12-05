package com.allinweb.ch.util;

import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;

import javax.swing.*;
import java.awt.*;

public class JcefTest {

    private static boolean cefStarted = false;

    public static void main(String[] args) {

        // ---------------------------------------
        // 1) EXACT same initialization as your app
        // ---------------------------------------
        if (!cefStarted) {
            CefApp.startup(new String[0]);
            cefStarted = true;

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (CefApp.getInstance() != null) {
                        CefApp.getInstance().dispose();
                    }
                } catch (Throwable ignored) {}
            }));
        }

        // ---------------------------------------
        // 2) Use local HTTP server instead of file
        // ---------------------------------------
        String startURL = "http://localhost:3000";

        // ---------------------------------------
        // 3) Build UI on EDT
        // ---------------------------------------
        SwingUtilities.invokeLater(() -> initUI(startURL));
    }

    private static void initUI(String url) {

        CefApp cefApp = CefApp.getInstance();
        CefClient client = cefApp.createClient();

        CefBrowser browser = client.createBrowser(url, false, false);
        Component browserUI = browser.getUIComponent();

        JFrame frame = new JFrame("JCEF Test - Local Server");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(browserUI, BorderLayout.CENTER);
        frame.setSize(900, 700);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
