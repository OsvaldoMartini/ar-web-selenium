package com.allinweb.ch.component.pane;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ScannerExternalBrowserAdapter {

    void open(String url) {
        try {
            java.awt.Desktop desktop =
                    java.awt.Desktop.isDesktopSupported() ? java.awt.Desktop.getDesktop() : null;
            if (desktop != null && desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                desktop.browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url)
                        .inheritIO()
                        .start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception ex) {
            log.warn("openInDefaultBrowser - failed to open {}: {}", url, ex.getMessage());
        }
    }
}
