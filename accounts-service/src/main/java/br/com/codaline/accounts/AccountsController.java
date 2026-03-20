package br.com.codaline.accounts;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/open-banking/accounts/v2")
public class AccountsController {


  @GetMapping
  public Map<String, Object> listAccounts() {
    return Map.of(
        "data", List.of(
            Map.of("accountId", "acc-001", "type", "CACC", "currency", "BRL"),
            Map.of("accountId", "acc-002", "type", "SVGS", "currency", "BRL")
        ),
        "meta", Map.of("totalRecords", 2)
    );
  }

  @GetMapping("/{accountId}/balances")
  public Map<String, Object> getBalances(@PathVariable String accountId) {
    return Map.of(
        "data", Map.of(
            "accountId", accountId,
            "availableAmount", "1500.00",
            "blockedAmount", "200.00",
            "currency", "BRL"
        )
    );
  }
}
