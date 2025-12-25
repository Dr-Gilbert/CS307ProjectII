package io.sustc.service.impl;

import io.sustc.dto.ReviewRecord;
import io.sustc.dto.UserRecord;
import io.sustc.dto.RecipeRecord;
import io.sustc.service.DatabaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * It's important to mark your implementation class with {@link Service} annotation.
 * As long as the class is annotated and implements the corresponding interface, you can place it under any package.
 */
@Service
@Slf4j
public class DatabaseServiceImpl implements DatabaseService {

    /**
     * Getting a {@link DataSource} instance from the framework, whose connections are managed by HikariCP.
     * <p>
     * Marking a field with {@link Autowired} annotation enables our framework to automatically
     * provide you a well-configured instance of {@link DataSource}.
     * Learn more: <a href="https://www.baeldung.com/spring-dependency-injection">Dependency Injection</a>
     */
    @Autowired
    private DataSource dataSource;

    @Override
    public List<Integer> getGroupMembers() {
        //TODO: replace this with your own student IDs in your group
        return Arrays.asList(12411122, 12412018);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String getGender(String gender) {
        if(gender != null && gender.toUpperCase().equals("MALE")) return "Male";
        if(gender != null && gender.toUpperCase().equals("FEMALE")) return "Female";
        return gender;
    }

    @Override
    @Transactional
    public void importData(
            List<ReviewRecord> reviewRecords,
            List<UserRecord> userRecords,
            List<RecipeRecord> recipeRecords) {

        // ddl to create tables.
        createTables();

        // Basic validations
        if ((userRecords == null || userRecords.isEmpty())
                && (recipeRecords == null || recipeRecords.isEmpty())
                && (reviewRecords == null || reviewRecords.isEmpty())) {
            return;
        }

//        for (int i = 0; i < 100; i++) {
//            log.debug("importData show: {}", userRecords.get(i).toString());
//        }

        // 1) Insert users
        if (userRecords != null && !userRecords.isEmpty()) {
            final String userSql = "INSERT INTO users (AuthorName, Gender, Age, followercount, followeecount, Password, Is_Deleted) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";

            jdbcTemplate.batchUpdate(userSql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    UserRecord u = userRecords.get(i);
                    ps.setString(1, u.getAuthorName());
                    ps.setString(2, getGender(u.getGender()));
                    ps.setInt(3, u.getAge());
                    ps.setLong(4, u.getFollowers());
                    ps.setLong(5, u.getFollowing());
                    ps.setString(6, u.getPassword());
                    ps.setBoolean(7, u.isDeleted());
                }

                @Override
                public int getBatchSize() {
                    return userRecords.size();
                }
            });
        }

        // 2) Insert recipes
        if (recipeRecords != null && !recipeRecords.isEmpty()) {
            final String recipeSql = "INSERT INTO recipes (Name, AuthorId, CookTime, PrepTime, TotalTime, DatePublished, Description, RecipeCategory, Calories, FatContent, SaturatedFatContent, CholesterolContent, SodiumContent, CarbohydrateContent, FiberContent, SugarContent, ProteinContent, RecipeServings, RecipeYield) " +
                    "VALUES ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            jdbcTemplate.batchUpdate(recipeSql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    RecipeRecord r = recipeRecords.get(i);
                    ps.setString(1, r.getName());
                    ps.setLong(2, r.getAuthorId());
                    ps.setString(3, r.getCookTime());
                    ps.setString(4, r.getPrepTime());
                    ps.setString(5, r.getTotalTime());
                    ps.setTimestamp(6, r.getDatePublished());
                    ps.setString(7, r.getDescription());
                    ps.setString(8, r.getRecipeCategory());
                    ps.setFloat(9, r.getCalories());
                    ps.setFloat(10, r.getFatContent());
                    ps.setFloat(11, r.getSaturatedFatContent());
                    ps.setFloat(12, r.getCholesterolContent());
                    ps.setFloat(13, r.getSodiumContent());
                    ps.setFloat(14, r.getCarbohydrateContent());
                    ps.setFloat(15, r.getFiberContent());
                    ps.setFloat(16, r.getSugarContent());
                    ps.setFloat(17, r.getProteinContent());
                    // RecipeServings in createTables is VARCHAR(100) in this implementation; DTO has int - write as int
                    ps.setInt(18, r.getRecipeServings());
                    ps.setString(19, r.getRecipeYield());
                }

