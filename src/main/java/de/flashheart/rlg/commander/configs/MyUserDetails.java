package de.flashheart.rlg.commander.configs;

import de.flashheart.rlg.commander.persistence.Users;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Getter
public class MyUserDetails implements UserDetails {
    private final String username;
    private final String password;
    private final String api_key;
    private final String locale;
    private final Collection<? extends GrantedAuthority> authorities;

    private MyUserDetails(String username, String password, String api_key, String locale,Collection<? extends GrantedAuthority> authorities) {
        this.username = username;
        this.password = password;
        this.api_key = api_key;
        this.locale = locale;
        this.authorities = authorities;
    }

    public static MyUserDetails build(Users user) {
        return new MyUserDetails(
                user.getUsername(),
                user.getPassword(),
                user.getApikey(),
                user.getLocale(),
                user.getRoles().stream().map(roles -> new SimpleGrantedAuthority(roles.getRole())).toList()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
