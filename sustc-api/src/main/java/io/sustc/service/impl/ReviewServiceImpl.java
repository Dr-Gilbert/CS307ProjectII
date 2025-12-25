package io.sustc.service.impl;

import io.sustc.dto.AuthInfo;
import io.sustc.dto.PageResult;
import io.sustc.dto.RecipeRecord;
import io.sustc.dto.ReviewRecord;
import io.sustc.service.RecipeService;
import io.sustc.service.ReviewService;
import io.sustc.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class ReviewServiceImpl implements ReviewService {

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
    @Transactional
    public long addReview(AuthInfo auth, long recipeId, int rating, String review) {
        checkAuth(auth);
        if(Boolean.FALSE.equals(jdbcTemplate.queryForObject(
                "select exists (select 1 from recipes where RecipeId = ?)",
                Boolean.class,
                recipeId
        ))
                || rating < 1 || rating > 5 || auth == null) throw new IllegalArgumentException();
        return jdbcTemplate.queryForObject(
                "insert into reviews (recipeid, authorid, rating, review, datesubmitted, datemodified) values (?,?,?,?,?,?) returning ReviewId",
                Long.class,
                recipeId,
                auth.getAuthorId(),
                rating,
                review,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now())
        ).longValue();
    }

    @Override
    @Transactional
    public void editReview(AuthInfo auth, long recipeId, long reviewId, int rating, String review) {
        checkAuth(auth);
        if(jdbcTemplate.queryForObject(
                "select AuthorId from reviews where ReviewId = ?",
                Long.class,
                reviewId
        ).longValue() != auth.getAuthorId()) throw new SecurityException();
        if(jdbcTemplate.queryForObject(
                "select RecipeId from reviews where ReviewId = ?",
                Long.class,
                reviewId
        ).longValue() != recipeId) throw new IllegalArgumentException();
        if(rating < 1 || rating > 5) throw new IllegalArgumentException();
        jdbcTemplate.update(
                "update reviews set Rating = ?, Review = ?, DateModified = ? where ReviewId = ?",
                rating,
                review,
                Timestamp.from(Instant.now()),
                reviewId
        );
    }

    @Override
    @Transactional
    public void deleteReview(AuthInfo auth, long recipeId, long reviewId) {
        checkAuth(auth);
        if(Boolean.FALSE.equals(jdbcTemplate.queryForObject(
                "select exists (select 1 from reviews where ReviewId = ?)",
                Boolean.class,
                reviewId
        ))) throw new IllegalArgumentException();
        if(jdbcTemplate.queryForObject(
                "select AuthorId from reviews where ReviewId = ?",
                Long.class,
                reviewId
        ).longValue() != auth.getAuthorId()) throw new SecurityException();
        if(jdbcTemplate.queryForObject(
                "select RecipeId from reviews where ReviewId = ?",
                Long.class,
                reviewId
        ).longValue() != recipeId) throw new IllegalArgumentException();
        jdbcTemplate.update(
                "delete from reviews where ReviewId = ?",
                reviewId
        );
    }

    @Override
    @Transactional
    public long likeReview(AuthInfo auth, long reviewId) {
        checkAuth(auth);
        if(Boolean.FALSE.equals(jdbcTemplate.queryForObject(
                "select exists (select 1 from reviews where ReviewId = ?)",
                Boolean.class,
                reviewId
        ))) throw new IllegalArgumentException();


            jdbcTemplate.update(
                    "insert into like_review values (?,?) on conflict do nothing ",
                    auth.getAuthorId(),
                    reviewId
            );
        return jdbcTemplate.queryForObject(
                "select count(*) from like_review where LikeReviewId = ?",
                Long.class,
                reviewId
        ).longValue();
    }

    @Override
    @Transactional
    public long unlikeReview(AuthInfo auth, long reviewId) {
        checkAuth(auth);
        if(Boolean.FALSE.equals(jdbcTemplate.queryForObject(
                "select exists (select 1 from reviews where ReviewId = ?)",
                Boolean.class,
                reviewId
        ))) throw new IllegalArgumentException();
        jdbcTemplate.update(
                "delete from like_review where (AuthorId, LikeReviewId) = (?,?)",
                auth.getAuthorId(),
                reviewId
        );
        return jdbcTemplate.queryForObject(
                "select count(*) from like_review where LikeReviewId = ?",
                Long.class,
                reviewId
        ).longValue();
    }
    private long[] getLikeUsers(long reviewId) {
        List<Long> parts = jdbcTemplate.query(
                "SELECT AuthorId FROM like_review WHERE LikeReviewId = ? ORDER BY AuthorId ASC",
                (rs, rowNum) -> rs.getLong("AuthorId"),
                reviewId
        );
        if (parts == null || parts.isEmpty()) return new long[0];
        return parts.stream().mapToLong(Long::longValue).toArray();
    }

    @Override
    public PageResult<ReviewRecord> listByRecipe(long recipeId, int page, int size, String sort) {
        if(page < 1 || size <= 0) throw new IllegalArgumentException();
        PageResult<ReviewRecord> pageResult = new PageResult<>();
        pageResult.setSize(size);
        pageResult.setPage(page);
        int offset = (page - 1) * size;
        if(sort.equals("likes_desc")) {
            pageResult.setItems(jdbcTemplate.query(
                    "select r.*, u.authorname as AuthorName from reviews r\n" +
                            "join like_review lr on r.ReviewId = lr.LikeReviewId\n" +
                            "join users u using (AuthorId)\n" +
                            "where RecipeId = ?\n" +
                            "group by r.ReviewId\n" +
                            "order by count(lr.AuthorId) desc\n " +
                            "offset ? limit ?",
                    (rs, i) -> ReviewRecord.builder()
                            .reviewId(rs.getLong("ReviewId"))
                            .recipeId(rs.getLong("RecipeId"))
                            .authorId(rs.getLong("AuthorId"))
                            .rating(rs.getFloat("Rating"))
                            .review(rs.getString("Review"))
                            .dateSubmitted(rs.getTimestamp("DateSubmitted"))
                            .dateModified(rs.getTimestamp("DateModified"))
                            .authorName(rs.getString("AuthorName"))
                            .build(),
                    recipeId, offset, size
            ));

            pageResult.setTotal(jdbcTemplate.queryForObject(
                    "select count(*) from reviews r\n" +
                            "join like_review lr on r.ReviewId = lr.LikeReviewId\n" +
                            "where RecipeId = ?\n" +
                            "group by r.ReviewId\n",
                    Long.class, recipeId
            ).longValue());
        }
        else {
            pageResult.setItems(jdbcTemplate.query(
                    "select r.*, u.authorname as AuthorName from reviews r inner join users u " +
                            "using (AuthorId) where RecipeId = ? " +
                            "order by DateModified desc " +
                            "offset ? limit ?",
                    (rs, i) -> ReviewRecord.builder()
                            .reviewId(rs.getLong("ReviewId"))
                            .recipeId(rs.getLong("RecipeId"))
                            .authorId(rs.getLong("AuthorId"))
                            .rating(rs.getFloat("Rating"))
                            .review(rs.getString("Review"))
                            .dateSubmitted(rs.getTimestamp("DateSubmitted"))
                            .dateModified(rs.getTimestamp("DateModified"))
                            .authorName(rs.getString("AuthorName"))
                            .build(),
                    recipeId, offset, size
            ));

            pageResult.setTotal(jdbcTemplate.queryForObject(
                    "select count(*) from reviews where RecipeId = ? ",
                    Long.class,
                    recipeId
            ).longValue());
        }

        for(ReviewRecord r : pageResult.getItems()) {
            r.setLikes(getLikeUsers(r.getReviewId()));
        }
        return pageResult;
    }

    private RecipeRecord getRecipeById(long recipeId) {
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
                    "select Ingredient from ingredient where RecipeId = ?",
                    (rs, i) -> rs.getString("Ingredient"),
                    recipeId
            ).stream().toArray(String[]::new));

            return recipeRecord;
        }catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
    @Override
    @Transactional
    public RecipeRecord refreshRecipeAggregatedRating(long recipeId) {
        if(Boolean.FALSE.equals(jdbcTemplate.queryForObject(
                "select exists (select 1 from recipes where RecipeId = ?)",
                Boolean.class,
                recipeId
        ))) throw new IllegalArgumentException();

        return getRecipeById(recipeId);
    }

}