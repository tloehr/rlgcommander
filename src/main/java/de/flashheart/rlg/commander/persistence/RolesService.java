package de.flashheart.rlg.commander.persistence;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

@Service
public class RolesService implements DefaultService<Roles> {
    public final static String ADMIN = "ROLE_ADMIN";
    public final static String USER  = "ROLE_USER";

    final RolesRepository rolesRepository;

    public RolesService(RolesRepository rolesRepository) {
        this.rolesRepository = rolesRepository;
    }

    @Override
    public JpaRepository<Roles, Long> getRepository() {
        return rolesRepository;
    }

    @Override
    public Roles createNew() {
        return new Roles();
    }

    @Transactional
    public Roles createNew(String role, Users user) {
        Roles roles = createNew();
        roles.setRole(role);
        roles.setUsers(user);
        user.getRoles().add(roles);
        return roles;
    }
}
