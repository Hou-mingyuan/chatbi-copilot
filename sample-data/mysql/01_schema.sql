-- ChatBI Copilot demo schema (MySQL) - a small retail/sales model.
-- Table and column COMMENTs are intentionally rich: the app reads them to build LLM prompts.

CREATE DATABASE IF NOT EXISTS chatbi_demo DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE chatbi_demo;

DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS customers;

CREATE TABLE customers (
    id         INT PRIMARY KEY AUTO_INCREMENT COMMENT '客户ID',
    name       VARCHAR(64)  NOT NULL          COMMENT '客户姓名',
    region     VARCHAR(32)                    COMMENT '所属大区(华东/华北/华南/华中/西南)',
    city       VARCHAR(32)                    COMMENT '所在城市',
    level      VARCHAR(16)                    COMMENT '客户等级(VIP/普通)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间'
) COMMENT='客户表';

CREATE TABLE products (
    id       INT PRIMARY KEY AUTO_INCREMENT COMMENT '产品ID',
    name     VARCHAR(64) NOT NULL           COMMENT '产品名称',
    category VARCHAR(32)                    COMMENT '产品类目(手机/电脑/配件/家电)',
    price    DECIMAL(12,2)                  COMMENT '销售单价(元)',
    cost     DECIMAL(12,2)                  COMMENT '成本单价(元)'
) COMMENT='产品表';

CREATE TABLE orders (
    id           INT PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID',
    customer_id  INT NOT NULL                   COMMENT '下单客户ID',
    order_date   DATE NOT NULL                  COMMENT '下单日期',
    status       VARCHAR(16) NOT NULL           COMMENT '订单状态(paid已支付/pending待支付/refunded已退款)',
    channel      VARCHAR(16)                    COMMENT '销售渠道(线上/线下)',
    total_amount DECIMAL(12,2)                  COMMENT '订单总金额(元)',
    KEY idx_orders_date (order_date),
    KEY idx_orders_customer (customer_id),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
) COMMENT='销售订单表';

CREATE TABLE order_items (
    id         INT PRIMARY KEY AUTO_INCREMENT COMMENT '明细ID',
    order_id   INT NOT NULL                   COMMENT '订单ID',
    product_id INT NOT NULL                   COMMENT '产品ID',
    quantity   INT NOT NULL                   COMMENT '购买数量',
    unit_price DECIMAL(12,2)                  COMMENT '成交单价(元)',
    amount     DECIMAL(12,2)                  COMMENT '小计金额(元)',
    KEY idx_items_order (order_id),
    KEY idx_items_product (product_id),
    CONSTRAINT fk_items_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_items_product FOREIGN KEY (product_id) REFERENCES products(id)
) COMMENT='订单明细表';
