package com.example.demo;

import com.example.demo.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
public class GenericUserDataController {

    private final GenericUserDataRepository repo;
    private final UserService userService;

    public GenericUserDataController(GenericUserDataRepository repo, UserService userService) {
        this.repo = repo;
        this.userService = userService;
    }

    @GetMapping
    public List<GenericUserData> getData(Authentication authentication) {
        requireAdmin(authentication);
        return repo.findAll();
    }

    @PostMapping
    public GenericUserData addData(@RequestBody GenericUserData body, Authentication authentication) {
        requireAdmin(authentication);
        return repo.save(new GenericUserData(body.getJson()));
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        if (authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        var user = userService.getByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
