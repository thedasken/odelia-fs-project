package com.odelia.kanban.resource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/boards/{id}/owner}: who the board is handed to.
 *
 * @param username the {@code preferred_username} of the new owner
 */
public record TransferOwnerCommand(@NotBlank @Size(max = 120) String username) {
}
