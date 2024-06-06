package com.allinweb.ch.persistence;

import java.util.List;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

public class Repository {

    private Session session;
    private Transaction transaction = null;

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
        transaction = session.beginTransaction();
        session.delete(obj);
        session.remove(obj);
        transaction.commit();
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
}
