package com.allinweb.ch.core;

import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.persistence.*;
import com.allinweb.ch.util.ARCallback;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ARPriorities;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.common.base.Strings;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javax.persistence.EntityNotFoundException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public class ARSharedResources {

    private Repository repository;

    private static final String CONNECTION_TYPE = "jdbc:ucanaccess://";
    private static final String CONNECTION_PARAMETERS = ";memory=false;newDatabaseVersion=V2010";
    private static final String lock = "locked";

    // Postgres
    private static boolean POSTGRES_DB = false;
    private static final String CONNECTION_POSTGRES = "jdbc:postgresql://";
    private static final String DB_HOST = "localhost"; // or your PostgreSQL server address
    private static final String DB_PORT = "5432"; // default PostgreSQL port
    private static final String DB_NAME = "abr_web"; // your database name
    private static final String USERNAME = "postgres"; // your database username
    private static final String PASSWORD = "martini"; // your database password

    private static volatile ARSharedResources instance;
    private static SessionFactory sessionFactory = null;
    private static Session session = null;

    private Connection conn = null;
    private List<DatabaseUserDTO> databaseList = new ArrayList<>();
    // Very important sequence on initiation
    private static ARPriorities arPriorities;
    private String previousDB;

    public Connection getConn() {
        return conn;
    }

    public void setConn(Connection conn) {
        this.conn = conn;
    }

    // Static block to initialize
    static {
        arPriorities = ARPriorities.getInstance();
    }

    private Map<Class<? extends BaseDTO>, ObservableList<? extends BaseDTO>> entityMap;

    public ARSharedResources() {
        entityMap = new HashMap<>();
        entityMap.put(HomeBankingDTO.class, FXCollections.observableArrayList());
        entityMap.put(BotJobDTO.class, FXCollections.observableArrayList());
        entityMap.put(BlockDTO.class, FXCollections.observableArrayList());
        entityMap.put(InstructionDTO.class, FXCollections.observableArrayList());
        this.entityMap.put(ComponentBlockDTO.class, FXCollections.observableArrayList());
        this.entityMap.put(ComponentInstructionDTO.class, FXCollections.observableArrayList());
        this.entityMap.put(ComponentReferenceDTO.class, FXCollections.observableArrayList());
        entityMap.put(ReferenceDTO.class, FXCollections.observableArrayList());
        changeDbConnection();
    }

    private <T extends BaseDTO> void cleanList(Class<T> clazz, Function<T, Boolean> filtering) {
        getEntityList(clazz).stream().filter(filtering::apply).forEach(el -> removeEntitySync(el, clazz, null));
    }

    public static ARSharedResources getInstance() {
        synchronized (lock) {
            if (instance == null) {
                instance = new ARSharedResources();
            }
        }
        return instance;
    }

    public <T extends BaseDTO> ObservableList<T> getEntityList(
            Class<T> clazz, Comparator<T> comparator, Predicate<T> predicate) {
        ObservableList<T> li = (ObservableList<T>) entityMap.get(clazz);
        if (comparator != null) {
            li = li.sorted(comparator);
        }
        if (predicate != null) {
            li = li.filtered(predicate);
        }
        return li;
    }

    public <T extends BaseDTO> ObservableList<T> getEntityList(Class<T> clazz, Predicate<T> predicate) {
        return getEntityList(clazz, null, predicate);
    }

    public <T extends BaseDTO> ObservableList<T> getEntityList(Class<T> clazz, Comparator<T> comparator) {
        return getEntityList(clazz, comparator, null);
    }

    public <T extends BaseDTO> ObservableList<T> getEntityList(Class<T> clazz) {
        return getEntityList(clazz, null, null);
    }

    public <T extends BaseDTO> T getEntityById(Class<T> clazz, int id) {
        return getEntityList(clazz).filtered((obj) -> obj.getId() == id).stream()
                .findFirst()
                .orElseGet(() -> {
                    T entity = new Repository(session).findEntityById(clazz, id);
                    if (entity != null) {
                        return entity;
                    }
                    throw new EntityNotFoundException("Entity for class " + clazz + " was not found with id: " + id);
                });
    }

    public <T extends BaseDTO> void addAllEntity(Queue<T> entityQueue, Class<T> clazz) {
        addAllEntity(entityQueue, clazz, null);
    }

    public <T extends BaseDTO> void addAllEntity(Queue<T> entityQueue, Class<T> clazz, ARCallback callback) {
        T entity = entityQueue.poll();
        if (entity != null) {
            addEntity(entity, clazz, () -> addAllEntity(entityQueue, clazz, callback));
        } else {
            if (callback != null) {
                Platform.runLater(callback::execute);
            }
        }
    }

    public <T extends BaseDTO> void addEntity(T entity, Class<T> clazz) {
        addEntity(entity, clazz, null);
    }

    public <T extends BaseDTO> void addEntity(T entity, Class<T> clazz, ARCallback callback) {
        Task<Void> executionTask = new Task<>() {
            @Override
            protected Void call() {
                try {
                    if (session.getTransaction().isActive()) {
                        session.getTransaction().rollback(); // Rollback any existing transaction
                    }
                    new Repository(session).write(entity);
                    getEntityList(clazz).add(entity);

                    if (callback != null) {
                        callback.execute();
                    }
                } catch (NullPointerException e) {
                    ARLogger.getInstance(ARWebDriver.class)
                            .severe(String.format("Error Creating %s\n", e.getMessage()));
                    ARLogger.getInstance(Thread.class)
                            .severe(String.format(
                                    "USE \"Platform Runner\" for JavaFX Threads!!!\nError: %s", e.getMessage()));
                } catch (UnsupportedOperationException e) {
                    ARLogger.getInstance(ARWebDriver.class)
                            .severe(String.format("Error Creating %s\n", clazz.getName()));
                    ARLogger.getInstance(Thread.class)
                            .severe(String.format(
                                    "USE \"Platform Runner\" for JavaFX Threads!!!\nError: %s", e.getMessage()));
                } catch (Exception e) {
                    ARLogger.getInstance(ARWebDriver.class)
                            .severe(String.format("Error Creating %s\n", clazz.getName()));
                    ARLogger.getInstance(Thread.class)
                            .severe(String.format(
                                    "USE \"Platform Runner\" for JavaFX Threads!!!\nError: %s", e.getMessage()));
                }
                return null;
            }
        };
        new Thread(executionTask).start();
    }

    public <T extends BaseDTO> void updateAllEntity(Queue<T> entityQueue, Class<T> clazz) {
        updateAllEntity(entityQueue, clazz, null);
    }

    public <T extends BaseDTO> void updateAllEntity(Queue<T> entityQueue, Class<T> clazz, ARCallback callback) {
        T entity = entityQueue.poll();
        if (entity != null) {
            updateEntity(entity, clazz, () -> updateAllEntity(entityQueue, clazz, callback));
        } else {
            if (callback != null) {
                callback.execute();
            }
        }
    }

    public <T extends BaseDTO> void updateEntity(T entity, Class<T> clazz) {
        updateEntity(entity, clazz, null);
    }

    public <T extends BaseDTO> void updateEntity(T entity, Class<T> clazz, ARCallback callback) {
        Task<Void> executionTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                T cachedEntity = getEntityById(clazz, entity.getId());
                ObservableList<T> obsList = getEntityList(clazz);
                new Repository(session).update(entity);
                obsList.remove(cachedEntity);
                obsList.add(entity);
                if (callback != null) {
                    callback.execute();
                }
                return null;
            }
        };
        new Thread(executionTask).start();
    }

    public <T extends BaseDTO> void refreshAllEntity(Queue<T> entityQueue, Class<T> clazz) {
        refreshAllEntity(entityQueue, clazz, null);
    }

    public <T extends BaseDTO> void refreshAllEntity(Queue<T> entityQueue, Class<T> clazz, ARCallback callback) {
        T entity = entityQueue.poll();
        if (entity != null) {
            refreshEntity(entity, clazz, () -> refreshAllEntity(entityQueue, clazz, callback));

        } else {
            if (callback != null) {
                callback.execute();
            }
        }
    }

    public <T extends BaseDTO> void refreshEntity(T entity, Class<T> clazz) {
        refreshEntity(entity, clazz, null);
    }

    public <T extends BaseDTO> void refreshEntity(T entity, Class<T> clazz, ARCallback callback) {
        Task<Void> executionTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                T cachedEntity = getEntityById(clazz, entity.getId());
                new Repository(session).refresh(entity);
                ObservableList<T> obsList = getEntityList(clazz);
                obsList.remove(cachedEntity);
                obsList.add(entity);
                if (callback != null) {
                    callback.execute();
                }
                return null;
            }
        };
        new Thread(executionTask).start();
    }

    public <T extends BaseDTO> void removeAllEntity(Queue<T> entityQueue, Class<T> clazz) {
        removeAllEntity(entityQueue, clazz, null);
    }

    public <T extends BaseDTO> void removeAllEntity(Queue<T> entityQueue, Class<T> clazz, ARCallback callback) {
        T entity = entityQueue.poll();
        if (entity != null) {
            removeEntity(entity, clazz, () -> removeAllEntity(entityQueue, clazz, callback));
        } else {
            if (callback != null) {
                Platform.runLater(callback::execute);
            }
        }
    }

    public <T extends BaseDTO> void removeEntity(T entity, Class<T> clazz) {
        removeEntity(entity, clazz, null);
    }

    public <T extends BaseDTO> void removeEntity(T entity, Class<T> clazz, ARCallback callback) {
        Task<Void> executionTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                removeEntitySync(entity, clazz, callback);
                return null;
            }
        };
        new Thread(executionTask).start();
    }

    public <T extends BaseDTO> void removeEntity(
            T entity, Class<T> clazz, ARCallback callback, Consumer<Exception> errorHandler) {
        Task<Void> executionTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    removeEntitySync(entity, clazz, callback);
                } catch (Exception e) {
                    errorHandler.accept(e); // Call the error handler with the exception
                }
                return null;
            }
        };
        new Thread(executionTask).start();
    }

    private <T extends BaseDTO> void removeEntitySync(T entity, Class<T> clazz, ARCallback callback) {
        try {
            new Repository(session).remove(entity);
            ObservableList<T> obsList = getEntityList(clazz);
            obsList.remove(entity);
            if (callback != null) {
                callback.execute();
            }
        } catch (Exception e) {
            ARLogger.getInstance(ARWebDriver.class).severe("Error removeEntitySync -> Cause: " + e.getMessage());
        }
    }

    public void cacheEntitiesFromDB() {

        getEntityList(HomeBankingDTO.class).clear();
        getEntityList(BotJobDTO.class).clear();
        getEntityList(BlockDTO.class).clear();
        getEntityList(InstructionDTO.class).clear();
        this.getEntityList(ComponentBlockDTO.class).clear();
        this.getEntityList(ComponentInstructionDTO.class).clear();
        this.getEntityList(ComponentReferenceDTO.class).clear();
        getEntityList(ReferenceDTO.class).clear();
        this.repository = new Repository(session);
        getEntityList(HomeBankingDTO.class).addAll(repository.findAllEntities(HomeBankingDTO.class));
        getEntityList(BotJobDTO.class).addAll(repository.findAllEntities(BotJobDTO.class));
        getEntityList(BlockDTO.class).addAll(repository.findAllEntities(BlockDTO.class));
        getEntityList(InstructionDTO.class).addAll(repository.findAllEntities(InstructionDTO.class));
        this.getEntityList(ComponentBlockDTO.class).addAll(repository.findAllEntities(ComponentBlockDTO.class));
        this.getEntityList(ComponentInstructionDTO.class)
                .addAll(repository.findAllEntities(ComponentInstructionDTO.class));
        this.getEntityList(ComponentReferenceDTO.class).addAll(repository.findAllEntities(ComponentReferenceDTO.class));
        getEntityList(ReferenceDTO.class).addAll(repository.findAllEntities(ReferenceDTO.class));
        try {

            cleanList(BotJobDTO.class, (botJob) -> botJob.getHomeBanking() == null);

            cleanList(BlockDTO.class, (block) -> block.getBotJobDTO() == null);
            cleanList(InstructionDTO.class, (instruction) -> instruction.getBlock() == null);
            cleanList(ComponentInstructionDTO.class, (instruction) -> {
                return instruction.getBlock() == null;
            });
            cleanList(ReferenceDTO.class, (ref) -> ref.getBlockLoopInstructionDTO() == null);
            cleanList(ComponentReferenceDTO.class, (ref) -> ref.getSavedBlockLoopInstructionDTO() == null);
        } catch (Exception e) {
            ARLogger.getInstance(ARWebDriver.class)
                    .severe("Many Entities Still Open In Threads\n" + "Wait to get to finish.\nError: "
                            + e.getMessage());
        }
        updateDBPriorities();
    }

    private void updateDBPriorities() {
        if (arPriorities.getAllPriorityList() != null) {
            StringBuilder priorities = new StringBuilder();
            priorities.append("#numero priorità, categoria, identificativo" + System.lineSeparator());
            for (com.allinweb.ch.util.Priority priority : arPriorities.getAllPriorityList()) {
                priorities.append(priority.getPriorityNumber() + "," + priority.getPriorityType() + ","
                        + priority.getName().stream().findFirst().get() + System.lineSeparator());
            }

            StringBuilder searchCriteria = new StringBuilder();
            searchCriteria.append("1,ByAttribute,test-id" + System.lineSeparator());
            //            searchCriteria.append(
            //                    "2,ByChained,By.tagName:input,By.className:mat-mdc-input-element" +
            // System.lineSeparator());
            //            searchCriteria.append(
            //                    "3,ByChained,By.xpath://*[contains(@idCOMMA \"mat-input\")]" +
            // System.lineSeparator());
            //            searchCriteria.append("4,ByTagName,input" + System.lineSeparator());
            //            searchCriteria.append("5,ByTagName,button" + System.lineSeparator());
            //            searchCriteria.append("6,ByChained,By.cssSelector:[id^=\"mat-input\"]" +
            // System.lineSeparator());

            // Proxy Example
            // Proxy Example
            String argument1 = "arg:-disable-web-security";
            String argument2 = "arg:-disable-site-isolation-trials";
            String argument3 = "arg:-allow-running-insecure-content";
            String argument4 = "arg:-disable-features=IsolateOrigins,site-per-process";
            String argument5 = "#arg:-disable-infobars";
            String argument6 = "#arg:-disable-dev-shm-usage";
            String proxyAddress = "#proxy:proxy_address:proxy_port";
            //            String browserLog = "#browser_log:active";
            //            String systemProps1 = "#systemProps:webdriver.chrome.logfile:logFolder";
            //            String systemProps2 = "#systemProps:webdriver.chrome.verboseLogging:true";
            StringBuilder optionsConfig = new StringBuilder();
            optionsConfig.append(argument1 + System.lineSeparator());
            optionsConfig.append(argument2 + System.lineSeparator());
            optionsConfig.append(argument3 + System.lineSeparator());
            optionsConfig.append(argument4 + System.lineSeparator());
            optionsConfig.append(argument5 + System.lineSeparator());
            optionsConfig.append(argument6 + System.lineSeparator());
            optionsConfig.append(proxyAddress + System.lineSeparator());
            //            optionsConfig.append(browserLog + System.lineSeparator());
            //            optionsConfig.append(systemProps1 + System.lineSeparator());
            //            optionsConfig.append(systemProps2 + System.lineSeparator());

            updateUserData(priorities.toString(), searchCriteria.toString(), optionsConfig.toString());
        }
    }

    public void closeConnection() {
        if (this.conn != null) {
            try {
                this.conn.close();
                this.conn = null; // Reset the connection to null after closing
            } catch (SQLException e) {
                System.out.println(e.getMessage()); // Handle the exception, log it or rethrow it as needed
            }
        }
    }

    public void changeDbConnection() {
        //        String priorityPath =
        // ARPropertyManager.getInstance().getProperty(ARPropertyEnum.FOLDER_PATH_PRIORITY);
        //
        //        if (priorityPath != null) {
        //
        //            if (priorityPath != null && !priorityPath.isBlank()) {
        //                arPriorities.loadPriorities();
        //            }

        String dataBaseType = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.DATABASE_TYPE);
        if (previousDB != null && previousDB != dataBaseType) {
            closeConnection();
            previousDB = dataBaseType;
        } else {
            previousDB = dataBaseType;
        }

        if (dataBaseType != null && dataBaseType.equalsIgnoreCase("POSTGRES")) {
            POSTGRES_DB = true;
        } else {
            POSTGRES_DB = false;
        }

        if (POSTGRES_DB) {
            String dbUrl = CONNECTION_POSTGRES + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
            sessionFactory = new Configuration()
                    .configure()
                    .setProperty("hibernate.connection.url", dbUrl)
                    .setProperty("hibernate.connection.username", USERNAME)
                    .setProperty("hibernate.connection.password", PASSWORD)
                    .setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                    .setProperty("hibernate.connection.driver_class", "org.postgresql.Driver")
                    //                        .setProperty("hibernate.hbm2ddl.auto", "update")
                    .buildSessionFactory();
            session = sessionFactory.openSession();
            cacheEntitiesFromDB();
        } else {

            String dbPath = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.FOLDER_PATH_DB);
            if (!dbPath.isBlank()) {
                File dbFolder = new File(dbPath);
                dbFolder.mkdirs();
                String dbUrl = CONNECTION_TYPE + dbPath + ARConstants.FILE_NAME_DB + CONNECTION_PARAMETERS;
                sessionFactory = new Configuration()
                        .configure()
                        .setProperty("hibernate.connection.url", dbUrl)
                        //                            .setProperty("hibernate.hbm2ddl.auto", "update")
                        .buildSessionFactory();
                session = sessionFactory.openSession();
                cacheEntitiesFromDB();
            }
        }
        //        }
    }

    private void updateUserData(String priority, String searchConfig, String optionsConfig) {
        if (getConnection() != null) {
            loadUserData();
            for (DatabaseUserDTO userDTO : databaseList) {
                boolean updateNeeded = false;
                String updateSQL = "UPDATE home_banking SET ";

                if (Strings.isNullOrEmpty(userDTO.getPriority())
                        && Strings.isNullOrEmpty(userDTO.getSearchConfig())
                        && Strings.isNullOrEmpty(userDTO.getOptionsConfig())) {
                    updateSQL += " Priority = '" + priority + "', " + " search_config = '" + searchConfig + "', "
                            + " options_config = '" + optionsConfig + "' ";
                    updateNeeded = true;
                } else if (Strings.isNullOrEmpty(userDTO.getPriority())) {
                    updateSQL += " Priority = '" + priority + "'";
                    updateNeeded = true;
                } else if (Strings.isNullOrEmpty(userDTO.getSearchConfig())) {
                    updateSQL += " search_config = '" + searchConfig + "'";
                    updateNeeded = true;
                } else if (Strings.isNullOrEmpty(userDTO.getOptionsConfig())) {
                    updateSQL += " options_config = '" + optionsConfig + "'";
                    updateNeeded = true;
                }

                if (updateNeeded) {
                    int userId = Integer.parseInt(userDTO.getId());
                    try {
                        updateSQL += " WHERE ID = " + userId;
                        try (Statement stmt = getConnection().createStatement()) {
                            int rowsAffected = stmt.executeUpdate(updateSQL);
                            if (rowsAffected > 0) {
                                System.out.println("Data updated successfully.");
                                ARLogger.getInstance(ARSharedResources.class).fine("Data updated successfully.");
                            } else {
                                System.out.println("No matching record found to update.");
                                ARLogger.getInstance(ARSharedResources.class)
                                        .severe("No matching record found to update:  Id: " + userId);
                            }
                        } catch (SQLException e) {
                            ARLogger.getInstance(ARSharedResources.class)
                                    .severe("Error: updateSQL\n" + updateSQL + "\n" + e.getMessage());
                        }
                    } catch (NumberFormatException e) {
                        ARLogger.getInstance(ARSharedResources.class).severe("Error: updateSQL -> Invalid ID format.");
                    }
                }
            }
        }
    }

    public Connection getConnection() {
        if (!POSTGRES_DB) {
            if (conn == null) {
                String dbPath = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.FOLDER_PATH_DB);
                String dbUrl = CONNECTION_TYPE + dbPath + ARConstants.FILE_NAME_DB + CONNECTION_PARAMETERS;
                try {
                    conn = DriverManager.getConnection(dbUrl);
                } catch (SQLException e) {
                    System.out.println(e.getMessage());
                }
            }
            return conn;
        } else {

            if (conn == null) {
                String dbUrl = CONNECTION_POSTGRES + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
                try {
                    conn = DriverManager.getConnection(dbUrl, USERNAME, PASSWORD);
                } catch (SQLException e) {
                    System.out.println(e.getMessage());
                }
            }
            return conn;
        }
    }

    private void loadUserData() {
        databaseList.clear();
        String selectSQL =
                " SELECT bank.ID, bank.Name, Url, bank.priority, COUNT(bot.ID) Jobs, search_config searchConfig, options_config optionsConfig, username, password "
                        + " FROM home_banking bank "
                        + " left join bot_job bot on bot.home_banking_id = bank.id "
                        + " group by bank.ID, bank.Name, bank.Url, bank.priority, bank.search_config, bank.options_config, bank.username, bank.password ";
        try (Statement stmt = getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                String id = rs.getString("ID");
                String jobs = rs.getString("Jobs");
                String name = rs.getString("Name");
                String url = rs.getString("Url");
                String priority = rs.getString("Priority");
                String searchConfig = rs.getString("searchConfig");
                String optionsConfig = rs.getString("optionsConfig");
                String username = rs.getString("username");
                String password = rs.getString("password");
                databaseList.add(new DatabaseUserDTO(
                        id, jobs, name, url, priority, searchConfig, optionsConfig, username, password));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        //        jobUserList.clear();
        //        loadBotJobData();
    }

    public List<ConfigUserDTO> loadUserConfig() {
        List<ConfigUserDTO> configList = new ArrayList<>();

        String selectSQL = " SELECT ID,  pathJava,  logLevel, pathDB,  interactionTimeoutSec, \n"
                + " pathLog,  defaultInstructionStopSeconds, \n"
                + " pathReport,  browser,  pageUpdateTimeoutSec, \n"
                + " pathPriority, pathEngine, pathExcel, pathJavaFx "
                + " FROM configuration ";
        try (Statement stmt = getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {

            while (rs.next()) {
                String id = rs.getString("ID");
                String pathJava = rs.getString("pathJava");
                String logLevel = rs.getString("logLevel");
                String pathDB = rs.getString("pathDB");
                String interactionTimeoutSec = rs.getString("interactionTimeoutSec");
                String pathLog = rs.getString("pathLog");
                String defaultInstructionStopSeconds = rs.getString("defaultInstructionStopSeconds");
                String pathReport = rs.getString("pathReport");
                String browser = rs.getString("browser");
                String pageUpdateTimeoutSec = rs.getString("pageUpdateTimeoutSec");
                String pathPriority = rs.getString("pathPriority");
                String pathEngine = rs.getString("pathEngine");
                String pathExcel = rs.getString("pathExcel");
                String pathJavaFx = rs.getString("pathJavaFx");
                configList.add(new ConfigUserDTO(
                        id,
                        pathJava,
                        logLevel,
                        pathDB,
                        interactionTimeoutSec,
                        pathLog,
                        defaultInstructionStopSeconds,
                        pathReport,
                        browser,
                        pageUpdateTimeoutSec,
                        pathPriority,
                        pathEngine,
                        pathExcel,
                        pathJavaFx));
            }
            return configList;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    public Session getSession() {
        if (session == null || !session.isOpen()) {
            session = sessionFactory.openSession();
        }
        return session;
    }
}
