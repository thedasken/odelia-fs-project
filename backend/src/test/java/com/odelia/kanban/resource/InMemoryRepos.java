package com.odelia.kanban.resource;

import com.odelia.kanban.entity.Board;
import com.odelia.kanban.entity.BoardMember;
import com.odelia.kanban.entity.Notification;
import com.odelia.kanban.repository.BoardMemberRepository;
import com.odelia.kanban.repository.BoardRepository;
import com.odelia.kanban.repository.NotificationRepository;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test doubles for the Jakarta Data repositories: plain lists behind a
 * {@link Proxy}, answering only the methods the resources call. No mocking
 * library is needed, and the lists can be inspected after the call.
 */
final class InMemoryRepos {

    final List<Board> boardRows = new ArrayList<>();
    final List<BoardMember> memberRows = new ArrayList<>();
    final List<Notification> notificationRows = new ArrayList<>();

    private final AtomicLong ids = new AtomicLong(1);

    final BoardRepository boards = proxy(BoardRepository.class, (name, args) -> switch (name) {
        case "findById" -> boardRows.stream().filter(b -> b.getId().equals(args[0])).findFirst();
        case "save" -> args[0];
        case "insert" -> {
            Board board = (Board) args[0];
            board.setId(ids.getAndIncrement());
            boardRows.add(board);
            yield board;
        }
        default -> throw unsupported(name);
    });

    final BoardMemberRepository members = proxy(BoardMemberRepository.class, (name, args) -> switch (name) {
        case "findByBoardIdAndUsername" -> memberRows.stream()
                .filter(m -> m.getBoardId() == (long) args[0] && m.getUsername().equals(args[1]))
                .findFirst();
        case "existsByBoardIdAndUsername" -> memberRows.stream()
                .anyMatch(m -> m.getBoardId() == (long) args[0] && m.getUsername().equals(args[1]));
        case "findByBoardIdOrderByUsernameAsc" -> memberRows.stream()
                .filter(m -> m.getBoardId() == (long) args[0])
                .sorted(Comparator.comparing(BoardMember::getUsername))
                .toList();
        case "insert" -> {
            BoardMember member = (BoardMember) args[0];
            member.setId(ids.getAndIncrement());
            memberRows.add(member);
            yield member;
        }
        case "delete" -> memberRows.remove(args[0]);
        default -> throw unsupported(name);
    });

    final NotificationRepository notifications = proxy(NotificationRepository.class, (name, args) -> switch (name) {
        case "findById" -> notificationRows.stream().filter(n -> n.getId().equals(args[0])).findFirst();
        case "findByUsernameOrderByCreatedAtDesc" -> notificationRows.stream()
                .filter(n -> n.getUsername().equals(args[0]))
                .toList();
        case "save" -> args[0];
        case "insert" -> {
            Notification notification = (Notification) args[0];
            notification.setId(ids.getAndIncrement());
            notificationRows.add(notification);
            yield notification;
        }
        default -> throw unsupported(name);
    });

    Board addBoard(String name, String owner) {
        return (Board) boards.insert(new Board(name, owner));
    }

    void addMember(Board board, String username) {
        members.insert(new BoardMember(board.getId(), username));
    }

    List<String> memberNames(Board board) {
        return members.findByBoardIdOrderByUsernameAsc(board.getId()).stream()
                      .map(BoardMember::getUsername)
                      .toList();
    }

    private interface Handler {
        Object handle(String method, Object[] args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (p, method, args) -> {
                    Object result = handler.handle(method.getName(), args == null ? new Object[0] : args);
                    // CrudRepository.findById and the derived finders return Optional/List/boolean already.
                    return result instanceof Optional || result instanceof List || result instanceof Boolean
                            || method.getReturnType() != void.class ? result : null;
                });
    }

    private static UnsupportedOperationException unsupported(String name) {
        return new UnsupportedOperationException("InMemoryRepos does not implement " + name);
    }
}
