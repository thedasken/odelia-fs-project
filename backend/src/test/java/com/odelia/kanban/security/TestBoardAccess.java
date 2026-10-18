package com.odelia.kanban.security;

import com.odelia.kanban.repository.BoardMemberRepository;

/**
 * Lets tests in other packages build a {@link BoardAccess} without CDI: its
 * injected fields are package-private, so they can only be set from here.
 */
public final class TestBoardAccess {

    private TestBoardAccess() {
    }

    public static BoardAccess of(CurrentUser currentUser, BoardMemberRepository members) {
        BoardAccess access = new BoardAccess();
        access.currentUser = currentUser;
        access.members = members;
        return access;
    }
}
