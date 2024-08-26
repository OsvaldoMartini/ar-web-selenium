package com.allinweb.ch.persistence;

import com.allinweb.ch.driver.ABRWebDriver;
import com.allinweb.ch.util.ABRConstants;
import com.allinweb.ch.util.ABRLogger;
import com.allinweb.ch.util.ABRPropertyEnum;
import com.allinweb.ch.util.ABRPropertyManager;
import java.io.File;
import java.util.List;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.query.Query;

public class Repository {

    private static final String CONNECTION_TYPE = "jdbc:ucanaccess://";
    private static final String CONNECTION_PARAMETERS = ";memory=false;newDatabaseVersion=V2010";

    private Session session;
    private SessionFactory sessionFactory = null;
    private Transaction transaction = null;

    public Repository() {
        openSession();
    }

    private void openSession() {
        if (sessionFactory == null) {
            String dbPath = ABRPropertyManager.getInstance().getProperty(ABRPropertyEnum.FOLDER_PATH_DB);
            File dbFolder = new File(dbPath);
            dbFolder.mkdirs();
            String dbUrl = CONNECTION_TYPE + dbPath + ABRConstants.FILE_NAME_DB + CONNECTION_PARAMETERS;
            sessionFactory = new Configuration()
                    .configure()
                    .setProperty("hibernate.connection.url", dbUrl)
                    .buildSessionFactory();
        }
        if (session == null || !session.isOpen()) {
            session = sessionFactory.openSession();
        }
    }

    public Repository(Session session) {
        this.session = session;
    }

    public <T> void write(T obj) {
        transaction = session.beginTransaction();
        session.save(obj);
        transaction.commit();
    }

    public <T> void update(T obj) {
        transaction = session.beginTransaction();
        session.update(obj);
        transaction.commit();
    }

    public <T> void remove(T obj) {
        try {
            transaction = session.beginTransaction();
            session.flush();
            session.clear();
            session.delete(obj);
            //            session.remove(obj);
            transaction.commit();
        } catch (Exception e) {
            ABRLogger.getInstance(ABRWebDriver.class).severe("Error Repository Remove.\nCause: " + e.getMessage());
        }
    }

    public <T> T findEntityById(Class<T> clazz, int id) {
        return session.get(clazz, id);
    }

    public <T> List<T> findAllEntities(Class<T> clazz) {
        CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
        CriteriaQuery<T> criteriaQuery = criteriaBuilder.createQuery(clazz);
        Root<T> root = criteriaQuery.from(clazz);
        criteriaQuery.select(root);

        Query<T> query = session.createQuery(criteriaQuery);
        return query.getResultList();
    }

    public <T> void refresh(T entity) {
        session.refresh(entity);
    }

    public void closeSession() {
        if (session != null && session.isOpen()) {
            session.close();
        }
    }
}
