package com.allinweb.ch.tests;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class SocketServerExample extends Application {

    private TextArea textArea;
    private ServerSocket serverSocket;

    @Override
    public void start(Stage primaryStage) {
        textArea = new TextArea();
        Button startServerButton = new Button("Start Server");

        startServerButton.setOnAction(e -> startServer());

        VBox layout = new VBox(10, textArea, startServerButton);
        Scene scene = new Scene(layout, 300, 250);

        primaryStage.setScene(scene);
        primaryStage.setTitle("JavaFX Socket Server");
        primaryStage.show();
    }

    private void startServer() {
        Task<Void> serverTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                serverSocket = new ServerSocket(12345); // Listening on port 12345
                appendMessage("Server started, waiting for clients...");

                while (true) {
                    Socket clientSocket = serverSocket.accept(); // Accept client connection
                    appendMessage("Client connected!");

                    // Handle client communication in a separate thread
                    new Thread(new ClientHandler(clientSocket)).start();
                }
            }
        };

        // Run the server task in a background thread
        new Thread(serverTask).start();
    }

    private void appendMessage(String message) {
        javafx.application.Platform.runLater(() -> textArea.appendText(message + "\n"));
    }

    private class ClientHandler implements Runnable {
        private Socket clientSocket;
        private PrintWriter writer;
        private BufferedReader reader;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try {
                writer = new PrintWriter(clientSocket.getOutputStream(), true);
                reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                String clientMessage;
                while ((clientMessage = reader.readLine()) != null) {
                    appendMessage("Received: " + clientMessage);
                    writer.println("Echo: " + clientMessage); // Echo the message back to the client
                }
            } catch (Exception e) {
                appendMessage("Error: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (Exception e) {
                    appendMessage("Error closing client socket: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void stop() throws Exception {
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
