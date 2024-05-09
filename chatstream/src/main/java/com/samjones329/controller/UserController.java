package com.samjones329.controller;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.samjones329.model.User;
import com.samjones329.repository.UserRepository;
import com.samjones329.service.UserDetailsServiceImpl;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
public class UserController {

    public record RegisterRequest(String username, String email, String password) {
    }

    @Autowired
    UserRepository userRepo;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserDetailsServiceImpl userDetailsService;

    @Autowired
    SecurityContextRepository securityContextRepository;

    Logger logger = LoggerFactory.getLogger(UserController.class);

    @PostMapping("/register")
    public ResponseEntity<SelfResponse> register(@RequestBody RegisterRequest userRequest) {
        try {
            var existingEmail = userRepo.findByEmail(userRequest.email());
            if (existingEmail.isPresent())
                return new ResponseEntity<>(HttpStatus.CONFLICT);
            var existingUsername = userRepo.findByUsername(userRequest.username());
            if (existingUsername.isPresent())
                return new ResponseEntity<>(HttpStatus.CONFLICT);
            // BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            String encodedPassword = passwordEncoder.encode(userRequest.password);
            User user = new User(Uuids.timeBased(), userRequest.username, userRequest.email, encodedPassword,
                    List.of());
            User savedUser = userRepo.save(user);
            return new ResponseEntity<>(
                    new SelfResponse(savedUser.getId(), savedUser.getUsername(), savedUser.getEmail(),
                            savedUser.getServerIds()),
                    HttpStatus.OK);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<SelfResponse> login(@CurrentSecurityContext SecurityContext securityContext,
            @RequestBody LoginRequest loginRequest) {
        try {
            // check user credentials
            Authentication authenticationRequest = UsernamePasswordAuthenticationToken
                    .unauthenticated(loginRequest.email(), loginRequest.password());
            Authentication authenticationResponse = this.authenticationManager.authenticate(authenticationRequest);

            // accessor to HttpServletRequest and HttpServletResponse
            ServletRequestAttributes reqAttr = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes());

            // replace authentication
            securityContext.setAuthentication(authenticationResponse);

            // new security context for thread
            SecurityContextHolder.setContext(securityContext);

            // persist authentication
            securityContextRepository.saveContext(securityContext, reqAttr.getRequest(), reqAttr.getResponse());

            // return user info
            var user = userRepo.findByEmail(loginRequest.email()).get();
            return new ResponseEntity<>(
                    new SelfResponse(user.getId(), user.getUsername(), user.getEmail(), user.getServerIds()),
                    HttpStatus.OK);
        } catch (Exception e) {
            if (e instanceof BadCredentialsException) {
                return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
            }
            logger.error(e.getMessage(), e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public record LoginRequest(String email, String password) {
    }

    public record SelfResponse(UUID id, String username, String email, List<UUID> serverIds) {
    }

    public record UserResponse(UUID id, String username) {
    }

    @GetMapping("/authentication")
    public ResponseEntity<SelfResponse> getAuthentication(
            @CurrentSecurityContext SecurityContext context) {
        var user = userDetailsService.getDetailsFromContext(context).getUser();

        return new ResponseEntity<>(
                new SelfResponse(user.getId(), user.getUsername(), user.getEmail(), user.getServerIds()),
                HttpStatus.OK);
    }

    @GetMapping("/user/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable("id") UUID id) {
        try {
            var user = userRepo.findById(id);
            if (user.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }
            return new ResponseEntity<>(new UserResponse(id, user.get().getUsername()), HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error getting user id=" + id, e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getUsers(@RequestParam List<UUID> ids) {
        try {
            var users = userRepo.findAllById(ids);
            var userResponses = new ArrayList<UserResponse>();
            for (var user : users) {
                userResponses.add(new UserResponse(user.getId(), user.getUsername()));
            }
            return new ResponseEntity<>(userResponses, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error fetching users with ids=" + ids);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
