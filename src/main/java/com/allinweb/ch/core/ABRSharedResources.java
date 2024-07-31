package com.allinweb.ch.core;

import com.allinweb.ch.driver.ABRWebDriver;
import com.allinweb.ch.persistence.*;
import com.allinweb.ch.util.ABRCallback;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRLogger;
import com.allinweb.ch.util.ABRPriorities;
import com.allinweb.ch.util.ABRPropertyEnum;
import com.allinweb.ch.util.ABRPropertyManager;
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

public class ABRSharedResources {

    private Repository repository;

    private static final String CONNECTION_TYPE = "jdbc:ucanaccess://";
    private static final String CONNECTION_PARAMETERS = ";memory=false;newDatabaseVersion=V2010";
    private static final String lock = "locked";

    // Postgres
    private static final boolean POSTGRES_DB = false;
    private static final String CONNECTION_POSTGRES = "jdbc:postgresql://";
    private static final String DB_HOST = "localhost"; // or your PostgreSQL server address
    private static final String DB_PORT = "5432"; // default PostgreSQL port
    private static final String DB_NAME = "abr_web"; // your database name
    private static final String USERNAME = "postgres"; // your database username
    private static final String PASSWORD = "martini"; // your database password

    private static volatile ABRSharedResources instance;
    private static SessionFactory sessionFactory = null;
    private static Session session = null;

    private Connection conn = null;
    private List<DatabaseUserDTO> databaseList = new ArrayList<>();
    // Very important sequence on initiation
    private static ABRPriorities abrPriorities;

    // Static block to initialize
    static {
        abrPriorities = ABRPriorities.getInstance();
    }

    private Map<Class<? extends BaseDTO>, ObservableList<? extends BaseDTO>> entityMap;

    public ABRSharedResources() {
        entityMap = new HashMap<>();
        entityMap.put(HomeBankingDTO.class, FXCollections.observableArrayList());
        entityMap.put(BotJobDTO.class, FXCollections.observableArrayList());
        entityMap.put(BlockDTO.class, FXCollections.observableArrayList());
        entityMap.put(BlockLoopInstructionDTO.class, FXCollections.observableArrayList());
        this.entityMap.put(SavedBlocksDTO.class, FXCollections.observableArrayList());
        this.entityMap.put(SavedBlockLoopInstructionDTO.class, FXCollections.observableArrayList());
        this.entityMap.put(SavedInstructionReferenceDTO.class, FXCollections.observableArrayList());
        entityMap.put(InstructionReferenceDTO.class, FXCollections.observableArrayList());
        changeDbConnection();
    }

    private <T extends BaseDTO> void cleanList(Class<T> clazz, Function<T, Boolean> filtering) {
        getEntityList(clazz).stream().filter(filtering::apply).forEach(el -> removeEntitySync(el, clazz, null));
    }

