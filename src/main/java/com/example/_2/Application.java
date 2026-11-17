
package com.example._2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

@SpringBootApplication
@RestController
@RequestMapping("/api")
public class Application {

    private final JdbcTemplate jdbc;

    public Application(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    // ==========================================
    // DATABASE TABLE CREATION
    // ==========================================

    @Bean
    CommandLineRunner initializeDatabase() {
        return args -> {

            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS products (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(150) NOT NULL,
                    description VARCHAR(500),
                    price DECIMAL(10,2) NOT NULL,
                    stock INT NOT NULL
                )
            """);

            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS orders (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    customer_name VARCHAR(150) NOT NULL,
                    total DECIMAL(10,2) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS order_items (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    order_id BIGINT NOT NULL,
                    product_id BIGINT NOT NULL,
                    quantity INT NOT NULL,
                    price DECIMAL(10,2) NOT NULL,
                    FOREIGN KEY (order_id) REFERENCES orders(id),
                    FOREIGN KEY (product_id) REFERENCES products(id)
                )
            """);

            System.out.println("=================================");
            System.out.println("ECOMMERCE DATABASE READY");
            System.out.println("=================================");
        };
    }

    // ==========================================
    // 1. ADD PRODUCT
    // ==========================================

    @PostMapping("/products")
    public ResponseEntity<?> addProduct(
            @RequestBody ProductRequest product) {

        if (product.name() == null ||
            product.name().isBlank() ||
            product.price() == null ||
            product.price().compareTo(BigDecimal.ZERO) < 0 ||
            product.stock() == null ||
            product.stock() < 0) {

            return ResponseEntity.badRequest().body(
                Map.of("error", "Invalid product details")
            );
        }

        jdbc.update("""
            INSERT INTO products(name, description, price, stock)
            VALUES (?, ?, ?, ?)
            """,
            product.name(),
            product.description(),
            product.price(),
            product.stock()
        );

        return ResponseEntity.ok(
            Map.of("message", "Product added successfully")
        );
    }

    // ==========================================
    // 2. GET ALL PRODUCTS
    // ==========================================

    @GetMapping("/products")
    public List<Map<String, Object>> getProducts() {

        return jdbc.queryForList(
            "SELECT * FROM products ORDER BY id DESC"
        );
    }

    // ==========================================
    // 3. GET PRODUCT BY ID
    // ==========================================

    @GetMapping("/products/{id}")
    public ResponseEntity<?> getProduct(
            @PathVariable Long id) {

        List<Map<String, Object>> result =
            jdbc.queryForList(
                "SELECT * FROM products WHERE id = ?", id
            );

        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(result.get(0));
    }

    // ==========================================
    // 4. UPDATE PRODUCT
    // ==========================================

    @PutMapping("/products/{id}")
    public ResponseEntity<?> updateProduct(
            @PathVariable Long id,
            @RequestBody ProductRequest product) {

        if (product.name() == null ||
            product.name().isBlank() ||
            product.price() == null ||
            product.price().compareTo(BigDecimal.ZERO) < 0 ||
            product.stock() == null ||
            product.stock() < 0) {

            return ResponseEntity.badRequest().body(
                Map.of("error", "Invalid product details")
            );
        }

        int updated = jdbc.update("""
            UPDATE products
            SET name = ?, description = ?, price = ?, stock = ?
            WHERE id = ?
            """,
            product.name(),
            product.description(),
            product.price(),
            product.stock(),
            id
        );

        if (updated == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(
            Map.of("message", "Product updated successfully")
        );
    }

    // ==========================================
    // 5. DELETE PRODUCT
    // ==========================================

    @DeleteMapping("/products/{id}")
    @Transactional
    public ResponseEntity<?> deleteProduct(
            @PathVariable Long id) {

        List<Map<String, Object>> result =
            jdbc.queryForList(
                "SELECT id FROM products WHERE id = ?", id
            );

        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        int orderCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM order_items WHERE product_id = ?",
            Integer.class,
            id
        );

        if (orderCount > 0) {
            return ResponseEntity.badRequest().body(
                Map.of("error",
                    "Cannot delete product because it has existing orders")
            );
        }

        jdbc.update(
            "DELETE FROM products WHERE id = ?", id
        );

        return ResponseEntity.ok(
            Map.of("message", "Product deleted successfully")
        );
    }

