package io.sustc.service.impl;

import io.sustc.dto.*;
import io.sustc.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public long register(RegisterUserReq req) {
        String sql = "insert into users (authorname, gender, age, is_deleted, followercount, followeecount, password) " +
                "values(?,?,?,false,0,0,?) returning AuthorId";
        try {
            long id = jdbcTemplate.queryForObject(
                    sql,
                    Long.class,
                    req.getName(),
                    getGender(req.getGender().name()),
                    LocalDate.from(LocalDate.now()).getYear() - LocalDate.parse(req.getBirthday()).getYear(),
                    req.getPassword()
            ).longValue();
            return id;
        } catch (DataIntegrityViolationException e) {
            return -1;
        }
    }

    @Override
    public long login(AuthInfo auth) {
        if(auth == null || auth.getPassword() == null || auth.getPassword().isEmpty()) return -1;
        try {
            String sql = "select Password from users where AuthorId = ? and Is_Deleted = false";
            String pwd = jdbcTemplate.queryForObject(
                    sql,
                    String.class,
                    auth.getAuthorId()
            );
            if(pwd != null && pwd.equals(auth.getPassword())) return auth.getAuthorId();
            else return -1;

        }catch (EmptyResultDataAccessException e) {
            return -1;
        }
    }

    public void checkAuth(AuthInfo auth) {
        if (auth == null) {
            throw new SecurityException("auth error");
        }
        String sql = "select exists (select 1 from users where AuthorId = ? and Is_Deleted = false)";
        Boolean ok = jdbcTemplate.queryForObject(
                sql,
                Boolean.class,
                auth.getAuthorId()
        );

        if (Boolean.FALSE.equals(ok)) {
            throw new SecurityException("auth error");
        }
        if(auth.getPassword() == null || !auth.getPassword().equals(jdbcTemplate.queryForObject(
                "select password from users where authorid = ?",
                String.class, auth.getAuthorId()
        ))) throw new SecurityException();
    }

    @Override
    @Transactional
    public boolean deleteAccount(AuthInfo auth, long userId) {
        checkAuth(auth);

        if (auth.getAuthorId() != userId) {
            throw new SecurityException();
        }
        Integer exists = jdbcTemplate.queryForObject(
                "select count(*) from users where AuthorId = ?",
                Integer.class,
                userId
        );

        if (exists == 0) {
            throw new IllegalArgumentException();
        }

        int updated = jdbcTemplate.update(
                "update users set Is_Deleted = true where AuthorId = ?",
                userId
        );
        if (updated == 0) {
            return false;
        }

        jdbcTemplate.update(
                "delete from follow where FollowerId = ? or FolloweeId = ?",
                userId, userId
        );

        return true;
    }
    private String getGender(String gender) {
        if(gender != null && gender.toUpperCase().equals("MALE")) return "Male";
        if(gender != null && gender.toUpperCase().equals("FEMALE")) return "Female";
        return gender;
    }
    @Override
    @Transactional
    public boolean follow(AuthInfo auth, long followeeId) {
        checkAuth(auth);
        if(auth.getAuthorId() == followeeId) throw new SecurityException();
        Boolean check_followee = jdbcTemplate.queryForObject(
                "select exists (select 1 from users where AuthorId = ? and Is_Deleted = false)",
                Boolean.class,
                followeeId
        );
        if(Boolean.FALSE.equals(check_followee)) throw new SecurityException();
        Integer cnt = jdbcTemplate.queryForObject(
                "select count(*) from follow where (FollowerId, FolloweeId) = (?, ?)",
                Integer.class,
                auth.getAuthorId(), followeeId
        );
        if(cnt == 0) {
            String sql = "insert into follow values (?, ?) on conflict do nothing ";
            jdbcTemplate.update(
                    sql,
                    auth.getAuthorId(),
                    followeeId
            );
        }
        else {
            String sql = "delete from follow where (FollowerId, FolloweeId) = (?, ?)";
            jdbcTemplate.update(
                    sql,
                    auth.getAuthorId(),
                    followeeId
            );
        }
        return true;
    }


    @Override
    public UserRecord getById(long userId) {
        UserRecord userRecord = jdbcTemplate.queryForObject(
                "select * from users where AuthorId = ?",
                (rs, i) -> UserRecord.builder()
                        .authorId(  rs.getLong("AuthorId"))
                        .authorName(rs.getString("AuthorName"))
                        .gender(rs.getString("Gender"))
                        .age(rs.getInt("Age"))
                        .isDeleted(rs.getBoolean("Is_Deleted"))
                        .followers(rs.getInt("FollowerCount"))
                        .following(rs.getInt("FolloweeCount"))
                        .password(rs.getString("Password"))
                        .build(),
                userId
        );

        userRecord.setFollowingUsers(
                jdbcTemplate.query(
                        "select FolloweeId from follow where FollowerId = ?",
                        (rs, i) -> rs.getLong("FolloweeId"),
                        userId
                ).stream().mapToLong(Long::longValue).toArray()
        );

        userRecord.setFollowerUsers(
                jdbcTemplate.query(
                        "select FollowerId from follow where FolloweeId = ?",
                        (rs, i) -> rs.getLong("FollowerId"),
                        userId
                ).stream().mapToLong(Long::longValue).toArray()
        );

        return userRecord;
    }

    @Override
    @Transactional
    public void updateProfile(AuthInfo auth, String gender, Integer age) {
        checkAuth(auth);
        gender = getGender(gender);
        if(gender != null) {
            if(gender.equals("Male") || gender.equals("Female")) {
                jdbcTemplate.update(
                        "update users set Gender = ? where AuthorId = ?",
                        gender, auth.getAuthorId()
                );
            }
        }
        if(age != null && age > 0) {
            jdbcTemplate.update(
                    "update users set Age = ? where AuthorId = ?",
                    age, auth.getAuthorId()
            );
        }
    }

    @Override
    public PageResult<FeedItem> feed(AuthInfo auth, int page, int size, String category) {
        checkAuth(auth);
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 200);
        int offset = (page - 1) * size;
        PageResult<FeedItem> pageResult = new PageResult<>();
        pageResult.setPage(page);
        pageResult.setSize(size);
        if(category == null) {
            pageResult.setItems(
                    jdbcTemplate.query(
                            "select RecipeId, Name, AuthorId, AuthorName, DatePublished, AggregatedRating, ReviewCount \n" +
                                    "from recipes inner join users using (AuthorId) \n" +
                                    "inner join follow on (AuthorId = FolloweeId)\n" +
                                    "where FollowerId = ?\n" +
                                    "order by (DatePublished, RecipeId) desc \n" +
                                    "offset ? limit ?",
                            (rs, i) -> new FeedItem(
                                    rs.getLong("RecipeId"),
                                    rs.getString("Name"),
                                    rs.getLong("AuthorId"),
                                    rs.getString("AuthorName"),
                                    rs.getTimestamp("DatePublished").toInstant().plus(Duration.ofHours(8)),
                                    rs.getDouble("AggregatedRating"),
                                    rs.getInt("ReviewCount")
                            ),
                            auth.getAuthorId(),
                            offset, size
                    )
            );

            pageResult.setTotal(
                    jdbcTemplate.queryForObject(
                            "select count(*) \n" +
                                    "from recipes inner join users using (AuthorId) \n" +
                                    "inner join follow on (AuthorId = FolloweeId)\n" +
                                    "where FollowerId = ?\n",
                            Long.class,
                            auth.getAuthorId()
                    ).longValue()
            );
        }
        else {
            pageResult.setItems(
                    jdbcTemplate.query(
                            "select RecipeId, Name, AuthorId, AuthorName, DatePublished, AggregatedRating, ReviewCount \n" +
                                    "from recipes inner join users using (AuthorId) \n" +
                                    "inner join follow on (AuthorId = FolloweeId)\n" +
                                    "where FollowerId = ? and RecipeCategory = ?\n" +
                                    "order by (DatePublished, RecipeId) desc \n" +
                                    "offset ? limit ?",
                            (rs, i) -> new FeedItem(
                                    rs.getLong("RecipeId"),
                                    rs.getString("Name"),
                                    rs.getLong("AuthorId"),
                                    rs.getString("AuthorName"),
                                    rs.getTimestamp("DatePublished").toInstant().plus(Duration.ofHours(8)),
                                    rs.getDouble("AggregatedRating"),
                                    rs.getInt("ReviewCount")
                            ),
                            auth.getAuthorId(),
                            category, offset, size
                    )
            );

            pageResult.setTotal(
                    jdbcTemplate.queryForObject(
                            "select count(*) \n" +
                                    "from recipes inner join users using (AuthorId) \n" +
                                    "inner join follow on (AuthorId = FolloweeId)\n" +
                                    "where FollowerId = ? and RecipeCategory = ?\n",
                            Long.class,
                            auth.getAuthorId(),
                            category
                    ).longValue()
            );
            for(FeedItem f : pageResult.getItems()) {

                if(!pageResult.getItems().isEmpty()) log.info("check rating: {}",jdbcTemplate.queryForList("select Rating from reviews where recipeid = ?", f.getRecipeId()).toString());

            }
        }
        return pageResult;
    }

    @Override
    public Map<String, Object> getUserWithHighestFollowRatio() {
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                """
                SELECT AuthorId, AuthorName,
                       followercount::float8 / followeecount AS Ratio
                FROM users 
                WHERE Is_Deleted = false and followeecount != 0
                ORDER BY Ratio DESC, AuthorId ASC
                LIMIT 1
                """
        );


        return list.isEmpty() ? null : list.get(0);
    }

}