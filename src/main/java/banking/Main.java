package banking;

import banking.model.*;
import banking.service.BankingService;
import banking.store.*;
import banking.ui.ConsoleMenu;

import java.time.LocalDateTime;

/**
 * Entry point — seeds a few records then launches the interactive console menu.
 */
public class Main {

    public static void main(String[] args) {

        InMemoryStore  store   = new InMemoryStore();
        BankingService service = new BankingService(store);

        // ── Seed Data ────────────────────────────────────────────────────────
        System.out.println("  [Seeding demo data…]");

        // Customers
        Customer alice = service.createCustomer("Alice Sharma", "alice@email.com", "9876543210");
        Customer bob   = service.createCustomer("Bob Verma",   "bob@email.com",   "9123456780");

        // Accounts
        Account aliceSavings = service.openAccount(alice.getCustomerId(),
                Account.AccountType.SAVINGS, 50_000.00);
        Account bobCurrent   = service.openAccount(bob.getCustomerId(),
                Account.AccountType.CURRENT, 1_00_000.00);

        // Transactions
        service.deposit(aliceSavings.getAccountId(), 10_000, "Salary credit");
        service.deposit(bobCurrent.getAccountId(),   25_000, "Client payment");
        service.withdraw(aliceSavings.getAccountId(),  2_000, "ATM withdrawal");
        service.transfer(bobCurrent.getAccountId(), aliceSavings.getAccountId(), 5_000);

        // Logins
        service.login(alice.getCustomerId());
        service.login(bob.getCustomerId());

        // Service requests (PriorityQueue)
        service.submitRequest(alice.getCustomerId(),
                ServiceRequest.RequestType.LOAN_APPLICATION, 7, "Home loan ₹30L");
        service.submitRequest(bob.getCustomerId(),
                ServiceRequest.RequestType.FRAUD_ALERT, 10, "Suspicious txn on card");
        service.submitRequest(alice.getCustomerId(),
                ServiceRequest.RequestType.SUPPORT_TICKET, 3, "Update email address");

        System.out.println("  [Seed complete — use menu to explore]\n");

        // ── Launch Console Menu ───────────────────────────────────────────────
        new ConsoleMenu(service).run();
    }
}
