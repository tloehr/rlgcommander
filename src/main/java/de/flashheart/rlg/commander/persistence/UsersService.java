package de.flashheart.rlg.commander.persistence;

import com.github.lalyos.jfiglet.FigletFont;
import com.github.lalyos.jfiglet.JFiglet;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
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
    public UsersRepository getRepository() {
        return usersRepository;
    }

    @Transactional
    public Users createNew(String username, String password, String... role_names) {
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
        save(user);
        return user;
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

    @Transactional
    public void set_password(Users user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        save(user);
    }

    @Transactional
    @CacheEvict(value = "apikeyCache", allEntries = true)
    public void create_new_api_key(Users user) {
        user.setApikey(UUID.randomUUID().toString());
        save(user);
    }

    @Transactional
    public void toggle_role(long user_pk, String role_name) throws EntityNotFoundException {
        usersRepository.findById(user_pk)
                .ifPresent(user -> {
                    rolesService.toggle_role(role_name, user);
                    save(user);
                });
    }

    @Transactional
    public void change_locale(long user_pk, final String new_locale) throws EntityNotFoundException {
        Optional<Users> optionalUsers = usersRepository.findById(user_pk);
        if (optionalUsers.isEmpty()) {
            throw new EntityNotFoundException(String.format("User with id %d was not found", user_pk));
        }
        optionalUsers.get().setLocale(new_locale);
        save(optionalUsers.get());
    }


    /**
     *  check if the database is empty, if yes -> this is the first start
     *  in this case we need to create a first admin user and write the password to the system log
     */
    @Transactional
    public void first_time_run_check() {
        // there are already users. Nothing to do.
        if (usersRepository.count() > 0) return;
        String password = UUID.randomUUID().toString();
        Users first_admin  = createNew("admin", password, RolesService.ADMIN);
        log.info("Created first admin user: {} with password: {}", first_admin.getUsername(), password);
    }

}
