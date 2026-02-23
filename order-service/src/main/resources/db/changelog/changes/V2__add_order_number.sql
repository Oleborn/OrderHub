ALTER TABLE orders ADD COLUMN order_number VARCHAR(255);

ALTER TABLE orders RENAME COLUMN creat_at TO create_at;