package de.flashheart.rlg.commander.persistence;

import jakarta.transaction.Transactional;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Service
@Log4j2
public class UsersService implements DefaultService<Users> {
    final PasswordEncoder passwordEncoder;
    final UsersRepository usersRepository;
    final RolesService rolesService;
    @Value("${server.locale.default}")
    public String default_locale;

    public UsersService(PasswordEncoder passwordEncoder, UsersRepository usersRepository, RolesService rolesService) {
        this.passwordEncoder = passwordEncoder;
        this.usersRepository = usersRepository;
        this.rolesService = rolesService;
    }

    @Override
    public JpaRepository<Users, Long> getRepository() {
        return usersRepository;
    }

    public Optional<Users> findByUsername(String username) {
        return Optional.ofNullable(usersRepository.findByUsername(username));
    }

    @Transactional
    public void createNew(String username, String password, String... role_names) {
        if (password.isBlank()) password = UUID.randomUUID().toString();
        Users user = createNew();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        save(user);
        // everybody is a user
        rolesService.save(rolesService.createNew(RolesService.USER, user));
        // if additional roles are needed - we'll add them here
        Arrays.stream(role_names).forEach(role_name -> rolesService.save(rolesService.createNew(role_name, user)));
        log.info("Created user: {} with password: {}", username, password);
    }

    @Override
    public Users createNew() {
        Users user = new Users();
        // the API key is always randomized
        user.setApikey(UUID.randomUUID().toString());
        user.setUsername("");
        user.setPassword("");
        user.setLocale(default_locale);
        user.setRoles(new ArrayList<>());
        return user;
    }
}
