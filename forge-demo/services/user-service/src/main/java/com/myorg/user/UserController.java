package com.myorg.user;

import com.myorg.shared.StringUtils;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final List<User> users = new ArrayList<>();
    
    static {
        users.add(new User(1L, "john.doe", "John Doe", "john@example.com"));
        users.add(new User(2L, "jane.smith", "Jane Smith", "jane@example.com"));
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("service", StringUtils.capitalize("user-service"));
        return response;
    }

    @GetMapping
    public List<User> getAllUsers() {
        return users;
    }

    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id) {
        return users.stream()
                .filter(user -> user.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @PostMapping
    public User createUser(@RequestBody User user) {
        if (StringUtils.isEmpty(user.getUsername())) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        
        user.setId((long) (users.size() + 1));
        user.setDisplayName(StringUtils.capitalize(user.getDisplayName()));
        users.add(user);
        return user;
    }

    public static class User {
        private Long id;
        private String username;
        private String displayName;
        private String email;
        
        public User() {}
        
        public User(Long id, String username, String displayName, String email) {
            this.id = id;
            this.username = username;
            this.displayName = displayName;
            this.email = email;
        }
        
        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }
}