    // ==========================================
    // 6. PLACE ORDER
    // ==========================================

    @PostMapping("/orders")
    @Transactional
    public ResponseEntity<?> placeOrder(
            @RequestBody OrderRequest order) {

        if (order.customerName() == null ||
            order.customerName().isBlank() ||
            order.productId() == null ||
            order.quantity() == null ||
            order.quantity() <= 0) {

            return ResponseEntity.badRequest().body(
                Map.of("error", "Invalid order details")
            );
        }

        // Find product and lock its row
        List<Map<String, Object>> products =
            jdbc.queryForList(
                "SELECT * FROM products WHERE id = ? FOR UPDATE",
                order.productId()
            );

        if (products.isEmpty()) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "Product not found")
            );
        }

        Map<String, Object> product = products.get(0);

        int stock = ((Number) product.get("stock")).intValue();

        if (stock < order.quantity()) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "Insufficient stock")
            );
        }

        BigDecimal price = (BigDecimal) product.get("price");

        BigDecimal total = price.multiply(
            BigDecimal.valueOf(order.quantity())
        );

        // Create order
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbc.update(connection -> {

            PreparedStatement ps =
                connection.prepareStatement(
                    "INSERT INTO orders(customer_name, total) VALUES (?, ?)",
                    new String[]{"id"}
                );

            ps.setString(1, order.customerName());
            ps.setBigDecimal(2, total);

            return ps;

        }, keyHolder);

        Number orderId = keyHolder.getKey();

        if (orderId == null) {
            throw new IllegalStateException(
                "Could not generate order ID"
            );
        }

        // Insert order item
        jdbc.update("""
            INSERT INTO order_items
            (order_id, product_id, quantity, price)
            VALUES (?, ?, ?, ?)
            """,
            orderId.longValue(),
            order.productId(),
            order.quantity(),
            price
        );

        // Reduce product stock
        jdbc.update("""
            UPDATE products
            SET stock = stock - ?
            WHERE id = ?
            """,
            order.quantity(),
            order.productId()
        );

        return ResponseEntity.ok(
            Map.of(
                "message", "Order placed successfully",
                "orderId", orderId,
                "customerName", order.customerName(),
                "productName", product.get("name"),
                "quantity", order.quantity(),
                "total", total
            )
        );
    }

    // ==========================================
    // 7. GET ALL ORDERS
    // ==========================================

    @GetMapping("/orders")
    public List<Map<String, Object>> getOrders() {

        return jdbc.queryForList("""
            SELECT
                o.id AS order_id,
                o.customer_name,
                o.total,
                o.created_at,
                p.name AS product_name,
                oi.product_id,
                oi.quantity,
                oi.price
            FROM orders o
            JOIN order_items oi ON o.id = oi.order_id
            JOIN products p ON oi.product_id = p.id
            ORDER BY o.id DESC
            """);
    }

    // ==========================================
    // 8. GET ORDER BY ID
    // ==========================================

    @GetMapping("/orders/{id}")
    public ResponseEntity<?> getOrder(
            @PathVariable Long id) {

        List<Map<String, Object>> result =
            jdbc.queryForList("""
                SELECT
                    o.id AS order_id,
                    o.customer_name,
                    o.total,
                    o.created_at,
                    p.name AS product_name,
                    oi.product_id,
                    oi.quantity,
                    oi.price
                FROM orders o
                JOIN order_items oi ON o.id = oi.order_id
                JOIN products p ON oi.product_id = p.id
                WHERE o.id = ?
                """, id);

        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(result);
    }

    // ==========================================
    // REQUEST DTOs
    // ==========================================

    public record ProductRequest(
        String name,
        String description,
        BigDecimal price,
        Integer stock
    ) {}

    public record OrderRequest(
        String customerName,
        Long productId,
        Integer quantity
    ) {}
}
