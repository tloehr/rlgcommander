package de.flashheart.rlg.commander.persistence;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DefaultService<T extends DefaultEntity> {

    JpaRepository<T, Long> getRepository();

    @Transactional
    default T save(T entity) {
        return getRepository().saveAndFlush(entity);
    }

    @Transactional
    default void delete(T entity) {
        if (entity == null) {
            throw new EntityNotFoundException();
        }
        getRepository().delete(entity);
    }

    @Transactional
    default void delete(long id) {
        delete(load(id));
    }

    @Transactional
    default long count() {
        return getRepository().count();
    }

    @Transactional
    default T load(T t) {
        return load(t.getId());
    }

    @Transactional
    default T load(long id) {
        T entity = getRepository().findById(id).orElse(null);
        if (entity == null) {
            throw new EntityNotFoundException();
        }
        return entity;
    }

    @Transactional
    T createNew();

    // @Transactional(propagation=Propagation.REQUIRED, readOnly=true, noRollbackFor=Exception.class)
}
