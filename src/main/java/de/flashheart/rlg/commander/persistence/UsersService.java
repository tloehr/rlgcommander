package de.flashheart.rlg.commander.persistence;

import com.github.lalyos.jfiglet.FigletFont;
import com.github.lalyos.jfiglet.JFiglet;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.Get;
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
    private final Get get;
    @Value("${rlgs.admin.set_password:#{null}}")
    public Optional<String> admin_password_to_be_set;
    @Value("${server.locale.default}")
    public String default_locale;

    public UsersService(PasswordEncoder passwordEncoder, UsersRepository usersRepository, RolesService rolesService, Get get) {
        this.passwordEncoder = passwordEncoder;
        this.usersRepository = usersRepository;
        this.rolesService = rolesService;
        this.get = get;
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
        log.debug("Created user: {} with password: {}", username, password);
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
    public void change_locale(Users user, final String new_locale) throws EntityNotFoundException {
        user.setLocale(new_locale);
        save(user);
    }

    /**
     * check if the database is empty, if yes -> this is the first start
     * in this case we need to create a first admin user and write the password to the system log
     * if there is a password set in application.yml this will be used instead
     */
    @Transactional
    public void first_time_run_check() {
        if (usersRepository.count() == 0) {
            String password = this.admin_password_to_be_set.orElse(UUID.randomUUID().toString());
            Users first_admin = createNew("admin", password, RolesService.ADMIN);
            log.info("Created first admin user: {} with password: {}", first_admin.getUsername(), password);
        } else if (this.admin_password_to_be_set.isPresent()) {
            Users admin = getRepository().findByUsername("admin").get();
            admin.setPassword(passwordEncoder.encode(this.admin_password_to_be_set.get()));
            log.info("Setting admin user password: {}", this.admin_password_to_be_set);
            save(admin);
        }
    }
}
