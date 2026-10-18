package com.odelia.kanban.resource;

import com.odelia.kanban.entity.Notification;
import com.odelia.kanban.repository.NotificationRepository;
import com.odelia.kanban.security.CurrentUser;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import java.util.List;

/**
 * The caller's own notifications. A notification belongs to its recipient only:
 * not even an admin reads or changes someone else's.
 */
@Path("/notifications")
@RequestScoped
@RolesAllowed({ CurrentUser.USER, CurrentUser.ADMIN })
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
public class NotificationResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    NotificationRepository notifications;

    @GET
    @Operation(operationId = "getNotifications")
    public List<Notification> listNotifications() {
        return notifications.findByUsernameOrderByCreatedAtDesc(currentUser.name());
    }

    @POST
    @Path("/{id}/read")
    @Transactional
    @Operation(operationId = "markNotificationRead")
    public Response markRead(@PathParam("id") long id) {
        Notification notification = notifications.findById(id).orElseThrow(
                () -> new WebApplicationException("No notification with id " + id, Response.Status.NOT_FOUND));
        if (!currentUser.name().equals(notification.getUsername())) {
            throw new ForbiddenException("Notification " + id + " belongs to someone else");
        }
        notification.setRead(true);
        notifications.save(notification);
        return Response.noContent().build();
    }
}
