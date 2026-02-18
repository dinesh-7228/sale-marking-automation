@Component
public class ApiClient {

    private String BASE_URL = "https://qa-cms.countrydelight.in";

    public String placeOrder(String customerId, List<Integer> productIds, List<Integer> qty) {

        Map<String, Object> body = new HashMap<>();
        body.put("customer_id", customerId);
        body.put("order_amount", -1);

        // Build subscriptions dynamically
        List<Map<String, Object>> subscriptions = new ArrayList<>();

        for (int i = 0; i < productIds.size(); i++) {
            Map<String, Object> sub = new HashMap<>();
            sub.put("id", 0);
            sub.put("quantity", qty.get(i));
            sub.put("order_start_date", LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
            sub.put("source", "CMS");
            sub.put("time_slot", 13);

            Map<String, Object> product = new HashMap<>();
            product.put("id", productIds.get(i));

            sub.put("product", product);

            subscriptions.add(sub);
        }

        body.put("subscriptions", subscriptions);

        return RestAssured.given()
                .header("Authorization", "YOUR_TOKEN")
                .contentType(ContentType.JSON)
                .body(body)
                .post(BASE_URL + "/admin/customers/v1/placeOrder")
                .then()
                .extract().asString();
    }

    public void generateRouteSheet(String customerId) {
        RestAssured.given()
                .post(BASE_URL + "/api/voice/generateRouteSheetByCustomerId?customerId=" + customerId);
    }

    public void saleMarking(String customerId, List<Integer> productIds, List<Integer> qty) {

        List<Map<String, Object>> products = new ArrayList<>();

        for (int i = 0; i < productIds.size(); i++) {
            Map<String, Object> p = new HashMap<>();
            p.put("change_type", "");
            p.put("deliveryId", 634737981);
            p.put("id", productIds.get(i));
            p.put("is_frozen", false);
            p.put("is_packaging_required", true);
            p.put("quantity", qty.get(i));
            products.add(p);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("delivered", true);
        data.put("delivery", 634737981);
        data.put("delivery_id", 634737981);
        data.put("delivery_time", System.currentTimeMillis() / 1000);
        data.put("products", products);

        Map<String, Object> finalBody = new HashMap<>();
        finalBody.put("action", "test");
        finalBody.put("data", data);

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(finalBody)
                .post(BASE_URL + "/api/delivery/sale_create");
    }
}
