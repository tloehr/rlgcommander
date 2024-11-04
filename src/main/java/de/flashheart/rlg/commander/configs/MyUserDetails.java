package de.flashheart.rlg.commander.configs;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Getter
public class MyUserDetails implements UserDetails {
    private final String username;
    private final String password;
    private final String api_key;
    private Collection<? extends GrantedAuthority> authorities;

    public MyUserDetails(String username, String password, String api_key, Collection<? extends GrantedAuthority> authorities) {
        this.username = username;
        this.password = password;
        this.api_key = api_key;
        this.authorities = authorities;
    }

    public static MyUserDetails build(YamlUser yamlUser) {
        return new MyUserDetails(
                yamlUser.getUsername(),
                yamlUser.getPassword(),
                yamlUser.getApi_key(),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
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
