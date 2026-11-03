-- Reference data: the starting set of food categories. Admins can change them through the API.
INSERT INTO categories (name, description, display_order, status, created_at, updated_at, version)
VALUES ('Pizza', 'Pizzas and flatbreads', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
       ('Burgers', 'Burgers and sandwiches', 2, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
       ('Biryani', 'Biryani and pulao', 3, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
       ('Chinese', 'Indo-Chinese and Chinese dishes', 4, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
       ('South Indian', 'Dosa, idli, vada and meals', 5, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
       ('North Indian', 'Curries, breads and tandoor', 6, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
       ('Desserts', 'Sweets, cakes and ice cream', 7, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
       ('Beverages', 'Hot and cold drinks', 8, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0);
