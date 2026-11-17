
const API = "/api";

let products = [];
let cart = [];

// Load products from Spring Boot
async function loadProducts() {
    try {
        const response = await fetch(`${API}/products`);

        if (!response.ok) {
            throw new Error("Unable to load products");
        }

        products = await response.json();
        displayProducts(products);

    } catch (error) {
        document.getElementById("productList").innerHTML =
            "<p>Unable to load products. Please try again.</p>";

        console.error(error);
    }
}

// Display products
function displayProducts(list) {
    const container = document.getElementById("productList");

    if (list.length === 0) {
        container.innerHTML = "<p>No products available.</p>";
        return;
    }

    container.innerHTML = list.map(product => `
        <div class="product-card">
            <img
                src="https://placehold.co/300x200?text=Product"
                alt="${escapeHTML(product.name)}"
            >

            <h3>${escapeHTML(product.name)}</h3>

            <p>${escapeHTML(product.description || "")}</p>

            <p class="price">₹${Number(product.price).toLocaleString("en-IN")}</p>

            <p>Stock: ${product.stock}</p>

            <button
                onclick="addToCart(${product.id})"
                ${product.stock <= 0 ? "disabled" : ""}
            >
                ${product.stock <= 0 ? "Out of Stock" : "Add to Cart"}
            </button>
        </div>
    `).join("");
}

// Escape product text before displaying it
function escapeHTML(value) {
    return String(value).replace(/[&<>"']/g, char => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    })[char]);
}

// Search products
function searchProducts() {
    const search = document
        .getElementById("searchInput")
        .value.toLowerCase();

    const filtered = products.filter(product =>
        product.name.toLowerCase().includes(search)
    );

    displayProducts(filtered);
}

// Add product to cart
function addToCart(id) {
    const product = products.find(p => p.id === id);

    if (!product || product.stock <= 0) return;

    const existing = cart.find(item => item.id === id);

    if (existing) {
        if (existing.quantity >= product.stock) {
            alert("Maximum available stock reached");
            return;
        }

        existing.quantity++;
    } else {
        cart.push({
            ...product,
            quantity: 1
        });
    }

    updateCart();
    alert("Product added to cart!");
}

// Update cart
function updateCart() {
    const count = cart.reduce(
        (sum, item) => sum + item.quantity, 0
    );

    document.getElementById("cartCount").textContent = count;

    const container = document.getElementById("cartItems");

    container.innerHTML = cart.map(item => `
        <div class="cart-item">
            <h3>${escapeHTML(item.name)}</h3>

            <p>₹${item.price} × ${item.quantity}</p>

            <button onclick="changeQuantity(${item.id}, -1)">-</button>

            ${item.quantity}

            <button onclick="changeQuantity(${item.id}, 1)">+</button>

            <button onclick="removeFromCart(${item.id})">
                Remove
            </button>
        </div>
    `).join("");

    const total = cart.reduce(
        (sum, item) => sum + Number(item.price) * item.quantity, 0
    );

    document.getElementById("cartTotal").textContent =
        "Total: ₹" + total.toLocaleString("en-IN");
}

// Change quantity
function changeQuantity(id, change) {
    const item = cart.find(p => p.id === id);
    const product = products.find(p => p.id === id);

    if (!item || !product) return;

    const newQuantity = item.quantity + change;

    if (newQuantity <= 0) {
        removeFromCart(id);
        return;
    }

    if (newQuantity > product.stock) {
        alert("Not enough stock available");
        return;
    }

    item.quantity = newQuantity;
    updateCart();
}

// Remove product
function removeFromCart(id) {
    cart = cart.filter(item => item.id !== id);
    updateCart();
}

// Show cart
function showCart() {
    document.getElementById("cartSection").classList.remove("hidden");
    document.getElementById("ordersSection").classList.add("hidden");

    document.getElementById("cartSection").scrollIntoView({
        behavior: "smooth"
    });

    updateCart();
}

// Close cart
function closeCart() {
    document.getElementById("cartSection").classList.add("hidden");
}

// Checkout
async function checkout() {
    const customerName = document
        .getElementById("customerName").value.trim();

    if (!customerName) {
        alert("Please enter your name");
        return;
    }

    if (cart.length === 0) {
        alert("Your cart is empty");
        return;
    }

    const button = document.querySelector(
        "#cartSection button[onclick='checkout()']"
    );

    button.disabled = true;

    try {
        // Current backend accepts one product per order.
        // Place each cart line separately.
        const completedOrders = [];

        for (const item of cart) {
            const response = await fetch(`${API}/orders`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    customerName: customerName,
                    productId: item.id,
                    quantity: item.quantity
                })
            });

            const result = await response.json();

            if (!response.ok) {
                throw new Error(
                    result.error || "Order failed"
                );
            }

            completedOrders.push(result);
        }

        alert(
            completedOrders.length + " order(s) placed successfully!"
        );

        cart = [];
        updateCart();

        document.getElementById("customerName").value = "";

        await loadProducts();

        closeCart();

    } catch (error) {
        alert(error.message + ". Please check your orders before retrying.");
        await loadProducts();
    } finally {
        button.disabled = false;
    }
}

// View orders
async function showOrders() {
    document.getElementById("cartSection").classList.add("hidden");
    document.getElementById("ordersSection").classList.remove("hidden");

    const container = document.getElementById("ordersList");

    container.innerHTML = "Loading orders...";

    try {
        const response = await fetch(`${API}/orders`);

        if (!response.ok) {
            throw new Error("Unable to load orders");
        }

        const orders = await response.json();

        if (orders.length === 0) {
            container.innerHTML = "<p>No orders found.</p>";
            return;
        }

        container.innerHTML = orders.map(order => `
            <div class="cart-item">
                <h3>Order #${order.order_id}</h3>
                <p>Customer: ${escapeHTML(order.customer_name)}</p>
                <p>Product: ${escapeHTML(order.product_name)}</p>
                <p>Quantity: ${order.quantity}</p>
                <p>Total: ₹${Number(order.total).toLocaleString("en-IN")}</p>
            </div>
        `).join("");

    } catch (error) {
        container.innerHTML = "<p>Unable to load orders.</p>";
    }

    document.getElementById("ordersSection").scrollIntoView({
        behavior: "smooth"
    });
}

function closeOrders() {
    document.getElementById("ordersSection").classList.add("hidden");
}

// Load products when website opens
document.addEventListener("DOMContentLoaded", loadProducts);
