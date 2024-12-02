package de.flashheart.rlg.commander.misc;

import de.flashheart.rlg.commander.persistence.*;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@Log4j2
public class AppStartupRunner implements ApplicationRunner {
    private final UsersService usersService;

    public AppStartupRunner(UsersService usersService) {
        this.usersService = usersService;
    }

    @Override
    public void run(ApplicationArguments args) {
        // create a default admin user if not present
        // this is for new installations only.
        Optional<Users> opt_admin_user = usersService.findByUsername("admin");
        if (opt_admin_user.isEmpty()) {
            usersService.createNew("admin", UUID.randomUUID().toString(), RolesService.ADMIN);
        }
    }
}
