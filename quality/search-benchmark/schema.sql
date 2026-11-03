SET GLOBAL local_infile=1;
DROP TABLE IF EXISTS restaurants; DROP TABLE IF EXISTS users;
-- The same columns and indexes as restaurant_db.restaurants / user_db.users (see SHOW CREATE TABLE), only the columns the queries touch
CREATE TABLE restaurants (
  id bigint NOT NULL AUTO_INCREMENT, owner_user_id bigint NOT NULL, name varchar(150) NOT NULL, cuisines varchar(255) NOT NULL,
  city varchar(100) NOT NULL, status varchar(20) NOT NULL, created_at datetime(6) NOT NULL,
  legal_business_name varchar(200) NOT NULL, description varchar(1000), latitude decimal(9,6), longitude decimal(9,6),
  PRIMARY KEY (id), KEY idx_restaurants_owner (owner_user_id), KEY idx_restaurants_status_city (status, city), KEY idx_restaurants_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE users (
  id bigint NOT NULL AUTO_INCREMENT, email varchar(255) NOT NULL, password_hash varchar(100) NOT NULL, full_name varchar(100) NOT NULL,
  phone varchar(20) DEFAULT NULL, role varchar(30) NOT NULL, status varchar(20) NOT NULL, created_at datetime(6) NOT NULL, updated_at datetime(6) NOT NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_users_email (email), UNIQUE KEY uk_users_phone (phone), KEY idx_users_role_status (role, status), KEY idx_users_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
