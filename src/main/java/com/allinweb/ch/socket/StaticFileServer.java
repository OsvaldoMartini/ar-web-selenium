package com.allinweb.ch.socket;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;

public class StaticFileServer {

  public static void start(int port, File rootDir) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext(
        "/",
        exchange -> {
          URI uri = exchange.getRequestURI();
          String path = uri.getPath();
          File file = new File(rootDir, path.equals("/") ? "index.html" : path);

          if (!file.exists()) {
            exchange.sendResponseHeaders(404, -1);
            return;
          }

          String mime = Files.probeContentType(file.toPath());
          exchange
              .getResponseHeaders()
              .add("Content-Type", mime != null ? mime : "application/octet-stream");
          exchange.sendResponseHeaders(200, file.length());
          try (OutputStream os = exchange.getResponseBody();
              FileInputStream fis = new FileInputStream(file)) {
            fis.transferTo(os);
          }
        });

    server.setExecutor(null); // Use default executor
    server.start();
    System.out.println("Server started at http://localhost:" + port);
  }
}
