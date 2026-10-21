package com.odelia.kanban.repository;

import com.odelia.kanban.entity.BoardMember;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Jakarta Data repository for {@link BoardMember}, queried by method name.
 */
@Repository(dataStore = "jdbc/kanban")
public interface BoardMemberRepository extends CrudRepository<BoardMember, Long> {

    List<BoardMember> findByBoardIdOrderByUsernameAsc(long boardId);

    List<BoardMember> findByUsername(String username);

    Optional<BoardMember> findByBoardIdAndUsername(long boardId, String username);

    boolean existsByBoardIdAndUsername(long boardId, String username);

    void deleteByBoardId(long boardId);
}
