package com.odelia.kanban.repository;

import com.odelia.kanban.entity.Notification;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Repository;

import java.util.List;

/**
 * Jakarta Data repository for {@link Notification}, queried by method name.
 */
@Repository(dataStore = "jdbc/kanban")
public interface NotificationRepository extends CrudRepository<Notification, Long> {

    List<Notification> findByUsernameOrderByCreatedAtDesc(String username);
}
