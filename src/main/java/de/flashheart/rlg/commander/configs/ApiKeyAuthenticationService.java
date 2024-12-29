package de.flashheart.rlg.commander.configs;

import de.flashheart.rlg.commander.persistence.Roles;
import de.flashheart.rlg.commander.persistence.Users;
import de.flashheart.rlg.commander.persistence.UsersRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

public class ApiKeyAuthenticationService {
    private static final String AUTH_TOKEN_HEADER_NAME = "X-API-KEY";

    public static Authentication getAuthentication(HttpServletRequest request, UsersRepository usersRepository) {
        String apiKey = request.getHeader(AUTH_TOKEN_HEADER_NAME);
        if (apiKey == null) {
            throw new BadCredentialsException("Invalid API Key");
        }
        Optional<Users> optUser = usersRepository.findByApikey(apiKey);
        if (optUser.isEmpty()) {
            throw new BadCredentialsException("Invalid API Key");
        }
        final List<String> authority_list = new ArrayList<>();
        optUser.ifPresent(u -> authority_list.addAll(u.getRoles().stream().map(Roles::getRole).toList()));
        return new ApiKeyAuthentication(optUser.get(), AuthorityUtils.createAuthorityList(authority_list));
    }
}
