package io.sustc.controller;

import io.sustc.dto.*;
import io.sustc.service.UserService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserService userService;

    // 1. 注册
    @PostMapping("/register")
    public ResponseEntity<Long> register(@RequestBody RegisterUserReq req) {
        long id = userService.register(req);
        return id != -1 ? ResponseEntity.ok(id) : ResponseEntity.badRequest().build();
    }

    // 2. 登录
    @PostMapping("/login")
    public ResponseEntity<Long> login(@RequestBody AuthInfo auth) {
        long id = userService.login(auth);
        return id != -1 ? ResponseEntity.ok(id) : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    // 3. 删除账号
    @DeleteMapping("/{userId}")
    public ResponseEntity<Boolean> deleteAccount(
            @PathVariable long userId,
            @RequestBody AuthInfo auth
    ) {
        try {
            boolean success = userService.deleteAccount(auth, userId);
            return ResponseEntity.ok(success);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 4. 关注/取关
    @PostMapping("/follow/{followeeId}")
    public ResponseEntity<Boolean> follow(
            @PathVariable long followeeId,
            @RequestBody AuthInfo auth
    ) {
        try {
            boolean result = userService.follow(auth, followeeId);
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(false);
        }
    }

    // 5. 获取用户信息
    @GetMapping("/{userId}")
    public ResponseEntity<UserRecord> getById(@PathVariable long userId) {
        UserRecord user = userService.getById(userId);
        return user != null ? ResponseEntity.ok(user) : ResponseEntity.notFound().build();
    }

    // 6. 更新个人信息
    @PutMapping("/profile")
    public ResponseEntity<Void> updateProfile(@RequestBody UpdateProfileReq req) {
        try {
            userService.updateProfile(req.getAuth(), req.getGender(), req.getAge());
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    // 7. Feed 流
    @PostMapping("/feed")
    public ResponseEntity<PageResult<FeedItem>> feed(@RequestBody FeedReq req) {
        try {
            return ResponseEntity.ok(userService.feed(req.getAuth(), req.getPage(), req.getSize(), req.getCategory()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // 8. 粉丝比例最高用户
    @GetMapping("/stats/highest-follow-ratio")
    public ResponseEntity<Map<String, Object>> getUserWithHighestFollowRatio() {
        return ResponseEntity.ok(userService.getUserWithHighestFollowRatio());
    }

    // DTO Helpers
    @Data
    static class UpdateProfileReq {
        private AuthInfo auth;
        private String gender;
        private Integer age;
    }

    @Data
    static class FeedReq {
        private AuthInfo auth;
        private int page;
        private int size;
        private String category;
    }
}