package com.odelia.kanban.security;

import com.odelia.kanban.entity.Board;
import com.odelia.kanban.entity.BoardList;
import com.odelia.kanban.entity.BoardMember;
import com.odelia.kanban.entity.Card;
import com.odelia.kanban.repository.BoardListRepository;
import com.odelia.kanban.repository.BoardMemberRepository;
import com.odelia.kanban.repository.BoardRepository;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

/**
 * Authorization: a board belongs to whoever created it, and only that
 * person - or an {@code admin} - may work on it or on anything inside it.
 *
 * <p>{@code @RolesAllowed} alone only answers "is this a signed-in Kanban
 * user?", which is not enough: every user has the {@code user} role, so without
 * these checks anyone could read someone else's board by guessing its id. The
 * coarse role check and this fine-grained one are complementary.</p>
 *
 * <p>A board can also be shared: its members (see {@link BoardMember}) get
 * collaborator access, which covers the board's columns and cards but not the
 * board itself nor its membership. Two levels therefore exist:
 * {@link #requireBoardAccess} (owner, member or admin) and
 * {@link #requireBoardOwner} (owner or admin).</p>
 *
 * <p>Columns and cards store no owner of their own; they inherit access by
 * walking up to their board.</p>
 */
@RequestScoped
public class BoardAccess {

    @Inject
    CurrentUser currentUser;

    @Inject
    BoardRepository boards;

    @Inject
    BoardListRepository lists;

    @Inject
    BoardMemberRepository members;

    /**
     * Collaborator level: fails with 403 unless the caller owns the board, is a
     * member of it, or is an admin. The membership is read on every call, so a
     * removed member is denied on their very next request.
     */
    public void requireBoardAccess(Board board) {
        if (!currentUser.isAdmin()
                && !isOwner(board)
                && !members.existsByBoardIdAndUsername(board.getId(), currentUser.name())) {
            throw new ForbiddenException("Board " + board.getId() + " belongs to someone else");
        }
    }

    /**
     * Owner level: fails with 403 unless the caller owns the board or is an
     * admin. A member is not enough - this guards renaming, deleting and
     * managing the membership.
     */
    public void requireBoardOwner(Board board) {
        if (!currentUser.isAdmin() && !isOwner(board)) {
            throw new ForbiddenException("Only the owner of board " + board.getId() + " may do this");
        }
    }

    private boolean isOwner(Board board) {
        return currentUser.name().equals(board.getOwner());
    }

    /** The same check, reached through the column's board. */
    public void requireColumnAccess(BoardList column) {
        requireBoardAccess(boardOf(column));
    }

    /** The same check, reached through the card's column and then its board. */
    public void requireCardAccess(Card card) {
        requireColumnAccess(columnOf(card));
    }

    private Board boardOf(BoardList column) {
        return boards.findById(column.getBoardId())
                     .orElseThrow(() -> orphan("column " + column.getId()));
    }

    private BoardList columnOf(Card card) {
        return lists.findById(card.getListId())
                    .orElseThrow(() -> orphan("card " + card.getId()));
    }

    /** With no board to check against, nobody can be shown to own the row: deny. */
    private ForbiddenException orphan(String what) {
        return new ForbiddenException("Cannot establish who owns " + what);
    }
}
