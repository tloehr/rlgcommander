package de.flashheart.rlg.commander.persistence;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsersRepository extends JpaRepository<Users, Long> {
    Optional<Users> findByUsername(String username);
    @Cacheable(value = "apikeyCache", key = "#apikey")
    Optional<Users> findByApikey(String apikey);
}
