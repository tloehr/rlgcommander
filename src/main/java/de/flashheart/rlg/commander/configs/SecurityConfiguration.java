package de.flashheart.rlg.commander.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private final MyYamlConfiguration myYamlConfiguration;

    public SecurityConfiguration(MyYamlConfiguration myYamlConfiguration) {
        this.myYamlConfiguration = myYamlConfiguration;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain restFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/**")
                .cors(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(requests ->
                        requests.anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(Customizer.withDefaults())
                //.authenticationManager(restAuthenticationManager())
                .addFilterBefore(new ApiKeyAuthenticationFilter(myYamlConfiguration.getApi_keys()), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        // For the Form Login filter chain, you can omit the @SecurityMatcher as it will only be invoked if none of the other matchers apply.
        return http
                .authorizeHttpRequests((requests) -> {
                            requests.requestMatchers("/").permitAll();
                            requests.requestMatchers(new AntPathRequestMatcher("/js/**")).permitAll();
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




//    @Bean
//    public InMemoryUserDetailsManager userDetailsService() {
//        myYamlConfiguration.getUsers().forEach(myWebUsers ->
//                users.add(new MyUserDetails(myWebUsers.getUsername(),
//                        passwordEncoder().encode(myWebUsers.getPassword()),
//                        authorities, myWebUsers.getApi_key())
//                )
//        );
//        return new InMemoryUserDetailsManager(users);
//    }

    // different authentication manager, because rest has its own passwords
//    private AuthenticationManager restAuthenticationManager() {
//        UserDetailsService userDetailsService = restUserDetailsService();
//
//        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
//        authenticationProvider.setPasswordEncoder(passwordEncoder());
//        authenticationProvider.setUserDetailsService(userDetailsService);
//
//        return new ProviderManager(authenticationProvider);
//    }
//
//    private UserDetailsService restUserDetailsService() {
//        UserDetails user = User.withUsername("user")
//                .password(passwordEncoder().encode("password2"))
//                .roles("USER")
//                .build();
//        return new InMemoryUserDetailsManager(user);
//    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


}
