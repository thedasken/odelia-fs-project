package com.odelia.kanban.resource;

import com.odelia.kanban.entity.Board;
import com.odelia.kanban.entity.BoardList;
import com.odelia.kanban.entity.BoardMember;
import com.odelia.kanban.entity.Notification;
import com.odelia.kanban.repository.BoardListRepository;
import com.odelia.kanban.repository.BoardMemberRepository;
import com.odelia.kanban.repository.BoardRepository;
import com.odelia.kanban.repository.CardRepository;
import com.odelia.kanban.repository.NotificationRepository;
import com.odelia.kanban.security.BoardAccess;
import com.odelia.kanban.security.CurrentUser;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CRUD over boards, plus the lists (columns) they contain.
 *
 * <p>Every endpoint needs a valid Keycloak token: {@code @RolesAllowed}
 * turns an anonymous call into a 401 and a token without the {@code user} role
 * into a 403. On top of that a board belongs to the person who created it - the
 * {@code preferred_username} from the JWT - so the listing is filtered and the
 * per-board endpoints go through {@link BoardAccess}.</p>
 *
 * <p>A board can be shared with other users (members). Members may work on its
 * columns and cards; renaming or deleting the board and managing the members
 * stay with the owner (or an admin).</p>
 */
@Path("/boards")
@RequestScoped
@RolesAllowed({ CurrentUser.USER, CurrentUser.ADMIN })
@SecurityRequirement(name = "bearerAuth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BoardResource {

    private static final List<String> DEFAULT_LISTS = List.of("To do", "Doing", "Done");

    @Inject
    CurrentUser currentUser;

    @Inject
    BoardAccess access;

    @Inject
    BoardRepository boards;

    @Inject
    BoardListRepository lists;

    @Inject
    CardRepository cards;

    @Inject
    BoardMemberRepository members;

    @Inject
    NotificationRepository notifications;

    @Context
    UriInfo uriInfo;

    /** The caller's own boards and the boards shared with them - or every board, for an admin. */
    @GET
    @Operation(operationId = "getBoards")
    public List<Board> listBoards() {
        if (currentUser.isAdmin()) {
            return boards.findAllSortedByName();
        }
        List<Board> result = new ArrayList<>(boards.findByOwnerOrderByNameAsc(currentUser.name()));
        List<Long> sharedIds = members.findByUsername(currentUser.name()).stream()
                                      .map(BoardMember::getBoardId)
                                      .toList();
        if (!sharedIds.isEmpty()) {
            result.addAll(boards.findByIdInOrderByNameAsc(sharedIds));
        }
        return result.stream()
                     .collect(Collectors.toMap(Board::getId, b -> b, (a, b) -> a))
                     .values().stream()
                     .sorted(Comparator.comparing(Board::getName))
                     .toList();
    }

    @GET
    @Path("/{id}")
    public Board getBoard(@PathParam("id") long id) {
        return accessibleBoard(id);
    }

    /**
     * Creates a board and seeds it with the three usual columns, so a new board
     * is immediately usable from the UI. The owner always comes from the token:
     * an {@code owner} sent by the client is ignored.
     */
    @POST
    @Transactional
    public Response createBoard(@Valid Board board) {
        board.setId(null);
        board.setCreatedAt(LocalDateTime.now());
        board.setOwner(currentUser.name());
        Board created = boards.insert(board);

        for (int i = 0; i < DEFAULT_LISTS.size(); i++) {
            lists.insert(new BoardList(created.getId(), DEFAULT_LISTS.get(i), i));
        }

        return Response.created(uriInfo.getAbsolutePathBuilder().path(String.valueOf(created.getId())).build())
                       .entity(created)
                       .build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Board updateBoard(@PathParam("id") long id, @Valid Board board) {
        Board existing = ownedBoard(id);
        existing.setName(board.getName());
        return boards.save(existing);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response deleteBoard(@PathParam("id") long id) {
        Board existing = ownedBoard(id);
        members.deleteByBoardId(id);
        for (BoardList column : lists.findByBoardIdOrderByPositionAsc(id)) {
            cards.deleteByListId(column.getId());
        }
        lists.deleteByBoardId(id);
        boards.delete(existing);
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/lists")
    public List<BoardList> listColumns(@PathParam("id") long id) {
        accessibleBoard(id);
        return lists.findByBoardIdOrderByPositionAsc(id);
    }

    @POST
    @Path("/{id}/lists")
    @Transactional
    public Response addColumn(@PathParam("id") long id, @Valid BoardList column) {
        accessibleBoard(id);
        column.setId(null);
        column.setBoardId(id);
        BoardList created = lists.insert(column);
        return Response.created(uriInfo.getBaseUriBuilder().path("lists").path(String.valueOf(created.getId())).build())
                       .entity(created)
                       .build();
    }

    @GET
    @Path("/{id}/members")
    @Operation(operationId = "getBoardMembers")
    public List<BoardMember> listMembers(@PathParam("id") long id) {
        accessibleBoard(id);
        return members.findByBoardIdOrderByUsernameAsc(id);
    }

    @POST
    @Path("/{id}/members")
    @Transactional
    @Operation(operationId = "addBoardMember")
    public Response addMember(@PathParam("id") long id, @Valid AddMemberCommand command) {
        Board board = ownedBoard(id);
        String username = command.username().trim();
        if (username.isEmpty()) {
            throw badRequest("A username is required");
        }
        if (username.equals(board.getOwner())) {
            throw badRequest("The owner of a board cannot be added as a member");
        }
        if (members.existsByBoardIdAndUsername(id, username)) {
            throw badRequest(username + " is already a member of this board");
        }
        BoardMember created = members.insert(new BoardMember(id, username));
        notifyUser(username, board, currentUser.name() + " added you to the board \"" + board.getName() + "\"");
        return Response.created(uriInfo.getAbsolutePathBuilder().path(username).build())
                       .entity(created)
                       .build();
    }

    @DELETE
    @Path("/{id}/members/{username}")
    @Transactional
    @Operation(operationId = "removeBoardMember")
    public Response removeMember(@PathParam("id") long id, @PathParam("username") String username) {
        Board board = ownedBoard(id);
        if (username.equals(board.getOwner())) {
            throw badRequest("The owner of a board is not a member and cannot be removed");
        }
        BoardMember member = members.findByBoardIdAndUsername(id, username)
                                    .orElseThrow(() -> new WebApplicationException(
                                            username + " is not a member of this board",
                                            Response.Status.NOT_FOUND));
        members.delete(member);
        notifyUser(username, board, currentUser.name() + " removed you from the board \"" + board.getName() + "\"");
        return Response.noContent().build();
    }

    /**
     * Hands the board to another user. The previous owner stays on the board as
     * a member, and the new owner stops being one: the owner is never a
     * {@code BoardMember} row.
     *
     * <p>"Transfer to yourself" means the board's current owner as the target:
     * that is a {@code 400}. An admin may hand any board to themselves, in line
     * with an admin being allowed to do anything on any board.</p>
     */
    @POST
    @Path("/{id}/owner")
    @Transactional
    @Operation(operationId = "transferBoardOwnership")
    public Board transferOwnership(@PathParam("id") long id, @Valid TransferOwnerCommand command) {
        Board board = ownedBoard(id);
        String newOwner = command.username().trim();
        if (newOwner.isEmpty()) {
            throw badRequest("A username is required");
        }
        String previousOwner = board.getOwner();
        if (newOwner.equals(previousOwner)) {
            throw badRequest(newOwner + " already owns this board");
        }
        members.findByBoardIdAndUsername(id, newOwner).ifPresent(members::delete);
        if (previousOwner != null && !members.existsByBoardIdAndUsername(id, previousOwner)) {
            members.insert(new BoardMember(id, previousOwner));
        }
        board.setOwner(newOwner);
        Board saved = boards.save(board);
        notifyUser(newOwner, board, currentUser.name() + " made you the owner of the board \"" + board.getName() + "\"");
        notifyUser(previousOwner, board, newOwner + " is now the owner of the board \"" + board.getName() + "\"");
        return saved;
    }

    /** Leaves a notification for a user - never for the caller, who just did the action. */
    private void notifyUser(String username, Board board, String message) {
        if (username != null && !username.equals(currentUser.name())) {
            notifications.insert(new Notification(username, board.getId(), message));
        }
    }

    /** Loads a board the caller is allowed to work on: 404 if it is gone, 403 if they are neither owner, member nor admin. */
    private Board accessibleBoard(long id) {
        Board board = boards.findById(id).orElseThrow(() -> notFound(id));
        access.requireBoardAccess(board);
        return board;
    }

    /** Loads a board only its owner (or an admin) may change: 404 if it is gone, 403 for anyone else, members included. */
    private Board ownedBoard(long id) {
        Board board = boards.findById(id).orElseThrow(() -> notFound(id));
        access.requireBoardOwner(board);
        return board;
    }

    private WebApplicationException badRequest(String message) {
        return new WebApplicationException(message, Response.Status.BAD_REQUEST);
    }

    private WebApplicationException notFound(long id) {
        return new WebApplicationException("No board with id " + id, Response.Status.NOT_FOUND);
    }
}
