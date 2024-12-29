package de.flashheart.rlg.commander.configs;

import de.flashheart.rlg.commander.persistence.Users;
import de.flashheart.rlg.commander.persistence.UsersRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Log4j2
public class MyUserDetailsService implements UserDetailsService {
    UsersRepository usersRepository;
    public MyUserDetailsService(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    @Override
    public MyUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<Users> optUser = usersRepository.findByUsername(username);
        log.debug("login process: searching for '{}'", username);
        if (optUser.isEmpty())
            throw new UsernameNotFoundException("User Not Found with username: " + username);
        log.debug("user found!");

        return MyUserDetails.build(optUser.get());
    }
}
