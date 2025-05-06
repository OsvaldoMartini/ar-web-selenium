package com.allinweb.ch.socket;

import java.net.URI;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;
import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;

public class WebSocketTestLauncher {

    static {
        try {
            // Trust all SSL certs
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // 👇 Disables hostname verification (important for 'localhost' in self-signed certs)
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

            // Set global default for WebSocketContainer if needed
            System.setProperty("javax.net.ssl.trustStoreType", "JKS");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        String uri = "wss://localhost:61757/websocket";

        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.connectToServer(WebSocketServer.class, URI.create(uri));
    }
}
