package com.example.demo;

import com.example.demo.entities.UserEntity;
import com.example.demo.services.UserService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SimpleUserDetailsService implements UserDetailsService {
    private final UserService userService;

    public SimpleUserDetailsService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        var user = this.userService.getByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "User not found: " + email
                        ));

        return User.withUsername(user.getEmail())
                .password("")
                .authorities(authoritiesFor(user))
                .disabled(false)
                .build();
    }

    private List<SimpleGrantedAuthority> authoritiesFor(UserEntity user) {
        if (user != null && user.isAdmin()) {
            return List.of(
                    new SimpleGrantedAuthority("ROLE_USER"),
                    new SimpleGrantedAuthority("ROLE_ADMIN")
            );
        }

        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

}
