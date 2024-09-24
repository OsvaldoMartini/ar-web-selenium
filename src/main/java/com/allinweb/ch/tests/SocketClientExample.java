package com.allinweb.ch.tests;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class SocketClientExample extends Application {

    private TextArea textArea;
    private TextField messageField;
    private PrintWriter writer;
    private BufferedReader reader;
    private Socket socket;

    @Override
    public void start(Stage primaryStage) {
        textArea = new TextArea();
        textArea.setEditable(false);
        messageField = new TextField();
        Button sendButton = new Button("Send Message");

        sendButton.setOnAction(e -> sendMessage());

        VBox layout = new VBox(10, textArea, messageField, sendButton);
        Scene scene = new Scene(layout, 300, 250);

        primaryStage.setScene(scene);
        primaryStage.setTitle("JavaFX Client Socket");
        primaryStage.show();

        // Start client connection in the background
        connectToServer();
    }

    private void connectToServer() {
        Task<Void> clientTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    socket = new Socket("localhost", 12345); // Replace with server address and port
                    writer = new PrintWriter(socket.getOutputStream(), true);
                    reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    appendMessage("Connected to server");

                    // Continuously read messages from the server
                    String response;
                    while ((response = reader.readLine()) != null) {
                        String finalResponse = response;
                        javafx.application.Platform.runLater(
                                () -> textArea.appendText("Server: " + finalResponse + "\n"));
                    }
                } catch (Exception e) {
                    appendMessage("Error: " + e.getMessage());
                }
                return null;
            }
        };

        new Thread(clientTask).start();
    }

    private void sendMessage() {
        String message = messageField.getText();
        if (message != null && !message.isEmpty() && writer != null) {
            writer.println(message);
            textArea.appendText("Client: " + message + "\n");
            messageField.clear();
        }
    }

    private void appendMessage(String message) {
        javafx.application.Platform.runLater(() -> textArea.appendText(message + "\n"));
    }

    @Override
    public void stop() throws Exception {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
