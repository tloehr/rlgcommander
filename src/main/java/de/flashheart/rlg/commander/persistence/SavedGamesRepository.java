package de.flashheart.rlg.commander.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SavedGamesRepository extends JpaRepository<SavedGames, Long> {
    List<SavedGames> findByPitBetweenOrderByPit(LocalDateTime from, LocalDateTime to);
}
