package com.allinweb.ch.core;

import com.allinweb.ch.persistence.*;
import com.allinweb.ch.util.ABRCallback;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRPropertyEnum;
import com.allinweb.ch.util.ABRPropertyManager;
import java.io.File;
import java.util.*;
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
    private static final String CONNECTION_TYPE = "jdbc:ucanaccess://";
    private static final String CONNECTION_PARAMETERS = ";memory=false;newDatabaseVersion=V2010";
    private static final String lock = "locked";

    private static volatile ABRSharedResources instance;
    private static SessionFactory sessionFactory = null;
    private static Session session = null;

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
            protected Void call() throws Exception {
                new Repository(session).write(entity);
                getEntityList(clazz).add(entity);
                if (callback != null) {
                    callback.execute();
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
        Repository repository = new Repository(session);
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
    }

    public void changeDbConnection() {
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
