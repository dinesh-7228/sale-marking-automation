@RestController
public class SaleMarkingController {

    @Autowired
    private SaleMarkingService service;

    @PostMapping("/run")
    public String runFlow(@RequestParam String customerId,
                          @RequestParam List<Integer> productId,
                          @RequestParam List<Integer> qty) {

        service.executeFlow(customerId, productId, qty);
        return "Sale Marking Completed Successfully!";
    }
}
