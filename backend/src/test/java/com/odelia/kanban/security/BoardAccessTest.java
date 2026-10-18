package com.odelia.kanban.security;

import com.odelia.kanban.entity.Board;
import com.odelia.kanban.repository.BoardMemberRepository;
import jakarta.ws.rs.ForbiddenException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardAccessTest {

    /** Test double: a CurrentUser with a fixed name and role, no JWT needed. */
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

    private final Board aliceBoard = boardWithId(1L, "Alice's board", "alice");

    private static Board boardWithId(long id, String name, String owner) {
        Board board = new Board(name, owner);
        board.setId(id);
        return board;
    }

    /** Test double: a membership repository where only the given usernames are members of any board. */
    private static BoardMemberRepository membersOf(String... usernames) {
        return (BoardMemberRepository) Proxy.newProxyInstance(
                BoardMemberRepository.class.getClassLoader(),
                new Class<?>[] { BoardMemberRepository.class },
                (proxy, method, args) -> List.of(usernames).contains(args[1]));
    }

    private BoardAccess accessFor(String user, boolean admin, String... memberUsernames) {
        BoardAccess access = new BoardAccess();
        access.currentUser = new StubCurrentUser(user, admin);
        access.members = membersOf(memberUsernames);
        return access;
    }

    @Test
    void ownerCanAccessOwnBoard() {
        assertDoesNotThrow(() -> accessFor("alice", false).requireBoardAccess(aliceBoard));
    }

    @Test
    void otherNonAdminUserIsForbidden() {
        assertThrows(ForbiddenException.class,
                () -> accessFor("bob", false).requireBoardAccess(aliceBoard));
    }

    @Test
    void adminCanAccessAnyBoard() {
        assertDoesNotThrow(() -> accessFor("bob", true).requireBoardAccess(aliceBoard));
    }

    @Test
    void memberCanAccessBoard() {
        assertDoesNotThrow(() -> accessFor("bob", false, "bob").requireBoardAccess(aliceBoard));
    }

    @Test
    void memberCannotActAsOwner() {
        assertThrows(ForbiddenException.class,
                () -> accessFor("bob", false, "bob").requireBoardOwner(aliceBoard));
    }

    @Test
    void ownerAndAdminPassOwnerCheck() {
        assertDoesNotThrow(() -> accessFor("alice", false).requireBoardOwner(aliceBoard));
        assertDoesNotThrow(() -> accessFor("carol", true).requireBoardOwner(aliceBoard));
    }
}
