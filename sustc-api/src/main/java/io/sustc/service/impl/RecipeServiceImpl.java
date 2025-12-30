package io.sustc.service.impl;

import io.sustc.dto.*;
import io.sustc.service.RecipeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.awt.geom.Arc2D;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.DateTimeException;
import java.time.Duration;
import java.util.*;

@Service
@Slf4j
public class RecipeServiceImpl implements RecipeService {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void checkAuth(AuthInfo auth) {
        if (auth == null) {
            throw new SecurityException();
        }
        String sql = "select exists (select 1 from users where AuthorId = ? and Is_Deleted = false)";
        Boolean ok = jdbcTemplate.queryForObject(
                sql,
                Boolean.class,
                auth.getAuthorId()
        );

        if (Boolean.FALSE.equals(ok)) {
            throw new SecurityException();
        }
        if(auth.getPassword() == null || !auth.getPassword().equals(jdbcTemplate.queryForObject(
                "select password from users where authorid = ?",
                String.class, auth.getAuthorId()
        ))) throw new SecurityException();
    }

    @Override
    public String getNameFromID(long id) {
        return jdbcTemplate.queryForObject(
                "select Name from recipes where RecipeId = ?",
                String.class,
                id
        );
    }

    @Override
    public RecipeRecord getRecipeById(long recipeId) {
        if(recipeId <= 0) throw new IllegalArgumentException();
        try {
            RecipeRecord recipeRecord = jdbcTemplate.queryForObject(
                    "select * from recipes where RecipeId = ?",
                    (rs, i) -> RecipeRecord.builder()
                            .RecipeId(rs.getLong("RecipeId"))
                            .name(rs.getString("Name"))
                            .authorId(rs.getLong("AuthorId"))
                            .cookTime(rs.getString("CookTime"))
                            .prepTime(rs.getString("PrepTime"))
                            .totalTime(rs.getString("TotalTime"))
                            .datePublished(rs.getTimestamp("DatePublished"))
                            .description(rs.getString("Description"))
                            .recipeCategory(rs.getString("RecipeCategory"))
                            .calories(rs.getFloat("Calories"))
                            .fatContent(rs.getFloat("FatContent"))
                            .saturatedFatContent(rs.getFloat("SaturatedFatContent"))
                            .cholesterolContent(rs.getFloat("CholesterolContent"))
                            .sodiumContent(rs.getFloat("SodiumContent"))
                            .carbohydrateContent(rs.getFloat("CarbohydrateContent"))
                            .fiberContent(rs.getFloat("FiberContent"))
                            .sugarContent(rs.getFloat("SugarContent"))
                            .proteinContent(rs.getFloat("ProteinContent"))
                            .recipeServings(rs.getInt("RecipeServings"))
                            .recipeYield(rs.getString("RecipeYield"))
                            .aggregatedRating(rs.getFloat("AggregatedRating"))
                            .reviewCount(rs.getInt("ReviewCount"))
                            .build(),
                    recipeId
            );

            recipeRecord.setAuthorName(jdbcTemplate.queryForObject(
                    "select AuthorName from users where AuthorId = ?",
                    String.class,
                    recipeRecord.getAuthorId()
            ));

            recipeRecord.setRecipeIngredientParts(jdbcTemplate.query(
                    "select Ingredient from ingredient where RecipeId = ? ORDER BY LOWER(Ingredient) ASC",
                    (rs, i) -> rs.getString("Ingredient"),
                    recipeId
            ).stream().toArray(String[]::new));

            return recipeRecord;
        }catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private float getFloatOrZero(ResultSet rs, String col) {
        try {
            float v = rs.getFloat(col);
            if (rs.wasNull()) return 0f;
            return v;
        } catch (SQLException e) {
            return 0f;
        }
    }

    private RecipeRecord mapRecipeRow(ResultSet rs, int rowNum) throws SQLException {
        RecipeRecord r = new RecipeRecord();
        r.setRecipeId(rs.getLong("RecipeId"));
        r.setName(rs.getString("Name"));
        r.setAuthorId(rs.getLong("AuthorId"));
        try {
            r.setAuthorName(rs.getString("AuthorName"));
        } catch (SQLException ignored) {
            // authorName may not be present in select
        }
        r.setCookTime(rs.getString("CookTime"));
        r.setPrepTime(rs.getString("PrepTime"));
        r.setTotalTime(rs.getString("TotalTime"));
        Timestamp ts = rs.getTimestamp("DatePublished");
        r.setDatePublished(ts);
        r.setDescription(rs.getString("Description"));
        r.setRecipeCategory(rs.getString("RecipeCategory"));

        // Ingredient parts will be set later
        r.setRecipeIngredientParts(null);

        // Ratings and nutrition
        r.setAggregatedRating(rs.getFloat("AggregatedRating"));
        r.setReviewCount(rs.getInt("ReviewCount"));

        // calories and nutritional values - some DBs return doubles; use ResultSet getters carefully
        double calories = rs.getDouble("Calories");
        if (rs.wasNull()) r.setCalories(0f);
        else r.setCalories((float) calories);

        r.setFatContent(getFloatOrZero(rs, "FatContent"));
        r.setSaturatedFatContent(getFloatOrZero(rs, "SaturatedFatContent"));
        r.setCholesterolContent(getFloatOrZero(rs, "CholesterolContent"));
        r.setSodiumContent(getFloatOrZero(rs, "SodiumContent"));
        r.setCarbohydrateContent(getFloatOrZero(rs, "CarbohydrateContent"));
        r.setFiberContent(getFloatOrZero(rs, "FiberContent"));
        r.setSugarContent(getFloatOrZero(rs, "SugarContent"));
        r.setProteinContent(getFloatOrZero(rs, "ProteinContent"));

        // servings / yield - note types mismatch in DB vs DTO: handle gracefully
        try {
            r.setRecipeServings(rs.getInt("RecipeServings"));
        } catch (SQLException e) {
            // in some schemas it may be varchar; try alternate
            try {
                String s = rs.getString("RecipeServings");
                r.setRecipeServings(s == null ? 0 : Integer.parseInt(s));
            } catch (Exception ex) {
                r.setRecipeServings(0);
            }
        }
        r.setRecipeYield(rs.getString("RecipeYield"));

        return r;
    }

    private String[] loadIngredientParts(long recipeId) {
        List<String> parts = jdbcTemplate.query(
                "SELECT Ingredient FROM ingredient WHERE RecipeId = ? ORDER BY LOWER(Ingredient) ASC",
                (rs, rowNum) -> rs.getString("Ingredient"),
                recipeId
        );
        if (parts == null || parts.isEmpty()) return null;
        return parts.toArray(new String[0]);
    }

    @Override
    public PageResult<RecipeRecord> searchRecipes(String keyword, String category, Double minRating,
                                                  Integer page, Integer size, String sort) {
        if (page == null || page < 1) throw new IllegalArgumentException("page must be >= 1");
        if (size == null || size <= 0) throw new IllegalArgumentException("size must be > 0");

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            where.append(" AND (LOWER(r.Name) LIKE ? OR LOWER(r.Description) LIKE ?) ");
            String kw = "%" + keyword.trim().toLowerCase() + "%";
            params.add(kw);
            params.add(kw);
        }
        if (category != null && !category.trim().isEmpty()) {
            where.append(" AND r.RecipeCategory = ? ");
            params.add(category);
        }
        if (minRating != null) {
            where.append(" AND r.AggregatedRating >= ? ");
            params.add(minRating);
        }

        String orderBy = " ORDER BY r.RecipeId ASC ";
        if (sort != null) {
            switch (sort) {
                case "rating_desc":
                    orderBy = " ORDER BY r.AggregatedRating DESC NULLS LAST, r.RecipeId DESC ";
                    break;
                case "date_desc":
                    orderBy = " ORDER BY r.DatePublished DESC NULLS LAST, r.RecipeId DESC ";
                    break;
                case "calories_asc":
                    orderBy = " ORDER BY r.Calories ASC NULLS LAST, r.RecipeId DESC ";
                    break;
                default:
                    // keep default
                    break;
            }
        }

        int offset = (page - 1) * size;
        String sql = "SELECT r.*, u.AuthorName FROM recipes r LEFT JOIN users u ON r.AuthorId = u.AuthorId "
                + where.toString()
                + orderBy
                + " LIMIT ? OFFSET ?";

        // add limit/offset params
        params.add(size);
        params.add(offset);

        List<RecipeRecord> items = jdbcTemplate.query(sql, params.toArray(), (rs, rowNum) -> mapRecipeRow(rs, rowNum));

        // load ingredient parts for each recipe (N+1 queries). For correctness and to ensure order.
        for (RecipeRecord rec : items) {
            rec.setRecipeIngredientParts(loadIngredientParts(rec.getRecipeId()));
        }

        // total count
        String countSql = "SELECT COUNT(*) FROM recipes r " + where.toString();
        // params for count are same as earlier without limit/offset
        Object[] countParams = params.subList(0, Math.max(0, params.size() - 2)).toArray();
        Long total = jdbcTemplate.queryForObject(countSql, countParams, Long.class);
        if (total == null) total = 0L;

        PageResult<RecipeRecord> result = PageResult.<RecipeRecord>builder()
                .items(items)
                .page(page)
                .size(size)
                .total(total)
                .build();

        for(RecipeRecord r : result.getItems()) {

        }

        return result;
    }

