package de.flashheart.rlg.commander.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    @Order(1)
    public SecurityFilterChain restFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/**")
                .authorizeHttpRequests((requests) -> requests.anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(withDefaults())
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        // For the Form Login filter chain, you can omit the @SecurityMatcher as it will only be invoked if none of the other matchers apply.
        return http
                .authorizeHttpRequests((requests) -> {
                            requests.requestMatchers("/").permitAll();
                            requests.requestMatchers(new AntPathRequestMatcher("/webjars/**")).permitAll();
                            requests.requestMatchers(new AntPathRequestMatcher("/img/**")).permitAll();
                            requests.requestMatchers(new AntPathRequestMatcher("/styles/**")).permitAll();
                            requests.requestMatchers("/error").permitAll();
                            requests.anyRequest().authenticated();
                        }
                )
                .formLogin(withDefaults())
                .build();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService() {
        UserDetails user = User.withDefaultPasswordEncoder()
                .username("user")
                .password("password")
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

}
