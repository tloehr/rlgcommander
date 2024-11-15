package de.flashheart.rlg.commander.persistence;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsersRepository extends JpaRepository<Users, Long> {
    Users findByUsername(String username);
    @Cacheable(value = "apikeyCache", key = "#apikey")
    Users findByApikey(String apikey);
}
