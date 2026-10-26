
package com.yongdrel.servicetemplate.adapter.in.web;

import com.yongdrel.servicetemplate.application.port.in.UserUseCase;
import com.yongdrel.servicetemplate.domain.model.User;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserUseCase userUseCase;

    public UserController(UserUseCase userUseCase) {
        this.userUseCase = userUseCase;
    }

    @PostMapping
    public ResponseEntity<User> registerUser(@RequestBody User user) {

        User saved = userUseCase.registerUser(user);

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userUseCase.getAllUsers();
    }

    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id) {
        return userUseCase.getUserById(id);
    }
}
