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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

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
                .authorizeHttpRequests((auth) ->
                        {
                            auth.anyRequest().authenticated();
                        }
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(Customizer.withDefaults())
                //.authenticationManager(restAuthenticationManager())
                .addFilterBefore(new ApiKeyAuthenticationFilter(myYamlConfiguration.getUsers()), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        // For the Form Login filter chain, you can omit the @SecurityMatcher as it will only be invoked if none of the other matchers apply.
        return http
                .authorizeHttpRequests((auth) -> {
//                            auth.requestMatchers("/").permitAll();
                            auth.requestMatchers(new AntPathRequestMatcher("/js/**")).permitAll();
                            auth.requestMatchers(new AntPathRequestMatcher("/webjars/**")).permitAll();
                            auth.requestMatchers(new AntPathRequestMatcher("/img/**")).permitAll();
                            auth.requestMatchers(new AntPathRequestMatcher("/styles/**")).permitAll();
                            // https://stackoverflow.com/questions/32993624/spring-boot-change-locale-on-login-page
                            // to allow lang http parameter on login
                            auth.requestMatchers(new AntPathRequestMatcher("/login/**")).permitAll();
                            auth.anyRequest().authenticated();
                        }
                )
                .formLogin((form) -> form
                        .loginPage("/login")
                        .permitAll()
                )
                .logout((logout) -> {
                    logout.logoutRequestMatcher(new AntPathRequestMatcher("/logout"));
                    logout.invalidateHttpSession(true);
                })
                .build();

    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


}
