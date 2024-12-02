package de.flashheart.rlg.commander.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

@Repository
public interface RolesRepository extends JpaRepository<Roles, Long> {
}
