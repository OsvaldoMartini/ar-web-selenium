package com.allinweb.ch;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;

public class DatabaseConnectorPostgres {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        Connection conn = null;

        try {
            System.out.println("Enter Postgres Host (e.g., localhost):");
            String dbHost = scanner.nextLine();
            System.out.println("Enter Postgres Port (e.g., 5432, or press Enter for default):");
            String portInput = scanner.nextLine();
            int dbPort = 5432; // Default port
            if (!portInput.isEmpty()) {
                dbPort = Integer.parseInt(portInput);
            }

            String username;
            String password;
            String dbName;
            do {
                System.out.println("Enter Postgres Database Name:");
                dbName = scanner.nextLine();
                System.out.println("Enter Postgres Username:");
                username = scanner.nextLine();
                System.out.println("Enter Postgres Password:");
                password = scanner.nextLine();

                String dbUrl = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/postgres";
                System.out.println("Connecting to Postgres...");
                conn = DriverManager.getConnection(dbUrl, username, password);

                int action;
                do {
                    System.out.println("Choose an action:");
                    System.out.println("1. Connect to database");
                    System.out.println("2. Create database");
                    System.out.println("3. Drop database");
                    System.out.println("4. Change database name");
                    System.out.println("5. Exit");
                    action = scanner.nextInt();
                    scanner.nextLine(); // Consume newline

                    switch (action) {
                        case 1:
                            // Connect to database
                            if (!databaseExists(conn, dbName)) {
                                System.out.println("Database does not exist.");
                                break;
                            }
                            try {
                                conn.close();
                                dbUrl = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
                                conn = DriverManager.getConnection(dbUrl, username, password);
                                System.out.println("Connected to " + dbName + " successfully!");
                                if (conn != null) {
                                    System.out.println(
                                            "Connection established. Use SQL client to perform queries. Press enter to close connection");
                                    scanner.nextLine(); // Wait for enter input to close connection.
                                }
                            } catch (SQLException e) {
                                System.err.println("Database connection error: " + e.getMessage());
                                e.printStackTrace();
                            }
                            break;
                        case 2:
                            // Create database
                            if (!databaseExists(conn, dbName)) {
                                try {
                                    createDatabase(conn, dbName);
                                    System.out.println("\n####   Database " + dbName + " created\n");
                                } catch (SQLException e) {
                                    System.err.println("Database creation error: " + e.getMessage());
                                    e.printStackTrace();
                                }
                            } else {
                                System.out.println("Database " + dbName + " already exists.");
                            }
                            break;
                        case 3:
                            // Drop database
                            if (databaseExists(conn, dbName)) {
                                try {
                                    dropDatabase(conn, dbName);
                                    System.out.println("\n####   Database " + dbName + " dropped\n");
                                } catch (SQLException e) {
                                    System.err.println("Database drop error: " + e.getMessage());
                                    e.printStackTrace();
                                }
                            } else {
                                System.out.println("Database " + dbName + " does not exist.");
                            }
                            break;
                        case 4:
                            break;
                        case 5:
                            System.out.println("Exiting...");
                            break;
                        default:
                            System.out.println("Invalid action.");
                            break;
                    }
                } while (action != 4 && action != 5);
                if (action == 5) {
                    break;
                }

            } while (true);

        } catch (SQLException e) {
            System.err.println("Initial database connection error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                    System.out.println("Connection closed.");
                } catch (SQLException e) {
                    System.err.println("Error closing connection: " + e.getMessage());
                }
            }
            scanner.close();
        }
    }

    private static boolean databaseExists(Connection conn, String dbName) throws SQLException {
        try (Statement stmt = conn.createStatement();
                java.sql.ResultSet rs = stmt.executeQuery("SELECT 1 FROM pg_database WHERE datname='" + dbName + "'")) {
            return rs.next();
        }
    }

    private static void createDatabase(Connection conn, String dbName) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE " + dbName);
        }
    }

    private static void dropDatabase(Connection conn, String dbName) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP DATABASE " + dbName);
        }
    }
}
