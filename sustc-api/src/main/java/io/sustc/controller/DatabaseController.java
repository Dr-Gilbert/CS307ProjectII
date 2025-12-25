package io.sustc.controller;

import io.sustc.dto.RecipeRecord;
import io.sustc.dto.ReviewRecord;
import io.sustc.dto.UserRecord;
import io.sustc.service.DatabaseService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/database")
@CrossOrigin
public class DatabaseController {

    @Autowired
    private DatabaseService databaseService;

    @GetMapping("/group-members")
    public List<Integer> getGroupMembers() {
        return databaseService.getGroupMembers();
    }

    @PostMapping("/import")
    public void importData(@RequestBody ImportDataRequest request) {
        databaseService.importData(request.getReviewRecords(), request.getUserRecords(), request.getRecipeRecords());
    }

    @DeleteMapping("/drop")
    public void drop() {
        databaseService.drop();
    }

    @GetMapping("/sum")
    public Integer sum(@RequestParam int a, @RequestParam int b) {
        return databaseService.sum(a, b);
    }

    // 内部类，用于接收导入的大量数据
    @Data
    public static class ImportDataRequest {
        private List<ReviewRecord> reviewRecords;
        private List<UserRecord> userRecords;
        private List<RecipeRecord> recipeRecords;
    }
}