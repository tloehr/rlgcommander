package de.flashheart.rlg.commander.persistence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UsersService implements DefaultService<Users> {
    UsersRepository usersRepository;
    @Value("${server.locale.default}")
    public String default_locale;

    public UsersService(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    @Override
    public JpaRepository<Users, Long> getRepository() {
        return usersRepository;
    }
//
//    public List<String> find_all_api_keys() {
//        return usersRepository.findAll().stream().map(Users::getApikey).toList();
//    }

    @Override
    public Users createNew() {
        Users user = new Users();
        user.setApikey(UUID.randomUUID().toString());
        user.setPassword("");
        user.setPassword("");
        user.setLocale(default_locale);
        return user;
    }
}
