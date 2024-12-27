package de.flashheart.rlg.commander.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

public interface SavedGamesRepository extends JpaRepository<SavedGames, Long> {
    List<SavedGames> findByPitBetweenOrderByPit(LocalDateTime from, LocalDateTime to);

    @Query("SELECT sd FROM SavedGames sd WHERE sd.mode = ?1 AND sd.defaults = true")
    Optional<SavedGames> findDefaultFor(String mode);
}
