# DatabaseConnectorPostgres

This Java application provides a command-line interface to connect to, create, or drop PostgreSQL databases.

## Prerequisites

* **Java Development Kit (JDK):** Make sure you have the JDK installed and configured on your system.
* **PostgreSQL JDBC Driver:** You'll need the PostgreSQL JDBC driver JAR file. Download it from the Maven repository or your preferred source.

## Instructions

1.  **Save the Java Code:**
  * Save the provided Java code as `DatabaseConnectorPostgres.java` in a directory of your choice.

2.  **Compile the Java Code:**
  * Open a command prompt or terminal.
  * Navigate to the directory where you saved `DatabaseConnectorPostgres.java`.
  * Compile the Java file using the following command:

      ```bash
      javac DatabaseConnectorPostgres.java
      ```

    This will generate a `DatabaseConnectorPostgres.class` file.

3.  **Run the Application:**
  * To run the application, use the following command. If the postgresql jar is in the same folder as the .class file.

      ```bash
      java DatabaseConnectorPostgres
      ```

  * If the PostgreSQL JDBC driver JAR is located in a different directory, you need to include it in the classpath:

      ```bash
    javac -cp "resources/postgresql-42.7.3.jar;." DatabaseConnectorPostgres.java #Windows
    javac -cp "resources/postgresql-42.7.3.jar:." DatabaseConnectorPostgres.java #Linux/Mac
      ```

    * Replace `postgresql-42.7.5.jar` with the actual name of your JDBC driver JAR file.
    * Adjust the classpath separator (`;` for Windows, `:` for Linux/Mac) accordingly.

4.  **Follow the Prompts:**
  * The application will prompt you for the following information:
    * Postgres Host (e.g., `localhost`)
    * Postgres Port (e.g., `5432`, or press Enter for default)
    * Postgres Database Name
    * Postgres Username
    * Postgres Password
  * After providing the connection details, you'll be presented with a menu of actions:
    * `1. Connect to database`
    * `2. Create database`
    * `3. Drop database`
    * `4. Change database name`
    * `5. Exit`
  * Choose the desired action by entering the corresponding number.

5.  **Database Operations:**
  * If you choose to connect to a database, the application will establish a connection. You can then use an SQL client to perform queries.
  * If you choose to create or drop a database, the application will execute the corresponding SQL command.

6.  **Error Handling:**
  * The application includes basic error handling for database connection and operation failures.
  * Error messages will be printed to the console.

7.  **Exit:**
  * To exit the application, choose option `5` from the menu.

## Security Considerations

* **Password Security:** Avoid storing database passwords directly in your code. Consider using environment variables or other secure methods for managing credentials.
* **Command-Line Security:** Be cautious when entering passwords in the command line, as they might be visible in your shell history.