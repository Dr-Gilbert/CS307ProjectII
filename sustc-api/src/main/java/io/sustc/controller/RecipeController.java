package io.sustc.controller;

import io.sustc.dto.AuthInfo;
import io.sustc.dto.PageResult;
import io.sustc.dto.RecipeRecord;
import io.sustc.service.RecipeService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recipes")
@CrossOrigin(origins = "*")
public class RecipeController {

    @Autowired
    private RecipeService recipeService;

    // 1. 获取食谱详情
    @GetMapping("/{recipeId}")
    public ResponseEntity<RecipeRecord> getRecipe(@PathVariable long recipeId) {
        RecipeRecord record = recipeService.getRecipeById(recipeId);
        return record != null ? ResponseEntity.ok(record) : ResponseEntity.notFound().build();
    }

    // 2. 搜索食谱
    @GetMapping("/search")
    public ResponseEntity<PageResult<RecipeRecord>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minRating,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String sort
    ) {
        try {
            return ResponseEntity.ok(recipeService.searchRecipes(keyword, category, minRating, page, size, sort));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // 3. 创建食谱
    @PostMapping
    public ResponseEntity<Long> createRecipe(@RequestBody CreateRecipeReq req) {
        try {
            long id = recipeService.createRecipe(req.getDto(), req.getAuth());
            return ResponseEntity.ok(id);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // 4. 删除食谱
    @DeleteMapping("/{recipeId}")
    public ResponseEntity<Void> deleteRecipe(
            @PathVariable long recipeId,
            @RequestBody AuthInfo auth
    ) {
        try {
            recipeService.deleteRecipe(recipeId, auth);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    // 5. 更新食谱时间
    @PutMapping("/{recipeId}/times")
    public ResponseEntity<Void> updateTimes(
            @PathVariable long recipeId,
            @RequestBody UpdateTimeReq req
    ) {
        try {
            recipeService.updateTimes(req.getAuth(), recipeId, req.getCookTimeIso(), req.getPrepTimeIso());
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // 6. 获取卡路里最接近的配对
    @GetMapping("/stats/closest-calories")
    public ResponseEntity<Map<String, Object>> getClosestCaloriePair() {
        return ResponseEntity.ok(recipeService.getClosestCaloriePair());
    }

    // 7. 获取配料最复杂的前3名
    @GetMapping("/stats/most-complex")
    public ResponseEntity<List<Map<String, Object>>> getMostComplex() {
        return ResponseEntity.ok(recipeService.getTop3MostComplexRecipesByIngredients());
    }

    @Data
    static class CreateRecipeReq {
        private AuthInfo auth;
        private RecipeRecord dto;
    }

    @Data
    static class UpdateTimeReq {
        private AuthInfo auth;
        private String cookTimeIso;
        private String prepTimeIso;
    }
}