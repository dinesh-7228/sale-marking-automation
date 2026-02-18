@Service
public class SaleMarkingService {

    @Autowired
    private ApiClient apiClient;

    @Autowired
    private DatabaseUtil dbUtil;

    public void executeFlow(String customerId, List<Integer> productIds, List<Integer> qty) {

        // Step 1 - Place Order
        String orderResponse = apiClient.placeOrder(customerId, productIds, qty);

        // Step 2 - Generate Route Sheet
        apiClient.generateRouteSheet(customerId);

        // Step 3 - Update route_sheet_details date
        dbUtil.updateRouteSheetDate(customerId);

        // Step 4 - Update order_detail date
        dbUtil.updateOrderDetailDate(customerId);

        // Step 5 - Sale Marking
        apiClient.saleMarking(customerId, productIds, qty);
    }
}
