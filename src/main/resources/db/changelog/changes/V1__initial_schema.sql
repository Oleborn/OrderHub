CREATE TABLE orders(
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(50) NOT NULL,
    creat_at TIMESTAMP WITH TIME ZONE NOT NULL

    CONSTRAINT check_status CHECK ( status IN ('CREATED', 'PAID', 'CANCELLED'))

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