package com.odelia.kanban.resource;

import com.odelia.kanban.entity.Board;
import com.odelia.kanban.entity.Notification;
import com.odelia.kanban.security.TestBoardAccess;
import com.odelia.kanban.security.CurrentUser;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner/member invariant and notifications of the member and ownership endpoints. */
class BoardOwnershipTransferTest {

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

    private BoardResource resourceFor(String user, boolean admin) {
        StubCurrentUser currentUser = new StubCurrentUser(user, admin);
        BoardResource resource = new BoardResource();
        resource.currentUser = currentUser;
        resource.access = TestBoardAccess.of(currentUser, repos.members);
        resource.boards = repos.boards;
        resource.members = repos.members;
        resource.notifications = repos.notifications;
        resource.uriInfo = (UriInfo) Proxy.newProxyInstance(UriInfo.class.getClassLoader(), new Class<?>[] { UriInfo.class },
                (proxy, method, args) -> UriBuilder.fromUri("http://localhost/api/boards/1/members"));
        return resource;
    }

    private static int statusOf(WebApplicationException e) {
        return e.getResponse().getStatus();
    }

    private List<String> notifiedUsers() {
        return repos.notificationRows.stream().map(Notification::getUsername).toList();
    }

    @Test
    void transferSwapsOwnerAndMemberRows() {
        Board board = repos.addBoard("Sprint", "alice");
        repos.addMember(board, "bob");

        Board result = resourceFor("alice", false)
                .transferOwnership(board.getId(), new TransferOwnerCommand("bob"));

        assertEquals("bob", result.getOwner());
        // The new owner is no longer a member row; the previous owner now is one.
        assertEquals(List.of("alice"), repos.memberNames(board));
    }

    @Test
    void transferToSomeoneWhoIsNotAMemberYetStillKeepsTheInvariant() {
        Board board = repos.addBoard("Sprint", "alice");

        resourceFor("alice", false).transferOwnership(board.getId(), new TransferOwnerCommand("bob"));

        assertEquals("bob", board.getOwner());
        assertEquals(List.of("alice"), repos.memberNames(board));
    }

    @Test
    void transferNotifiesTheNewOwnerButNotTheCaller() {
        Board board = repos.addBoard("Sprint", "alice");

        resourceFor("alice", false).transferOwnership(board.getId(), new TransferOwnerCommand("bob"));

        assertEquals(List.of("bob"), notifiedUsers());
    }

    @Test
    void anAdminTransferNotifiesBothOwners() {
        Board board = repos.addBoard("Sprint", "alice");

        resourceFor("carol", true).transferOwnership(board.getId(), new TransferOwnerCommand("bob"));

        assertEquals(List.of("bob", "alice"), notifiedUsers());
    }

    @Test
    void aMemberCannotTransferTheBoard() {
        Board board = repos.addBoard("Sprint", "alice");
        repos.addMember(board, "bob");

        assertThrows(ForbiddenException.class, () -> resourceFor("bob", false)
                .transferOwnership(board.getId(), new TransferOwnerCommand("bob")));
        assertEquals("alice", board.getOwner());
    }

    @Test
    void transferToTheCurrentOwnerIsRejected() {
        Board board = repos.addBoard("Sprint", "alice");

        WebApplicationException e = assertThrows(WebApplicationException.class, () -> resourceFor("alice", false)
                .transferOwnership(board.getId(), new TransferOwnerCommand("alice")));
        assertEquals(400, statusOf(e));
        assertTrue(repos.memberRows.isEmpty());
    }

    /** "Yourself" is the board's current owner; an admin may take any board, like everywhere else. */
    @Test
    void anAdminMayHandAnyBoardToThemselves() {
        Board board = repos.addBoard("Sprint", "alice");

        resourceFor("carol", true).transferOwnership(board.getId(), new TransferOwnerCommand("carol"));

        assertEquals("carol", board.getOwner());
        assertEquals(List.of("alice"), repos.memberNames(board));
    }

    @Test
    void addingAMemberNotifiesThemAndRejectsTheOwnerAndDuplicates() {
        Board board = repos.addBoard("Sprint", "alice");
        BoardResource alice = resourceFor("alice", false);

        alice.addMember(board.getId(), new AddMemberCommand("bob"));
        assertEquals(List.of("bob"), notifiedUsers());

        assertEquals(400, statusOf(assertThrows(WebApplicationException.class,
                () -> alice.addMember(board.getId(), new AddMemberCommand("bob")))));
        assertEquals(400, statusOf(assertThrows(WebApplicationException.class,
                () -> alice.addMember(board.getId(), new AddMemberCommand("alice")))));
        assertEquals(List.of("bob"), repos.memberNames(board));
    }

    @Test
    void aVeryLongBoardNameAndUsernameStillGiveAStorableNotification() {
        String longName = "n".repeat(120);
        Board board = repos.addBoard(longName, "a".repeat(120));
        BoardResource owner = resourceFor("a".repeat(120), false);

        owner.addMember(board.getId(), new AddMemberCommand("u".repeat(120)));
        owner.removeMember(board.getId(), "u".repeat(120));
        owner.transferOwnership(board.getId(), new TransferOwnerCommand("b".repeat(120)));

        assertEquals(3, repos.notificationRows.size());
        for (Notification n : repos.notificationRows) {
            assertTrue(n.getMessage().length() <= Notification.MESSAGE_MAX, n.getMessage().length() + " chars");
        }
    }
}
