SELECT *
FROM users
:filters :sorting
OFFSET :initialPosition ROWS FETCH NEXT :pageSize ROWS ONLY