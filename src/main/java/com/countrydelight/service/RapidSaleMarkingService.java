package com.countrydelight.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.countrydelight.api.ApiClient;
import com.countrydelight.db.DatabaseUtil;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RapidSaleMarkingService {

    @Autowired
    private ApiClient apiClient;

    @Autowired
    private DatabaseUtil dbUtil;

    private static final String ELIGIBLE_MESSAGE = "Customer is rapid eligible";
    private static final String NOT_ELIGIBLE_MESSAGE = "Customer is not rapid eligible";

    // Target franchise that the selected address must be normalized to before
    // the rapid order flow proceeds (franchise 57 for the rapid delivery route).
    private static final Integer TARGET_FRANCHISE = 57;

    // Cache: customerId -> latest franchise id (auto-selected from newest address).
    // Used in later steps (Step-3 product fetch by franchise).
    private final Map<String, Integer> franchiseCache = new ConcurrentHashMap<>();

    /**
     * Check rapid sale marking eligibility for a customer.
     *
     * Queries the CMS getCustomerDetails API and inspects the
     * "instant_eligibility" and "instant_polygon" keys. The customer is
     * considered rapid eligible only when BOTH keys are true.
     *
     * @param customerId the CMS/DB customer id (e.g. 9935686)
     */
    public Map<String, Object> checkRapidEligibility(String customerId) throws Exception {
        Map<String, Object> details = apiClient.getCustomerDetails(customerId);
        return evaluateRapidEligibility(customerId, details);
    }

    /**
     * Evaluate rapid eligibility from already-fetched customer details.
     * Reuses a single getCustomerDetails call when the caller has it available.
     */
    public Map<String, Object> evaluateRapidEligibility(String customerId, Map<String, Object> details) {
        Map<String, Object> result = new HashMap<>();
        result.put("customerId", customerId);
        result.put("rapidEligible", false);
        result.put("instantEligibility", false);
        result.put("instantPolygon", false);
        result.put("message", NOT_ELIGIBLE_MESSAGE);

        if (details == null) {
            result.put("message", "Customer details not available - unable to determine rapid eligibility");
            return result;
        }

        boolean instantEligibility = isTrue(details.get("instant_eligibility"));
        boolean instantPolygon = isTrue(details.get("instant_polygon"));

        result.put("instantEligibility", instantEligibility);
        result.put("instantPolygon", instantPolygon);

        if (instantEligibility && instantPolygon) {
            result.put("rapidEligible", true);
            result.put("message", ELIGIBLE_MESSAGE);
        }
        return result;
    }

    /**
     * Step-2: Fetch the customer's addresses from the rapiddelivery database,
     * auto-select the newest one (id DESC) and cache its franchise id.
     *
     * @param customerId the customer DB id (e.g. 9935686)
     * @return map with addresses list, selected address and cached franchise id
     */
    public Map<String, Object> getAddressesWithAutoSelect(String customerId) throws Exception {
        Map<String, Object> result = new HashMap<>();

        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be empty");
        }

        List<Map<String, Object>> addresses = dbUtil.getAddressesByCustomer(customerId);

        if (addresses.isEmpty()) {
            result.put("success", false);
            result.put("message", "No addresses found for customer " + customerId);
            return result;
        }

        // Newest address = first row (ORDER BY id DESC)
        Map<String, Object> selectedAddress = addresses.get(0);
        Integer franchiseId = extractFranchiseId(selectedAddress);
        Integer addressId = selectedAddress.get("id") instanceof Number
                ? ((Number) selectedAddress.get("id")).intValue() : null;
        Integer originalFranchiseId = franchiseId;

        // Normalize the selected address's franchise to the target franchise (57):
        // if it differs, update the address row in the DB, then use the target
        // franchise for the rest of the flow (products + order placement).
        if (addressId != null && !TARGET_FRANCHISE.equals(franchiseId)) {
            System.out.println("⚡ Normalizing selected address " + addressId + " franchise " + franchiseId + " -> " + TARGET_FRANCHISE);
            dbUtil.updateAddressFranchise(addressId, TARGET_FRANCHISE);
            franchiseId = TARGET_FRANCHISE;
            selectedAddress.put("franchise", franchiseId);
            // Keep the returned list consistent with the update
            for (Map<String, Object> addr : addresses) {

                if (addr.get("id") instanceof Number && ((Number) addr.get("id")).intValue() == addressId
                        || (addr.get("id") != null && addr.get("id").toString().equals(String.valueOf(addressId)))) {
                    addr.put("franchise", franchiseId);
                }
            }
        }

        if (franchiseId != null) {
            franchiseCache.put(customerId, franchiseId);
            System.out.println("⚡ Cached franchise " + franchiseId + " for customer " + customerId);
        }

        result.put("success", true);
        result.put("customerId", customerId);
        result.put("addresses", addresses);
        result.put("addressCount", addresses.size());
        result.put("selectedAddress", selectedAddress);
        result.put("selectedAddressId", selectedAddress.get("id"));
        result.put("franchiseId", franchiseId);
        result.put("franchiseCached", franchiseId != null);
        result.put("franchiseNormalized", addressId != null && !TARGET_FRANCHISE.equals(originalFranchiseId));
        result.put("message", franchiseId != null
                ? "Newest address selected with franchise " + franchiseId + " (normalized to target franchise)"
                : "Newest address auto-selected but no franchise id could be resolved");
        return result;
    }

    /**
     * Step-3: Fetch the products available for a franchise from the
     * rapiddelivery database.
     *
     * Resolves product ids via product_franchise_detail (FRANCHISE = ?, active=1)
     * and fetches the full product records from the `product` table.
     *
     * @param franchiseId the franchise id resolved from the customer's address
     */
    public Map<String, Object> getProductsByFranchise(Integer franchiseId) throws Exception {
        Map<String, Object> result = new HashMap<>();

        if (franchiseId == null) {
            throw new IllegalArgumentException("Franchise ID cannot be null");
        }

        List<Map<String, Object>> franchiseDetails = dbUtil.getProductFranchiseDetails(franchiseId);

        // Apply active=1 filter and collect product ids
        List<Integer> productIds = new ArrayList<>();
        for (Map<String, Object> detail : franchiseDetails) {
            if (!isActive(detail)) {
                continue;
            }
            Object productIdObj = detail.get("product");
            if (productIdObj == null) {
                productIdObj = detail.get("product_id");
            }
            if (productIdObj != null) {
                productIds.add(((Number) productIdObj).intValue());
            }
        }

        List<Map<String, Object>> products = dbUtil.getProductsByIds(productIds);

        result.put("success", true);
        result.put("franchiseId", franchiseId);
        result.put("franchiseDetailsCount", franchiseDetails.size());
        result.put("productCount", products.size());
        result.put("productIds", productIds);
        result.put("data", products);
        result.put("message", "Fetched " + products.size() + " product(s) for franchise " + franchiseId);
        System.out.println("⚡ Products for franchise " + franchiseId + ": " + products.size());
        return result;
    }

    /**
     * Returns the cached franchise id for a customer (auto-selected in Step-2).
     */
    public Integer getCachedFranchise(String customerId) {
        return franchiseCache.get(customerId);
    }

    /**
     * Step-4: Place a rapid order for the customer's selected products.
     *
     * Flow:
     *  1. Resolve the customer's refresh_token from customer_token (beejapuri DB)
     *  2. Exchange it for a rapid API auth token via /auth/customerApp
     *  3. For each product fetch the product_franchise_detail row (by
     *     product + franchise) to build category_id, mrp,
     *     product_franchise_detail_id and selling_price
     *  4. POST the order payload to /api/order
     *
     * @param customerId the customer DB id (e.g. 9935686)
     * @param franchiseId the franchise id resolved from the customer's address
     * @param products    selected products: list of maps with "product" (id) and "quantity"
     * @return full order response body
     */
    public Map<String, Object> placeRapidOrder(String customerId, Integer franchiseId, List<Map<String, Object>> products) throws Exception {
        return handleRapidOrder(customerId, franchiseId, products, "PLACE_AND_MARK", null);
    }

    /**
     * Step-4: Handle a rapid order with a chosen scenario.
     *
     * Scenarios (mode):
     *  - PLACE_AND_MARK (default): the order is placed via the rapid API
     *    for the selected franchise + products, then it is ready for the
     *    sale marking automation.
     *  - ONLY_MARK: the order was already placed (by the customer/app), so
     *    no new order is created — the latest already-placed order for the
     *    customer is fetched and returned for the sale marking automation.
     *
     * The selected address's franchise is normalized to the target franchise
     * (57) before the rest of the flow runs, so the order is placed for the
     * correct rapid franchise.
     *
     * @param customerId        the customer DB id (e.g. 9935686)
     * @param franchiseId       the franchise id resolved from the customer's address
     * @param products          selected products (required for PLACE_AND_MARK only)
     * @param mode              "PLACE_AND_MARK" or "ONLY_MARK"
     * @param selectedAddressId the customer's selected address row id (optional)
     * @return map with success, mode, order info and message
     */
    public Map<String, Object> handleRapidOrder(String customerId, Integer franchiseId, List<Map<String, Object>> products, String mode, Integer selectedAddressId) throws Exception {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be empty");
        }

        String orderMode = mode == null || mode.trim().isEmpty() ? "PLACE_AND_MARK" : mode.trim().toUpperCase();
        if (!"PLACE_AND_MARK".equals(orderMode) && !"ONLY_MARK".equals(orderMode)) {
            throw new IllegalArgumentException("Invalid mode '" + mode + "'. Use PLACE_AND_MARK or ONLY_MARK.");
        }

        // Normalize the selected address's franchise to the target franchise (57)
        // before the rest of the flow (products fetch + order placement).
        Integer resolvedFranchise = normalizeSelectedAddressFranchise(customerId, selectedAddressId, franchiseId);

        if ("ONLY_MARK".equals(orderMode)) {
            return onlyMarkOrder(customerId, resolvedFranchise);
        }
        return placeAndMarkOrder(customerId, resolvedFranchise, products);
    }

    /**
     * Ensures the selected address's franchise is the target franchise (57).
     *
     * When a selectedAddressId is provided, the address row's franchise is
     * checked and updated to the target franchise if it differs. If no address
     * id is given, falls back to the franchise passed in (already normalized by
     * Step-2's getAddressesWithAutoSelect).
     */
    private Integer normalizeSelectedAddressFranchise(String customerId, Integer selectedAddressId, Integer franchiseId) throws Exception {
        if (selectedAddressId != null) {
            Map<String, Object> current = dbUtil.getAddressById(selectedAddressId);
            if (current != null) {
                Integer currentFranchise = extractFranchiseId(current);
                if (!TARGET_FRANCHISE.equals(currentFranchise)) {
                    System.out.println("⚡ Normalizing selected address " + selectedAddressId + " franchise " + currentFranchise + " -> " + TARGET_FRANCHISE);
                    dbUtil.updateAddressFranchise(selectedAddressId, TARGET_FRANCHISE);
                }
                return TARGET_FRANCHISE;
            }
        }
        return franchiseId != null ? franchiseId : TARGET_FRANCHISE;
    }

    private Map<String, Object> placeAndMarkOrder(String customerId, Integer franchiseId, List<Map<String, Object>> products) throws Exception {
        Map<String, Object> result = new HashMap<>();

        if (franchiseId == null) {
            throw new IllegalArgumentException("Franchise ID cannot be null");
        }
        if (products == null || products.isEmpty()) {
            throw new IllegalArgumentException("No products selected for the rapid order");
        }

        // 1. Get the customer's refresh token (latest) from customer_token
        String refreshToken = dbUtil.getCustomerToken(customerId);
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new RuntimeException("No refresh token found for customer " + customerId + ". Cannot authenticate rapid order.");
        }

        // 2. Exchange refresh token for a rapid API auth token
        String rapidToken = apiClient.getRapidAuthToken(refreshToken);

        // 3. Build product_list from product_franchise_detail
        List<Map<String, Object>> productList = new ArrayList<>();
        for (Map<String, Object> selection : products) {
            Integer productId = productIdOf(selection);
            Integer quantity = quantityOf(selection);
            if (productId == null || quantity == null || quantity <= 0) {
                throw new IllegalArgumentException("Each product needs a valid id and quantity > 0");
            }

            Map<String, Object> pfd = dbUtil.getProductFranchiseDetailByProductAndFranchise(productId, franchiseId);
            if (pfd == null) {
                throw new RuntimeException("No product_franchise_detail for product " + productId + " franchise " + franchiseId);
            }

            Map<String, Object> item = new HashMap<>();
            item.put("category_id", pfd.get("product_category"));
            item.put("mrp", asDouble(pfd.get("product_price")));
            item.put("product", productId);
            item.put("product_franchise_detail_id", pfd.get("id"));
            item.put("quantity", quantity);
            item.put("selling_price", asDouble(pfd.get("new_selling_price")));
            productList.add(item);
        }

        // 4. Build the order payload
        Map<String, Object> orderPayload = new HashMap<>();
        orderPayload.put("applied_offer_ids", new ArrayList<>());
        orderPayload.put("daily_vip_amount", 0.0);
        orderPayload.put("product_list", productList);
        orderPayload.put("rescheduled", false);
        orderPayload.put("special_price", 0);
        orderPayload.put("timestamp", System.currentTimeMillis() / 1000);

        String responseBody = apiClient.placeRapidOrder(rapidToken, orderPayload);

        result.put("success", true);
        result.put("mode", "PLACE_AND_MARK");
        result.put("customerId", customerId);
        result.put("franchiseId", franchiseId);
        result.put("productCount", productList.size());
        result.put("orderPayload", orderPayload);
        result.put("response", responseBody);
        result.put("message", "Rapid order placed with " + productList.size() + " product(s)");
        System.out.println("⚡ Rapid order placed for customer " + customerId + " with " + productList.size() + " product(s)");
        return result;
    }

    private Map<String, Object> onlyMarkOrder(String customerId, Integer franchiseId) throws Exception {
        Map<String, Object> result = new HashMap<>();

        Map<String, Object> latestOrder = dbUtil.getLatestOrderByCustomer(customerId);
        if (latestOrder == null) {
            throw new RuntimeException("No already-placed rapid order found for customer " + customerId + ". Nothing to mark.");
        }

        Integer orderFranchise = franchiseId;
        if (orderFranchise == null) {
            Object franchiseObj = latestOrder.get("franchise");
            if (franchiseObj instanceof Number) {
                orderFranchise = ((Number) franchiseObj).intValue();
            } else if (franchiseObj != null) {
                try {
                    orderFranchise = Integer.parseInt(franchiseObj.toString());
                } catch (NumberFormatException ignored) {
                }
            }
        }

        result.put("success", true);
        result.put("mode", "ONLY_MARK");
        result.put("customerId", customerId);
        result.put("franchiseId", orderFranchise);
        result.put("orderId", latestOrder.get("id"));
        result.put("orderNumber", latestOrder.get("order_number"));
        result.put("orderStatus", latestOrder.get("status"));
        result.put("order", latestOrder);
        result.put("message", "Order already placed — latest rapid order " + latestOrder.get("order_number") + " fetched for sale marking");
        System.out.println("⚡ ONLY_MARK: latest rapid order " + latestOrder.get("order_number") + " fetched for customer " + customerId);
        return result;
    }

    /**
     * Fetch the latest already-placed rapid order for a customer (Only Sale Mark
     * scenario). Returns the order row map (lowercase keys) or null if none.
     */
    public Map<String, Object> getLatestOrder(String customerId) throws Exception {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be empty");
        }
        return dbUtil.getLatestOrderByCustomer(customerId);
    }

    private Integer productIdOf(Map<String, Object> selection) {
        Object value = selection.get("id");
        if (value == null) value = selection.get("product");
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.parseInt(value.toString()); } catch (NumberFormatException e) { return null; }
    }

    private Integer quantityOf(Map<String, Object> selection) {
        Object value = selection.get("quantity");
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.parseInt(value.toString().trim()); } catch (NumberFormatException e) { return null; }
    }

    private Double asDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try { return Double.parseDouble(value.toString().trim()); } catch (NumberFormatException e) { return null; }
    }

    private Integer extractFranchiseId(Map<String, Object> address) {
        Object franchise = address.get("franchise");
        if (franchise == null) {
            franchise = address.get("last_franchise");
        }
        if (franchise == null) {
            franchise = address.get("rapid_franchise");
        }
        if (franchise == null) {
            return null;
        }
        if (franchise instanceof Number) {
            return ((Number) franchise).intValue();
        }
        try {
            return Integer.parseInt(franchise.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isActive(Map<String, Object> row) {
        Object active = row.get("active");
        return isTrue(active);
    }

    private boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value)
                || (value instanceof String
                    && ("true".equalsIgnoreCase((String) value) || "1".equals(value)))
                || (value instanceof Number && ((Number) value).intValue() == 1);
    }
}