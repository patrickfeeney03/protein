package com.example.demo.services;

import com.example.demo.entities.UserEntity;
import com.example.demo.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PersistentTokenRepository persistentTokenRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void findOrCreateGoogleUser_createsNewVerifiedUser() {
        when(userRepository.findByGoogleSub("google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var identity = new GoogleIdentity("google-sub-1", "Person@Example.com", "Person", true);
        var user = userService.findOrCreateGoogleUser(identity);

        assertEquals("person@example.com", user.getEmail());
        assertEquals("Person", user.getName());
        assertEquals("google-sub-1", user.getGoogleSub());
        assertTrue(user.isEmailVerified());
        assertNotNull(user.getEmailVerifiedAt());
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void findOrCreateGoogleUser_fallsBackToEmailWhenNameBlank() {
        when(userRepository.findByGoogleSub("google-sub-2")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("nameless@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var user = userService.findOrCreateGoogleUser(
                new GoogleIdentity("google-sub-2", "Nameless@Example.com", "   ", true));

        assertEquals("nameless@example.com", user.getEmail());
        assertEquals("nameless@example.com", user.getName());
    }

    @Test
    void findOrCreateGoogleUser_linksExistingEmailMatchedUser() {
        var existing = new UserEntity();
        existing.setId(5L);
        existing.setEmail("person@example.com");
        existing.setName("Person");
        existing.setEmailVerified(false);
        when(userRepository.findByGoogleSub("google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        var identity = new GoogleIdentity("google-sub-1", "person@example.com", "Person", true);
        var user = userService.findOrCreateGoogleUser(identity);

        assertSame(existing, user);
        assertEquals("google-sub-1", user.getGoogleSub());
        assertTrue(user.isEmailVerified());
        assertNotNull(user.getEmailVerifiedAt());
        verify(userRepository).save(existing);
    }

    @Test
    void findOrCreateGoogleUser_keepsExistingVerifiedAtWhenAlreadySet() {
        var existing = new UserEntity();
        existing.setId(5L);
        existing.setEmail("person@example.com");
        existing.setEmailVerified(true);
        existing.setEmailVerifiedAt(Instant.parse("2020-01-01T00:00:00Z"));
        when(userRepository.findByGoogleSub("google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        userService.findOrCreateGoogleUser(new GoogleIdentity("google-sub-1", "person@example.com", "Person", true));

        assertEquals(Instant.parse("2020-01-01T00:00:00Z"), existing.getEmailVerifiedAt());
    }

    @Test
    void findOrCreateGoogleUser_returnsExistingUserWhenGoogleSubKnown() {
        var existing = new UserEntity();
        existing.setId(9L);
        existing.setEmail("person@example.com");
        existing.setGoogleSub("google-sub-1");
        when(userRepository.findByGoogleSub("google-sub-1")).thenReturn(Optional.of(existing));

        var user = userService.findOrCreateGoogleUser(
                new GoogleIdentity("google-sub-1", "person@example.com", "Person", true));

        assertSame(existing, user);
        verify(userRepository, never()).save(any());
    }

    @Test
    void findOrCreateGoogleUser_rejectsUnverifiedGoogleEmail() {
        var identity = new GoogleIdentity("google-sub-1", "person@example.com", "Person", false);

        var ex = assertThrows(ResponseStatusException.class, () -> userService.findOrCreateGoogleUser(identity));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verifyNoInteractions(userRepository);
    }

    @Test
    void findOrCreateGoogleUser_rejectsBlankSubject() {
        var identity = new GoogleIdentity("   ", "person@example.com", "Person", true);

        var ex = assertThrows(ResponseStatusException.class, () -> userService.findOrCreateGoogleUser(identity));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verifyNoInteractions(userRepository);
    }

    @Test
    void findOrCreateGoogleUser_rejectsBlankEmail() {
        var identity = new GoogleIdentity("google-sub-1", "   ", "Person", true);

        var ex = assertThrows(ResponseStatusException.class, () -> userService.findOrCreateGoogleUser(identity));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verifyNoInteractions(userRepository);
    }

    @Test
    void findOrCreateGoogleUser_rejectsNullIdentity() {
        var ex = assertThrows(ResponseStatusException.class, () -> userService.findOrCreateGoogleUser(null));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verifyNoInteractions(userRepository);
    }

    @Test
    void findOrCreateGoogleUser_rejectsInvalidEmail() {
        var identity = new GoogleIdentity("google-sub-1", "not-an-email", "Person", true);

        var ex = assertThrows(ResponseStatusException.class, () -> userService.findOrCreateGoogleUser(identity));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(userRepository);
    }

    @Test
    void findOrCreateGoogleUser_rejectsOverlongEmail() {
        var identity = new GoogleIdentity("google-sub-1", "a".repeat(255) + "@example.com", "Person", true);

        var ex = assertThrows(ResponseStatusException.class, () -> userService.findOrCreateGoogleUser(identity));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(userRepository);
    }

    @Test
    void findOrCreateGoogleUser_normalizesEmailCaseAndWhitespace() {
        when(userRepository.findByGoogleSub("google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("mixed@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var user = userService.findOrCreateGoogleUser(
                new GoogleIdentity("google-sub-1", "  MiXeD@Example.com  ", "Mixed", true));

        assertEquals("mixed@example.com", user.getEmail());
        verify(userRepository).findByEmail("mixed@example.com");
        verify(userRepository, never()).findByEmail("  MiXeD@Example.com  ");
    }

    @Test
    void invalidateRememberMeTokens_removesNormalizedTokens() {
        userService.invalidateRememberMeTokens("  User@Example.com  ");

        verify(persistentTokenRepository).removeUserTokens("user@example.com");
    }

    @Test
    void invalidateRememberMeTokens_ignoresBlankEmail() {
        userService.invalidateRememberMeTokens("   ");

        verifyNoInteractions(persistentTokenRepository);
    }

    @Test
    void me_requiresAuthenticatedUser() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        var ex = assertThrows(ResponseStatusException.class, () -> userService.me(9L));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }
}