                @Override
                public int getBatchSize() {
                    return recipeRecords.size();
                }
            });
        }

        // 3) Insert recipe ingredients (recipe_ingredients)
        // Use ON CONFLICT DO NOTHING to tolerate duplicates if table has PK constraint
        if (recipeRecords != null && !recipeRecords.isEmpty()) {
            List<Map.Entry<Long, String>> ingredientPairs = new ArrayList<>();
            for (RecipeRecord r : recipeRecords) {
                String[] parts = r.getRecipeIngredientParts();
                if (parts == null || parts.length == 0) continue;
                // Clean, distinct, sorted case-insensitively
                List<String> cleaned = new ArrayList<>();
                for (String p : parts) {
                    if (p == null) continue;
                    String t = p.trim();
                    if (!t.isEmpty()) cleaned.add(t);
                }
                cleaned = new ArrayList<>(new LinkedHashSet<>(cleaned)); // keep order but dedup
                cleaned.sort(String.CASE_INSENSITIVE_ORDER);
                for (String p : cleaned) {
                    ingredientPairs.add(new AbstractMap.SimpleEntry<>(r.getRecipeId(), p));
                }
            }

            if (!ingredientPairs.isEmpty()) {
                final String ingrSql = "INSERT INTO ingredient (RecipeId, Ingredient) VALUES (?, ?) ON CONFLICT DO NOTHING";
                jdbcTemplate.batchUpdate(ingrSql, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        Map.Entry<Long, String> e = ingredientPairs.get(i);
                        ps.setLong(1, e.getKey());
                        ps.setString(2, e.getValue());
                    }

                    @Override
                    public int getBatchSize() {
                        return ingredientPairs.size();
                    }
                });
            }
        }

        // 4) Insert reviews
        if (reviewRecords != null && !reviewRecords.isEmpty()) {
            final String reviewSql = "INSERT INTO reviews (RecipeId, AuthorId, Rating, Review, DateSubmitted, DateModified) VALUES (?, ?, ?, ?, ?, ?)";
            jdbcTemplate.batchUpdate(reviewSql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    ReviewRecord rv = reviewRecords.get(i);
                    ps.setLong(1, rv.getRecipeId());
                    ps.setLong(2, rv.getAuthorId());
                    ps.setFloat(3, rv.getRating());
                    ps.setString(4, rv.getReview());
                    ps.setTimestamp(5, rv.getDateSubmitted());
                    ps.setTimestamp(6, rv.getDateModified());
                }

                @Override
                public int getBatchSize() {
                    return reviewRecords.size();
                }
            });
        }

        // 5) Insert review likes (table review_likes has columns ReviewId, AuthorId in createTables)
        if (reviewRecords != null && !reviewRecords.isEmpty()) {
            List<Map.Entry<Long, Long>> reviewLikePairs = new ArrayList<>();
            for (ReviewRecord rv : reviewRecords) {
                long rid = rv.getReviewId();
                long[] likes = rv.getLikes();
                if (likes == null || likes.length == 0) continue;
                for (long liker : likes) {
                    reviewLikePairs.add(new AbstractMap.SimpleEntry<>(rid, liker));
                }
            }
            if (!reviewLikePairs.isEmpty()) {
                final String likeSql = "INSERT INTO like_review (LikeReviewId, AuthorId) VALUES (?, ?) ON CONFLICT DO NOTHING";
                jdbcTemplate.batchUpdate(likeSql, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        Map.Entry<Long, Long> e = reviewLikePairs.get(i);
                        ps.setLong(1, e.getKey());
                        ps.setLong(2, e.getValue());
                    }

                    @Override
                    public int getBatchSize() {
                        return reviewLikePairs.size();
                    }
                });
            }
        }

        // 6) Insert user follows (user_follows table created in createTables)
        // Build pairs from UserRecord.followerUsers and followingUsers
        if (userRecords != null && !userRecords.isEmpty()) {
            // Use a set to deduplicate pairs
            Set<Long> pairSet = new HashSet<>(); // encode (follower << 32) | following might overflow; use string key
            List<Map.Entry<Long, Long>> follows = new ArrayList<>();
            for (UserRecord u : userRecords) {
                long author = u.getAuthorId();
                long[] followers = u.getFollowerUsers();
                if (followers != null) {
                    for (long f : followers) {
                        if (f == author) continue;
                        String key = f + ":" + author;
                        if (pairSet.add(key.hashCode() * 31L + key.length())) {
                            follows.add(new AbstractMap.SimpleEntry<>(f, author));
                        } else {
                            // avoid duplicates using string-set instead for correctness
                        }
                    }
                }
                long[] following = u.getFollowingUsers();
                if (following != null) {
                    for (long fo : following) {
                        if (fo == author) continue;
                        String key = author + ":" + fo;
                        if (pairSet.add(key.hashCode() * 31L + key.length())) {
                            follows.add(new AbstractMap.SimpleEntry<>(author, fo));
                        }
                    }
                }
            }

            // Better dedup using explicit string set (recompute properly)
            Set<String> seen = new HashSet<>();
            List<Map.Entry<Long, Long>> filtered = new ArrayList<>();
            for (Map.Entry<Long, Long> e : follows) {
                String k = e.getKey() + ":" + e.getValue();
                if (!seen.contains(k)) {
                    seen.add(k);
                    filtered.add(e);
                }
            }

            if (!filtered.isEmpty()) {
                final String followSql = "INSERT INTO follow (FollowerId, FolloweeId) VALUES (?, ?) ON CONFLICT DO NOTHING";
                jdbcTemplate.batchUpdate(followSql, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        Map.Entry<Long, Long> e = filtered.get(i);
                        ps.setLong(1, e.getKey());
                        ps.setLong(2, e.getValue());
                    }

                    @Override
                    public int getBatchSize() {
                        return filtered.size();
                    }
                });
            }

            // Optionally, ensure Followers/Following counters match provided userRecords (some schemas expect these fields to be prefilled).
            // We'll update all users' counters from the provided DTO values to be safe.
            final String updateCountSql = "UPDATE users SET FollowerCount = ?, FolloweeCount = ? WHERE AuthorId = ?";
            jdbcTemplate.batchUpdate(updateCountSql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    UserRecord u = userRecords.get(i);
                    ps.setLong(1, u.getFollowers());
                    ps.setLong(2, u.getFollowing());
                    ps.setLong(3, u.getAuthorId());
                }

                @Override
                public int getBatchSize() {
                    return userRecords.size();
                }
            });
        }

        // 7) (Optional) Recompute ratings from reviews to ensure consistency (if rating trigger not present)
        // We'll compute aggregated rating and review count per recipe from inserted reviews to be safe.
        try {
            String recomputeSql = "UPDATE recipes r SET (AggregatedRating, ReviewCount) = (" +
                    "  COALESCE(t.avg_rating, 0), COALESCE(t.cnt, 0)) " +
                    "FROM (" +
                    "  SELECT RecipeId, ROUND(AVG(Rating)::numeric * 2) / 2 AS avg_rating, COUNT(*) AS cnt " +
                    "  FROM reviews GROUP BY RecipeId" +
                    ") t WHERE r.RecipeId = t.RecipeId";
            jdbcTemplate.update(recomputeSql);
        } catch (Exception ex) {
            log.warn("Failed to recompute recipe ratings - continuing. Reason: {}", ex.getMessage());
        }

        log.info("Import completed: users={}, recipes={}, reviews={}",
                userRecords == null ? 0 : userRecords.size(),
                recipeRecords == null ? 0 : recipeRecords.size(),
                reviewRecords == null ? 0 : reviewRecords.size());

    }


    private void createTables() {
        String[] createTableSQLs = {
                "create table users (\n" +
                        "    AuthorId bigserial,\n" +
                        "    AuthorName varchar not null check ( AuthorName != '' ),\n" +
                        "    Gender varchar check ( Gender = 'Male' or Gender = 'Female' ),\n" +
                        "    Age int check ( Age > 0 ),\n" +
                        "    Is_Deleted boolean,\n" +
                        "    FollowerCount bigint,\n" +
                        "    FolloweeCount bigint,\n" +
                        "    Password varchar,\n" +
                        "    constraint pk_users primary key (AuthorId)\n" +
                        ");",

                "create table follow (\n" +
                        "    FollowerId bigint,\n" +
                        "    FolloweeId bigint,\n" +
                        "    constraint pk_follow primary key (FollowerId, FolloweeId),\n" +
                        "    constraint fk1_follow_users foreign key (FollowerId) references users(AuthorId),\n" +
                        "    constraint fk2_follow_users foreign key (FolloweeId) references users(AuthorId)\n" +
                        ");",

                "create index follow_followee_index on follow(FolloweeId);",

                "create or replace function follow_adder()\n" +
                        "returns trigger as $$\n" +
                        "begin\n" +
                        "    update users set FollowerCount = FollowerCount + 1 where AuthorId = new.FolloweeId;\n" +
                        "    update users set FolloweeCount = FolloweeCount + 1 where AuthorId = new.FollowerId;\n" +
                        "    return new;\n" +
                        "end;\n" +
                        "$$ language plpgsql;",

                "create or replace trigger follow_add_trigger\n" +
                        "after insert on follow\n" +
                        "for each row\n" +
                        "execute function follow_adder();",

                "create or replace function follow_delete()\n" +
                        "returns trigger as $$\n" +
                        "begin\n" +
                        "    update users set FollowerCount = FollowerCount - 1 where AuthorId = old.FolloweeId;\n" +
                        "    update users set FolloweeCount = FolloweeCount - 1 where AuthorId = old.FollowerId;\n" +
                        "    return old;\n" +
                        "end;\n" +
                        "$$ language plpgsql;",

                "create or replace trigger follow_delete_trigger\n" +
                        "after delete on follow\n" +
                        "for each row\n" +
                        "execute function follow_delete();",

                "create table recipes (\n" +
                        "    RecipeId bigserial,\n" +
                        "    Name varchar not null check ( Name != '' ),\n" +
                        "    AuthorId bigint,\n" +
                        "    CookTime varchar,\n" +
                        "    PrepTime varchar,\n" +
                        "    TotalTime varchar,\n" +
                        "    DatePublished timestamp,\n" +
                        "    Description varchar,\n" +
                        "    RecipeCategory varchar,\n" +
                        "    Calories float,\n" +
                        "    FatContent float,\n" +
                        "    SaturatedFatContent float,\n" +
                        "    CholesterolContent float,\n" +
                        "    SodiumContent float,\n" +
                        "    CarbohydrateContent float,\n" +
                        "    FiberContent float,\n" +
                        "    SugarContent float,\n" +
                        "    ProteinContent float,\n" +
                        "    RecipeServings int,\n" +
                        "    RecipeYield varchar,\n" +
                        "    AggregatedRating float,\n" +
                        "    ReviewCount int,\n" +
                        "\n" +
                        "    constraint pk_recipes primary key (RecipeId),\n" +
                        "    constraint fk1_recipes_users foreign key (AuthorId) references users(AuthorId)\n" +
                        ");",

                "create index recipes_name_index on recipes(Name);",
                "create index recipes_category_index on recipes(RecipeCategory);",
                "create index recipes_rating_index on recipes(AggregatedRating desc);",
                "create index recipes_date_index on recipes(DatePublished desc);",

                "create table keyword (\n" +
                        "    RecipeId bigint,\n" +
                        "    Keyword varchar,\n" +
                        "    constraint pk_keyword primary key (RecipeId, Keyword),\n" +
                        "    constraint fk_keyword_recipe foreign key (RecipeId) references recipes(RecipeId) on delete cascade \n" +
                        ");",

                "create index keyword_recipes_index on keyword(Keyword);",

                "create table ingredient (\n" +
                        "    RecipeId bigint,\n" +
                        "    Ingredient varchar,\n" +
                        "    constraint pk_ingredient primary key (RecipeId, Ingredient),\n" +
                        "    constraint fk_ingredient_recipe foreign key (RecipeId) references recipes(RecipeId) on delete cascade \n" +
                        ");\n",

                "create index ingredient_index on ingredient(Ingredient);",

                "create table reviews (\n" +
                        "    ReviewId bigserial,\n" +
                        "    RecipeId bigint,\n" +
                        "    AuthorId bigint,\n" +
                        "    Rating float,\n" +
                        "    Review varchar,\n" +
                        "    DateSubmitted timestamp,\n" +
                        "    DateModified timestamp,\n" +
                        "    constraint pk_reviews primary key (ReviewId),\n" +
                        "    constraint fk1_reviews foreign key (RecipeId) references recipes on delete cascade ,\n" +
                        "    constraint fk2_reviews foreign key (AuthorId) references users\n" +
                        ");",

                "create or replace function rating_adapter(id BIGINT)\n" +
                        "returns void as $$\n" +
                        "begin\n" +
                        "    update recipes set\n" +
                        "    AggregatedRating = avg_rating,\n" +
                        "    ReviewCount = cnt\n" +
                        "    from (\n" +
                        "        select round(avg(rating)::numeric * 2) / 2 as avg_rating,\n" +
                        "               count(*) as cnt\n" +
                        "        from reviews where RecipeId = id\n" +
                        "         ) t1\n" +
                        "    where RecipeId = id;\n" +
                        "end;\n" +
                        "$$ language plpgsql;",

                "create or replace function rating_select()\n" +
                        "returns trigger as $$\n" +
                        "begin\n" +
                        "    if tg_op = 'INSERT' then perform rating_adapter(new.RecipeId);\n" +
                        "    elsif tg_op = 'UPDATE' then perform rating_adapter(new.RecipeId);\n" +
                        "    elsif tg_op = 'DELETE' then perform rating_adapter(old.RecipeId);\n" +
                        "    end if;\n" +
                        "    return null;\n" +
                        "end;\n" +
                        "$$ language plpgsql;",

                "create or replace trigger rating_trigger\n" +
                        "after insert or update or delete on reviews\n" +
                        "for each row\n" +
                        "execute function rating_select();",

                "create table like_review (\n" +
                        "    AuthorId bigint,\n" +
                        "    LikeReviewId bigint,\n" +
                        "    constraint pk_like_review primary key (AuthorId, LikeReviewId),\n" +
                        "    constraint fk1_like_review foreign key (AuthorId) references users(AuthorId),\n" +
                        "    constraint fk2_like_review foreign key (LikeReviewId) references reviews(ReviewId) on delete cascade\n" +
                        ");",

                "create index like_review_index on like_review(LikeReviewId);"
        };

        for (String sql : createTableSQLs) {
            jdbcTemplate.execute(sql);
        }

    }



    /*
     * The following code is just a quick example of using jdbc datasource.
     * Practically, the code interacts with database is usually written in a DAO layer.
     *
     * Reference: [Data Access Object pattern](https://www.baeldung.com/java-dao-pattern)
     */

    @Override
    public void drop() {
        // You can use the default drop script provided by us in most cases,
        // but if it doesn't work properly, you may need to modify it.
        // This method will delete all the tables in the public schema.

        String sql = "DO $$\n" +
                "DECLARE\n" +
                "    tables CURSOR FOR\n" +
                "        SELECT tablename\n" +
                "        FROM pg_tables\n" +
                "        WHERE schemaname = 'public';\n" +
                "BEGIN\n" +
                "    FOR t IN tables\n" +
                "    LOOP\n" +
                "        EXECUTE 'DROP TABLE IF EXISTS ' || QUOTE_IDENT(t.tablename) || ' CASCADE;';\n" +
                "    END LOOP;\n" +
                "END $$;\n";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Integer sum(int a, int b) {
        String sql = "SELECT ?+?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, a);
            stmt.setInt(2, b);
            log.info("SQL: {}", stmt);

            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
