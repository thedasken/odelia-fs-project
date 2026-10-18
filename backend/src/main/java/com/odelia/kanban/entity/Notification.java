package com.odelia.kanban.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A message for one user, created when something happens to them on a board
 * (added or removed as a member, made owner...).
 *
 * <p>Like {@link BoardMember}, the board is referenced by its identifier.
 * {@code username} is the recipient's {@code preferred_username}.</p>
 */
@Entity
@Table(name = "NOTIFICATION")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "USERNAME", nullable = false, length = 120)
    private String username;

    @Column(name = "BOARD_ID")
    private Long boardId;

    @Column(name = "MESSAGE", nullable = false, length = 255)
    private String message;

    /** "READ" is a keyword in several databases, hence the explicit column name. */
    @Column(name = "IS_READ", nullable = false)
    private boolean read;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    public Notification() {
    }

    public Notification(String username, Long boardId, String message) {
        this.username = username;
        this.boardId = boardId;
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getBoardId() {
        return boardId;
    }

    public void setBoardId(Long boardId) {
        this.boardId = boardId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
