package trycb.web.credit;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import trycb.service.TransferCreditService;

@RestController
@RequestMapping("/api/credit")
public class CreditController {
    private final TransferCreditService transferCreditService;

    public CreditController(TransferCreditService transferCreditService) {
        this.transferCreditService = transferCreditService;
    }

    @RequestMapping("/transfer")
    public boolean transferCredit(@RequestParam("sourceUser") String sourceUser, @RequestParam("targetUser") String targetUser, @RequestParam("amount") int amount) {
        try {
            transferCreditService.transferCredit(sourceUser, targetUser, amount);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