    @Override
    @Transactional
    public long createRecipe(RecipeRecord dto, AuthInfo auth) {
        checkAuth(auth);
        if(dto == null) throw new IllegalArgumentException();
        try {
            long id = jdbcTemplate.queryForObject(
                    "insert into recipes (name, authorid, cooktime, preptime," +
                            " totaltime, datepublished, description, recipecategory," +
                            " calories, fatcontent, saturatedfatcontent, cholesterolcontent," +
                            " sodiumcontent, carbohydratecontent, fibercontent, sugarcontent," +
                            " proteincontent, recipeservings, recipeyield, aggregatedrating, reviewcount) " +
                            "values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,null,0) returning RecipeId",
                    Long.class,
                    dto.getName(),
                    dto.getAuthorId(),
                    dto.getCookTime(),
                    dto.getPrepTime(),
                    dto.getTotalTime(),
                    dto.getDatePublished(),
                    dto.getDescription(),
                    dto.getRecipeCategory(),
                    dto.getCalories(),
                    dto.getFatContent(),
                    dto.getSaturatedFatContent(),
                    dto.getCholesterolContent(),
                    dto.getSodiumContent(),
                    dto.getCarbohydrateContent(),
                    dto.getFiberContent(),
                    dto.getSugarContent(),
                    dto.getProteinContent(),
                    dto.getRecipeServings(),
                    dto.getRecipeYield()
            ).longValue();
            String[] parts = dto.getRecipeIngredientParts();
            if (parts == null) return id;
            jdbcTemplate.batchUpdate(
                    "INSERT INTO ingredient (RecipeId, Ingredient) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                            ps.setLong(1,id);
                            ps.setString(2,parts[i]);
                        }

                        @Override
                        public int getBatchSize() {
                            return parts.length;
                        }
                    }
            );

