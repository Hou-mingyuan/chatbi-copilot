-- ChatBI Copilot demo schema (PostgreSQL).
-- Run against an existing database, e.g.:  createdb chatbi_demo && psql -d chatbi_demo -f 01_schema.sql

DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS customers;

CREATE TABLE customers (
    id         INT PRIMARY KEY,
    name       VARCHAR(64) NOT NULL,
    region     VARCHAR(32),
    city       VARCHAR(32),
    level      VARCHAR(16),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE customers IS '客户表';
COMMENT ON COLUMN customers.id IS '客户ID';
COMMENT ON COLUMN customers.name IS '客户姓名';
COMMENT ON COLUMN customers.region IS '所属大区(华东/华北/华南/华中/西南)';
COMMENT ON COLUMN customers.city IS '所在城市';
COMMENT ON COLUMN customers.level IS '客户等级(VIP/普通)';
COMMENT ON COLUMN customers.created_at IS '注册时间';

CREATE TABLE products (
    id       INT PRIMARY KEY,
    name     VARCHAR(64) NOT NULL,
    category VARCHAR(32),
    price    DECIMAL(12,2),
    cost     DECIMAL(12,2)
);
COMMENT ON TABLE products IS '产品表';
COMMENT ON COLUMN products.id IS '产品ID';
COMMENT ON COLUMN products.name IS '产品名称';
COMMENT ON COLUMN products.category IS '产品类目(手机/电脑/配件/家电)';
COMMENT ON COLUMN products.price IS '销售单价(元)';
COMMENT ON COLUMN products.cost IS '成本单价(元)';

CREATE TABLE orders (
    id           INT PRIMARY KEY,
    customer_id  INT NOT NULL REFERENCES customers(id),
    order_date   DATE NOT NULL,
    status       VARCHAR(16) NOT NULL,
    channel      VARCHAR(16),
    total_amount DECIMAL(12,2)
);
COMMENT ON TABLE orders IS '销售订单表';
COMMENT ON COLUMN orders.id IS '订单ID';
COMMENT ON COLUMN orders.customer_id IS '下单客户ID';
COMMENT ON COLUMN orders.order_date IS '下单日期';
COMMENT ON COLUMN orders.status IS '订单状态(paid已支付/pending待支付/refunded已退款)';
COMMENT ON COLUMN orders.channel IS '销售渠道(线上/线下)';
COMMENT ON COLUMN orders.total_amount IS '订单总金额(元)';

CREATE TABLE order_items (
    id         INT PRIMARY KEY,
    order_id   INT NOT NULL REFERENCES orders(id),
    product_id INT NOT NULL REFERENCES products(id),
    quantity   INT NOT NULL,
    unit_price DECIMAL(12,2),
    amount     DECIMAL(12,2)
);
COMMENT ON TABLE order_items IS '订单明细表';
COMMENT ON COLUMN order_items.id IS '明细ID';
COMMENT ON COLUMN order_items.order_id IS '订单ID';
COMMENT ON COLUMN order_items.product_id IS '产品ID';
COMMENT ON COLUMN order_items.quantity IS '购买数量';
COMMENT ON COLUMN order_items.unit_price IS '成交单价(元)';
COMMENT ON COLUMN order_items.amount IS '小计金额(元)';
