package de.flashheart.rlg.commander.configs;

import de.flashheart.rlg.commander.persistence.Users;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

@Getter
public class ApiKeyAuthentication extends AbstractAuthenticationToken {
    private final Users user;

    public ApiKeyAuthentication(Users user, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.user = user;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return user.getUsername();
    }
}
