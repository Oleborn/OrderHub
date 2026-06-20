CREATE TABLE orders(
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(50) NOT NULL,
    creat_at TIMESTAMP WITH TIME ZONE NOT NULL

--  из-за H2 которая работает под капотом у JOOQ вынуждены использовать CHECK
    CONSTRAINT check_status CHECK (status IN ('CREATED', 'PAID', 'PENDING', 'CANCELLED', 'AWAITING_PAYMENT'))
);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT,
    product_id BIGINT not null,
    product_name VARCHAR(255) not null,
    quantity INTEGER NOT NULL CHECK ( quantity > 0 ),
    price DECIMAL(10, 2) NOT NULL CHECK ( price >= 0 ),

    CONSTRAINT fk_order_items_order
                         FOREIGN KEY (order_id)
                         REFERENCES orders(id)
                         ON DELETE CASCADE

);

CREATE INDEX idx_order_items_id ON order_items(order_id);
CREATE INDEX idx_order_status ON orders(status);


-- ENUM даёт строгую типизацию и лучше отражает бизнес-логику.
-- Значения перечисления хранятся компактно (по 4 байта вместо текста).
-- Индексы и запросы с ENUM работают быстрее, чем с текстовым полем + CHECK.

-- CREATE TYPE order_status AS ENUM ('CREATED', 'PAID', 'PENDING', 'CANCELLED', 'AWAITING_PAYMENT');