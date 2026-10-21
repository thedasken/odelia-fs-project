package com.odelia.kanban.resource;

import com.odelia.kanban.entity.Notification;
import com.odelia.kanban.security.CurrentUser;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A notification is visible to, and changeable by, its recipient only. */
class NotificationResourceTest {

    static class StubCurrentUser extends CurrentUser {
        private final String name;
        private final boolean admin;

        StubCurrentUser(String name, boolean admin) {
            this.name = name;
            this.admin = admin;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean isAdmin() {
            return admin;
        }
    }

    private final InMemoryRepos repos = new InMemoryRepos();

    private NotificationResource resourceFor(String user, boolean admin) {
        NotificationResource resource = new NotificationResource();
        resource.currentUser = new StubCurrentUser(user, admin);
        resource.notifications = repos.notifications;
        return resource;
    }

    private Notification notificationFor(String username) {
        return repos.notifications.insert(new Notification(username, 1L, "hello " + username));
    }

    @Test
    void listReturnsOnlyTheCallersNotifications() {
        notificationFor("bob");
        notificationFor("alice");

        List<Notification> forBob = resourceFor("bob", false).listNotifications();

        assertEquals(1, forBob.size());
        assertEquals("bob", forBob.get(0).getUsername());
    }

    @Test
    void anAdminDoesNotSeeSomeoneElsesNotifications() {
        notificationFor("bob");

        assertTrue(resourceFor("carol", true).listNotifications().isEmpty());
    }

    @Test
    void theRecipientMarksTheirNotificationAsRead() {
        Notification notification = notificationFor("bob");

        assertEquals(204, resourceFor("bob", false).markRead(notification.getId()).getStatus());
        assertTrue(notification.isRead());
    }

    @Test
    void nobodyElseMarksItAsReadNotEvenAnAdmin() {
        Notification notification = notificationFor("bob");

        assertThrows(ForbiddenException.class, () -> resourceFor("alice", false).markRead(notification.getId()));
        assertThrows(ForbiddenException.class, () -> resourceFor("carol", true).markRead(notification.getId()));
        assertFalse(notification.isRead());
    }

    @Test
    void anUnknownNotificationIsNotFound() {
        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> resourceFor("bob", false).markRead(999));
        assertEquals(404, e.getResponse().getStatus());
    }
}