    public static ABRSharedResources getInstance() {
        synchronized (lock) {
            if (instance == null) {
                instance = new ABRSharedResources();
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

    public <T extends BaseDTO> void addAllEntity(Queue<T> entityQueue, Class<T> clazz, ABRCallback callback) {
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

    public <T extends BaseDTO> void addEntity(T entity, Class<T> clazz, ABRCallback callback) {
        Task<Void> executionTask = new Task<>() {
            @Override
            protected Void call() {
                try {
                    new Repository(session).write(entity);
                    getEntityList(clazz).add(entity);
                    if (callback != null) {
                        callback.execute();
                    }
                } catch (NullPointerException e) {
                    // Handle NullPointer exception
                    ABRLogger.getInstance(ABRWebDriver.class).fine("Error Creating JOB \n" + e);
                } catch (UnsupportedOperationException e) {
                    // Handle UnsupportedOperation exception
                    ABRLogger.getInstance(ABRWebDriver.class).fine("Error Creating JOB \n" + e);
                } catch (Exception e) {
                    // Handle any other exceptions
                    ABRLogger.getInstance(ABRWebDriver.class).fine("Error Creating JOB \n" + e);
                }
                return null;
            }
        };
        new Thread(executionTask).start();
    }

    public <T extends BaseDTO> void updateAllEntity(Queue<T> entityQueue, Class<T> clazz) {
        updateAllEntity(entityQueue, clazz, null);
    }

    public <T extends BaseDTO> void updateAllEntity(Queue<T> entityQueue, Class<T> clazz, ABRCallback callback) {
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

    public <T extends BaseDTO> void updateEntity(T entity, Class<T> clazz, ABRCallback callback) {
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

    public <T extends BaseDTO> void refreshAllEntity(Queue<T> entityQueue, Class<T> clazz, ABRCallback callback) {
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

    public <T extends BaseDTO> void refreshEntity(T entity, Class<T> clazz, ABRCallback callback) {
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

    public <T extends BaseDTO> void removeAllEntity(Queue<T> entityQueue, Class<T> clazz, ABRCallback callback) {
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

    public <T extends BaseDTO> void removeEntity(T entity, Class<T> clazz, ABRCallback callback) {
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
            T entity, Class<T> clazz, ABRCallback callback, Consumer<Exception> errorHandler) {
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

    private <T extends BaseDTO> void removeEntitySync(T entity, Class<T> clazz, ABRCallback callback) {
        new Repository(session).remove(entity);
        ObservableList<T> obsList = getEntityList(clazz);
        obsList.remove(entity);
        if (callback != null) {
            callback.execute();
        }
    }

    private void cacheEntitiesFromDB() {

        getEntityList(HomeBankingDTO.class).clear();
        getEntityList(BotJobDTO.class).clear();
        getEntityList(BlockDTO.class).clear();
        getEntityList(BlockLoopInstructionDTO.class).clear();
        this.getEntityList(SavedBlocksDTO.class).clear();
        this.getEntityList(SavedBlockLoopInstructionDTO.class).clear();
        this.getEntityList(SavedInstructionReferenceDTO.class).clear();
        getEntityList(InstructionReferenceDTO.class).clear();
        this.repository = new Repository(session);
        getEntityList(HomeBankingDTO.class).addAll(repository.findAllEntities(HomeBankingDTO.class));
        getEntityList(BotJobDTO.class).addAll(repository.findAllEntities(BotJobDTO.class));
        getEntityList(BlockDTO.class).addAll(repository.findAllEntities(BlockDTO.class));
        getEntityList(BlockLoopInstructionDTO.class).addAll(repository.findAllEntities(BlockLoopInstructionDTO.class));
        this.getEntityList(SavedBlocksDTO.class).addAll(repository.findAllEntities(SavedBlocksDTO.class));
        this.getEntityList(SavedBlockLoopInstructionDTO.class)
                .addAll(repository.findAllEntities(SavedBlockLoopInstructionDTO.class));
        this.getEntityList(SavedInstructionReferenceDTO.class)
                .addAll(repository.findAllEntities(SavedInstructionReferenceDTO.class));
        getEntityList(InstructionReferenceDTO.class).addAll(repository.findAllEntities(InstructionReferenceDTO.class));
        cleanList(BotJobDTO.class, (botJob) -> botJob.getHomeBanking() == null);
        cleanList(BlockDTO.class, (block) -> block.getBotJob() == null);
        cleanList(BlockLoopInstructionDTO.class, (instruction) -> instruction.getBlock() == null);
        cleanList(SavedBlockLoopInstructionDTO.class, (instruction) -> {
            return instruction.getBlock() == null;
        });
        cleanList(InstructionReferenceDTO.class, (ref) -> ref.getBlockLoopInstructionDTO() == null);
        cleanList(SavedInstructionReferenceDTO.class, (ref) -> ref.getSavedBlockLoopInstructionDTO() == null);

        updateDBPriorities();
    }

    private void updateDBPriorities() {
        if (abrPriorities.getAllPriorityList() != null) {
            StringBuilder priorities = new StringBuilder();
            priorities.append("#numero priorità, categoria, identificativo" + System.lineSeparator());
            for (com.allinweb.ch.util.Priority priority : abrPriorities.getAllPriorityList()) {
                priorities.append(priority.getPriorityNumber() + "," + priority.getPriorityType() + ","
                        + priority.getName().stream().findFirst().get() + System.lineSeparator());
            }

            StringBuilder searchCriteria = new StringBuilder();
            searchCriteria.append("1,ByAttribute,test-id" + System.lineSeparator());
            searchCriteria.append(
                    "2,ByChained,By.tagName:input,By.className:mat-mdc-input-element" + System.lineSeparator());
            searchCriteria.append(
                    "3,ByChained,By.xpath://*[contains(@idCOMMA \"mat-input\")]" + System.lineSeparator());
            searchCriteria.append("4,ByTagName,input" + System.lineSeparator());
            searchCriteria.append("5,ByTagName,button" + System.lineSeparator());
            searchCriteria.append("6,ByChained,By.cssSelector:[id^=\"mat-input\"]" + System.lineSeparator());

            // Proxy Example
            String proxyAddress = "#proxy:proxy_address:proxy_port";
            String browserLog = "#browser_log:active";
            String argument1 = "#argument:--disable-infobars";
            String argument2 = "#argument:--disable-dev-shm-usage";
            String argument3 = "#argument:--no-sandbox";
            String systemProps1 = "#systemProps:webdriver.chrome.logfile:logFolder";
            String systemProps2 = "#systemProps:webdriver.chrome.verboseLogging:true";
            StringBuilder optionsConfig = new StringBuilder();
            optionsConfig.append(proxyAddress + System.lineSeparator());
            optionsConfig.append(browserLog + System.lineSeparator());
            optionsConfig.append(argument1 + System.lineSeparator());
            optionsConfig.append(argument2 + System.lineSeparator());
            optionsConfig.append(argument3 + System.lineSeparator());
            optionsConfig.append(systemProps1 + System.lineSeparator());
            optionsConfig.append(systemProps2 + System.lineSeparator());

            updateUserData(priorities.toString(), searchCriteria.toString(), optionsConfig.toString());
        }
    }

    public void changeDbConnection() {
        String priorityPath = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_PRIORITY);

        if (priorityPath != null) {

            if (priorityPath != null && !priorityPath.isBlank()) {
                abrPriorities.loadPriorities();
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
                        .buildSessionFactory();
                session = sessionFactory.openSession();
                cacheEntitiesFromDB();
            } else {

                String dbPath = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_DB);
                if (!dbPath.isBlank()) {
                    File dbFolder = new File(dbPath);
                    dbFolder.mkdirs();
                    String dbUrl = CONNECTION_TYPE + dbPath + ABRConstants.FILE_NAME_DB + CONNECTION_PARAMETERS;
                    sessionFactory = new Configuration()
                            .configure()
                            .setProperty("hibernate.connection.url", dbUrl)
                            .buildSessionFactory();
                    session = sessionFactory.openSession();
                    cacheEntitiesFromDB();
                }
            }
        }
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
                                ABRLogger.getInstance(ABRSharedResources.class).fine("Data updated successfully.");
                            } else {
                                System.out.println("No matching record found to update.");
                                ABRLogger.getInstance(ABRSharedResources.class)
                                        .severe("No matching record found to update:  Id: " + userId);
                            }
                        } catch (SQLException e) {
                            ABRLogger.getInstance(ABRSharedResources.class)
                                    .severe("Error: updateSQL\n" + updateSQL + "\n" + e.getMessage());
                        }
                    } catch (NumberFormatException e) {
                        ABRLogger.getInstance(ABRSharedResources.class)
                                .severe("Error: updateSQL -> Invalid ID format.");
                    }
                }
            }
        }
    }

    private Connection getConnection() {
        if (!POSTGRES_DB) {
            if (conn == null) {
                String dbPath = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_DB);
                String dbUrl = CONNECTION_TYPE + dbPath + ABRConstants.FILE_NAME_DB + CONNECTION_PARAMETERS;
                try {
                    conn = DriverManager.getConnection(dbUrl);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            return conn;
        } else {

            if (conn == null) {
                String dbUrl = CONNECTION_POSTGRES + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
                try {
                    conn = DriverManager.getConnection(dbUrl, USERNAME, PASSWORD);
                } catch (SQLException e) {
                    e.printStackTrace();
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
            e.printStackTrace();
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
            e.printStackTrace();
        }
        return null;
    }
}
