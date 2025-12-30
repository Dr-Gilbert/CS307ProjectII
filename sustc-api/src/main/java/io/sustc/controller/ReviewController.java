package io.sustc.controller;

import io.sustc.dto.AuthInfo;
import io.sustc.dto.PageResult;
import io.sustc.dto.RecipeRecord;
import io.sustc.dto.ReviewRecord;
import io.sustc.service.ReviewService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    // 1. 添加评论
    @PostMapping("/recipes/{recipeId}/reviews")
    public ResponseEntity<Long> addReview(
            @PathVariable long recipeId,
            @RequestBody ReviewReq req
    ) {
        try {
            long reviewId = reviewService.addReview(req.getAuth(), recipeId, req.getRating(), req.getReview());
            return ResponseEntity.ok(reviewId);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // 2. 编辑评论
    @PutMapping("/recipes/{recipeId}/reviews/{reviewId}")
    public ResponseEntity<Void> editReview(
            @PathVariable long recipeId,
            @PathVariable long reviewId,
            @RequestBody ReviewReq req
    ) {
        try {
            reviewService.editReview(req.getAuth(), recipeId, reviewId, req.getRating(), req.getReview());
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // 3. 删除评论
    @DeleteMapping("/recipes/{recipeId}/reviews/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable long recipeId,
            @PathVariable long reviewId,
            @RequestBody AuthInfo auth
    ) {
        try {
            reviewService.deleteReview(auth, recipeId, reviewId);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // 4. 点赞评论
    @PostMapping("/reviews/{reviewId}/like")
    public ResponseEntity<Long> likeReview(
            @PathVariable long reviewId,
            @RequestBody AuthInfo auth
    ) {
        try {
            long count = reviewService.likeReview(auth, reviewId);
            return ResponseEntity.ok(count);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // 5. 取消点赞评论
    @PostMapping("/reviews/{reviewId}/unlike")
    public ResponseEntity<Long> unlikeReview(
            @PathVariable long reviewId,
            @RequestBody AuthInfo auth
    ) {
        try {
            long count = reviewService.unlikeReview(auth, reviewId);
            return ResponseEntity.ok(count);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // 6. 获取食谱的评论列表
    @GetMapping("/recipes/{recipeId}/reviews")
    public ResponseEntity<PageResult<ReviewRecord>> listReviews(
            @PathVariable long recipeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "date_desc") String sort
    ) {
        try {
            return ResponseEntity.ok(reviewService.listByRecipe(recipeId, page, size, sort));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // 7. 刷新食谱评分（辅助接口）
    @PostMapping("/recipes/{recipeId}/refresh-rating")
    public ResponseEntity<RecipeRecord> refreshRating(@PathVariable long recipeId) {
        try {
            return ResponseEntity.ok(reviewService.refreshRecipeAggregatedRating(recipeId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // DTO Helper
    @Data
    static class ReviewReq {
        private AuthInfo auth;
        private int rating;
        private String review;
    }
}