            return id;
        }catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException();
        }
    }

    @Override
    @Transactional
    public void deleteRecipe(long recipeId, AuthInfo auth) {
        checkAuth(auth);
        Long authorId = null;
        try {
            authorId = jdbcTemplate.queryForObject(
                    "select AuthorId from recipes where RecipeId = ?",
                    Long.class,
                    recipeId
            );
        }catch (EmptyResultDataAccessException e) {
            throw new SecurityException();
        }
        if(authorId == null || authorId.longValue() != auth.getAuthorId()) throw new SecurityException();
        jdbcTemplate.update(
                "delete from recipes where RecipeId = ?",
                recipeId
        );
    }

    private long getRecipeAuthorIdOrThrow(long recipeId) {
        if(Boolean.FALSE.equals(jdbcTemplate.queryForObject(
                "select exists (select 1 from recipes where recipeid = ?)",
                Boolean.class,
                recipeId
        ))) throw new IllegalArgumentException();

        Long authorId = jdbcTemplate.queryForObject(
                "SELECT AuthorId FROM recipes WHERE RecipeId = ?",
                Long.class,
                recipeId
        );
        if (authorId == null) {
            throw new IllegalArgumentException();
        }
        return authorId;
    }
    @Override
    @Transactional
    public void updateTimes(AuthInfo auth, long recipeId, String cookTimeIso, String prepTimeIso) {
        checkAuth(auth);
        long ownerId = getRecipeAuthorIdOrThrow(recipeId);
        if (ownerId != auth.getAuthorId()) {
            throw new SecurityException();
        }

        // Parse durations if provided
        Duration cook = null, prep = null;

        try {
            cook = cookTimeIso == null ? null : Duration.parse(cookTimeIso);
            prep = prepTimeIso == null ? null : Duration.parse(prepTimeIso);
        }  catch (Exception e) {
            throw new IllegalArgumentException();
        }

        if (cook != null && cook.isNegative()) throw new IllegalArgumentException();
        if (prep != null && prep.isNegative()) throw new IllegalArgumentException();

        // fetch existing values if one of them is null
        String existingCook = null;
        String existingPrep = null;
        if (cook == null || prep == null) {
            Map<String, Object> row = jdbcTemplate.queryForMap("SELECT CookTime, PrepTime FROM recipes WHERE RecipeId = ?", recipeId);
            existingCook = ((String) row.get("CookTime")).trim();
            existingPrep = ((String) row.get("PrepTime")).trim();
            try {
                if (cook == null && existingCook != null && !existingCook.isEmpty()) cook = Duration.parse(existingCook);
            } catch (Exception e) {
                throw new IllegalArgumentException();
            }
            try {
                if (prep == null && existingPrep != null && !existingPrep.isEmpty()) prep = Duration.parse(existingPrep);
            } catch (Exception e) {
                throw new IllegalArgumentException();
            }
            // if still null, treat absent as zero
            if (cook == null) cook = Duration.ZERO;
            if (prep == null) prep = Duration.ZERO;
        }

        // compute total and update
        Duration total = cook.plus(prep);
        if (total.isNegative()) throw new IllegalArgumentException();

        // Use ISO-8601 string representation
        String cookStr = cookTimeIso != null ? cookTimeIso : existingCook;
        String prepStr = prepTimeIso != null ? prepTimeIso : existingPrep;
        String totalStr = total.toString();

        // Update fields
        jdbcTemplate.update("UPDATE recipes SET CookTime = ?, PrepTime = ?, TotalTime = ? WHERE RecipeId = ?",
                cookStr, prepStr, totalStr, recipeId);
    }

    @Override
    public Map<String, Object> getClosestCaloriePair() {
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                """
                        select r1.RecipeId as "RecipeA", r2.RecipeId as "RecipeB", r1.Calories as "CaloriesA", r2.Calories as "CaloriesB", abs(r1.Calories - r2.Calories) as "Difference"
                        from recipes r1 join recipes r2 on (r1.RecipeId < r2.RecipeId)
                        where r1.Calories is not null and r2.Calories is not null
                        order by "Difference" asc, r1.RecipeId asc, r2.RecipeId asc
                        limit 1
                    """
        );
        for(Map<String, Object> mp : list) {
            Double originCA = (Double) mp.get("CaloriesA"), originCB = (Double) mp.get("CaloriesB"), diff = (Double) mp.get("Difference");
            mp.replace("CaloriesA", (Double) (new BigDecimal(originCA == null ? "0.0" : originCA.toString()).setScale(2, RoundingMode.HALF_UP).doubleValue()));
            mp.replace("CaloriesB", (Double) (new BigDecimal(originCB == null ? "0.0" : originCB.toString()).setScale(2, RoundingMode.HALF_UP).doubleValue()));
            mp.replace("Difference", (Double) (new BigDecimal(diff == null ? "0.0" : diff.toString()).setScale(2, RoundingMode.HALF_UP).doubleValue()));
        }
        return (list.isEmpty() ? null : list.get(0));
    }

    @Override
    public List<Map<String, Object>> getTop3MostComplexRecipesByIngredients() {
         List<Map<String, Object>> res = jdbcTemplate.queryForList(
                """
                        SELECT r.RecipeId AS "RecipeId", COUNT(ri.Ingredient) AS "IngredientCount", r.Name AS "Name"
                        FROM ingredient ri JOIN recipes r ON ri.RecipeId = r.RecipeId
                        GROUP BY r.RecipeId, r.Name
                        ORDER BY "IngredientCount" DESC, r.RecipeId ASC 
                        LIMIT 3
                    """
        );
         for(Map<String, Object> mp : res) {
             Integer val = ((Long) mp.get("IngredientCount")).intValue();
             mp.replace("IngredientCount", val);
         }
         return res;
    }


}