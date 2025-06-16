package com.allinweb.ch.socket;

import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefAppHandlerAdapter;

import javax.swing.*;
import java.awt.*;

public class JavaCEFExample {
    public static void main(String[] args) {
        CefApp cefApp = CefApp.getInstance();
        CefClient client = cefApp.createClient();

        // You can load any WASM page here
        String url = "https://webassembly.org/demo/";

        CefBrowser browser = client.createBrowser(url, false, false);
        Component browserUI = browser.getUIComponent();

        JFrame frame = new JFrame("JavaCEF - WebAssembly Demo");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.getContentPane().add(browserUI, BorderLayout.CENTER);
        frame.setSize(1024, 768);
        frame.setVisible(true);
    }
}
