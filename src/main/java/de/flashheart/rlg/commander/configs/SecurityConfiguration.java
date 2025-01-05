package de.flashheart.rlg.commander.configs;

import de.flashheart.rlg.commander.persistence.UsersRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = false)
public class SecurityConfiguration {
    private final UsersRepository usersRepository;

    public SecurityConfiguration(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    /**
     *
     * Key Concepts
     * ** Filter Chain in Spring Security
     * *** Spring Security uses a series of filters to handle different aspects of security, such as authentication and authorization.
     * *** Each filter in the chain processes the request and response before passing it to the next filter, forming a chain of responsibilities.
     * ** Custom Filters
     * *** Custom filters can be implemented to handle specific security requirements not covered by the default filters.
     * *** Custom filters are created by implementing the Filter interface from the jakarta.servlet package.
     * ** Adding Filters to the Chain
     * *** Custom filters can be added to the Spring Security filter chain at specific positions relative to existing filters.
     * *** Methods like addFilterBefore(), addFilterAfter(), and addFilterAt() are used to specify the position of the custom filter in the chain.
     * ** Replacing Existing Filters
     * *** Existing filters can be replaced with custom filters to implement alternative authentication or authorization logic.
     * *** The addFilterAt() method is used to place the custom filter at the position of the filter being replaced.
     * ** Implementing Filter Logic
     * *** The core logic of a custom filter is implemented in the doFilter() method, where the filter processes the request and response.
     * *** For example, a custom filter might check for the presence of a specific header or log authentication events.
     * ** Managing Security Context
     * *** The security context holds the details of the authenticated user and is managed using the SecurityContextHolder.
     * *** Different strategies for managing the security context include MODE_THREADLOCAL, MODE_INHERITABLETHREADLOCAL, and MODE_GLOBAL.
     * *** MODE_THREADLOCAL is the default strategy, where each thread has its own security context.
     * *** MODE_INHERITABLETHREADLOCAL allows the security context to be inherited by child threads, useful for asynchronous operations.
     * *** MODE_GLOBAL shares the security context across all threads, suitable for standalone applications.
     * ** SecurityContext and Thread Management
     * *** The SecurityContext stores the authentication details, which can be accessed and managed across different threads.
     * *** For self-managed threads, tools like DelegatingSecurityContextRunnable help propagate the security context.
     *
     * @param http
     * @return
     * @throws Exception
     */

    @Bean
    @Order(1)
    public SecurityFilterChain restFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/**")
                .cors(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests((auth) -> auth.anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(Customizer.withDefaults())
                .addFilterBefore(new ApiKeyAuthenticationFilter(usersRepository), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        // For the Form Login filter chain, you can omit the @SecurityMatcher as it will only be invoked if none of the other matchers apply.
        return http.authorizeHttpRequests((auth) -> {
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
                    logout.deleteCookies("JSESSIONID");
                })
                .rememberMe(rememberMe -> rememberMe.key("uniqueAndSecret"))
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


}
