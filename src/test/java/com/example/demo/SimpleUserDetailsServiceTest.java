package com.example.demo;

import com.example.demo.entities.UserEntity;
import com.example.demo.services.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimpleUserDetailsServiceTest {

    @Mock
    private UserService userService;

    private SimpleUserDetailsService createService() {
        return new SimpleUserDetailsService(userService);
    }

    @Test
    void loadUserByUsername_existingUser_returnsUserDetails() {
        var user = new UserEntity();
        user.setEmail("test@example.com");
        user.setAdmin(false);
        when(userService.getByEmail("test@example.com")).thenReturn(Optional.of(user));

        var service = createService();
        var details = service.loadUserByUsername("test@example.com");

        assertEquals("test@example.com", details.getUsername());
        assertEquals("", details.getPassword());
        assertTrue(details.isEnabled());
        assertTrue(details.isAccountNonExpired());
        assertTrue(details.isCredentialsNonExpired());
        assertTrue(details.isAccountNonLocked());
        var authorities = details.getAuthorities().stream()
                .map(Object::toString)
                .toList();
        assertTrue(authorities.contains("ROLE_USER"));
        assertFalse(authorities.contains("ROLE_ADMIN"));
        assertEquals(1, authorities.size());
    }

    @Test
    void loadUserByUsername_adminUser_returnsUserDetailsWithAdminRole() {
        var user = new UserEntity();
        user.setEmail("admin@example.com");
        user.setAdmin(true);
        when(userService.getByEmail("admin@example.com")).thenReturn(Optional.of(user));

        var service = createService();
        var details = service.loadUserByUsername("admin@example.com");

        assertEquals("admin@example.com", details.getUsername());
        var authorities = details.getAuthorities().stream()
                .map(Object::toString)
                .toList();
        assertTrue(authorities.contains("ROLE_USER"));
        assertTrue(authorities.contains("ROLE_ADMIN"));
        assertEquals(2, authorities.size());
    }

    @Test
    void loadUserByUsername_nonExistingUser_throwsUsernameNotFoundException() {
        when(userService.getByEmail("unknown@example.com")).thenReturn(Optional.empty());

        var service = createService();
        var ex = assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("unknown@example.com"));
        assertTrue(ex.getMessage().contains("unknown@example.com"));
    }

    @Test
    void loadUserByUsername_nullEmail_throwsUsernameNotFoundException() {
        when(userService.getByEmail(null)).thenReturn(Optional.empty());

        var service = createService();
        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername(null));
    }

    @Test
    void loadUserByUsername_blankEmail_throwsUsernameNotFoundException() {
        when(userService.getByEmail("   ")).thenReturn(Optional.empty());

        var service = createService();
        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("   "));
    }

    @Test
    void loadUserByUsername_nullUserEmail_handlesGracefully() {
        var user = new UserEntity();
        user.setEmail(null);
        user.setAdmin(false);
        when(userService.getByEmail("null-email@example.com")).thenReturn(Optional.of(user));

        var service = createService();
        assertThrows(Exception.class,
                () -> service.loadUserByUsername("null-email@example.com"));
    }
}
