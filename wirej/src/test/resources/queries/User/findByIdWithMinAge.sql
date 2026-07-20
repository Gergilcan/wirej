SELECT *
FROM users
where id = :id AND :min_age >= 0
