package de.flashheart.rlg.commander.configs;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MyUserDetailsService implements UserDetailsService {
    private final MyYamlConfiguration myYamlConfiguration;

    public MyUserDetailsService(MyYamlConfiguration myYamlConfiguration) {
        this.myYamlConfiguration = myYamlConfiguration;
    }

    @Override
    public MyUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<YamlUser> optionalMyPreconfiguredUser =
                myYamlConfiguration.getUsers()
                        .stream().filter(yamlUser -> yamlUser.getUsername().equalsIgnoreCase(username))
                        .findFirst();
        if (optionalMyPreconfiguredUser.isEmpty())
            throw new UsernameNotFoundException("User Not Found with username: " + username);

        return MyUserDetails.build(optionalMyPreconfiguredUser.get());
    }
}
