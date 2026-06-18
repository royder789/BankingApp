package banking.ui;

import banking.model.*;
import banking.service.BankingService;
import banking.store.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Simple console menu for the Banking In-Memory Store demo.
 * No frameworks — just Scanner + System.out.
 */
public class ConsoleMenu {

    private final BankingService service;
    private final Scanner        sc = new Scanner(System.in);

    public ConsoleMenu(BankingService service) {
        this.service = service;
    }

    public void run() {
        System.out.println(banner());
        boolean running = true;
        while (running) {
            printMainMenu();
            String choice = prompt("Enter choice").trim();
            switch (choice) {
                case "1"  -> customerMenu();
                case "2"  -> accountMenu();
                case "3"  -> transactionMenu();
                case "4"  -> requestMenu();
                case "5"  -> viewAuditTrail();
                case "6"  -> viewRecentLogins();
                case "7"  -> undoLastTxn();
                case "8"  -> viewCollectionSummary();
                case "0"  -> { System.out.println("Goodbye!"); running = false; }
                default   -> System.out.println("  Invalid option, try again.");
            }
        }
        sc.close();
    }

    // ── Main Menu ──────────────────────────────────────────────────────────

    private void printMainMenu() {
        System.out.println("""
                
                ╔══════════════════════════════╗
                ║       MAIN MENU              ║
                ╠══════════════════════════════╣
                ║  1. Customer Management      ║
                ║  2. Account Management       ║
                ║  3. Transactions             ║
                ║  4. Service Requests         ║
                ║  5. View Audit Trail         ║
                ║  6. Recent Logins Cache      ║
                ║  7. Undo Last Transaction    ║
                ║  8. Collection Summary       ║
                ║  0. Exit                     ║
                ╚══════════════════════════════╝""");
    }

    // ── Customer ──────────────────────────────────────────────────────────

    private void customerMenu() {
        System.out.println("\n--- Customer Management ---");
        System.out.println("  a. Add customer   b. Find customer   c. List all   d. Login");
        String ch = prompt("Choice").trim().toLowerCase();
        switch (ch) {
            case "a" -> {
                String name  = prompt("Name");
                String email = prompt("Email");
                String phone = prompt("Phone");
                Customer c = service.createCustomer(name, email, phone);
                System.out.println("  ✔ Created: " + c);
            }
            case "b" -> {
                String id = prompt("Customer ID");
                service.findCustomer(id).ifPresentOrElse(
                        c -> System.out.println("  " + c),
                        ()  -> System.out.println("  Not found."));
            }
            case "c" -> {
                service.getStore().getAllCustomers().forEach(c -> System.out.println("  " + c));
            }
            case "d" -> {
                String id = prompt("Customer ID");
                try {
                    service.login(id);
                    System.out.println("  ✔ Logged in (recorded in LinkedHashMap cache).");
                } catch (Exception e) { System.out.println("  ✘ " + e.getMessage()); }
            }
            default -> System.out.println("  Unknown option.");
        }
    }

    // ── Account ───────────────────────────────────────────────────────────

    private void accountMenu() {
        System.out.println("\n--- Account Management ---");
        System.out.println("  a. Open account   b. View account   c. List by customer");
        String ch = prompt("Choice").trim().toLowerCase();
        switch (ch) {
            case "a" -> {
                String custId = prompt("Customer ID");
                System.out.println("  Types: SAVINGS / CURRENT / LOAN");
                String typeStr = prompt("Account type").toUpperCase();
                Account.AccountType type;
                try { type = Account.AccountType.valueOf(typeStr); }
                catch (Exception e) { System.out.println("  Invalid type."); return; }
                double dep = doublePrompt("Initial deposit");
                try {
                    Account acc = service.openAccount(custId, type, dep);
                    System.out.println("  ✔ Opened: " + acc);
                } catch (Exception e) { System.out.println("  ✘ " + e.getMessage()); }
            }
            case "b" -> {
                String id = prompt("Account ID");
                service.getStore().getAccount(id).ifPresentOrElse(
                        a -> System.out.println("  " + a),
                        ()  -> System.out.println("  Not found."));
            }
            case "c" -> {
                String custId = prompt("Customer ID");
                List<Account> accs = service.getStore().getAccountsForCustomer(custId);
                if (accs.isEmpty()) System.out.println("  No accounts found.");
                else accs.forEach(a -> System.out.println("  " + a));
            }
            default -> System.out.println("  Unknown option.");
        }
    }

    // ── Transactions ──────────────────────────────────────────────────────

    private void transactionMenu() {
        System.out.println("\n--- Transactions ---");
        System.out.println("  a. Deposit   b. Withdraw   c. Transfer   d. View history");
        String ch = prompt("Choice").trim().toLowerCase();
        switch (ch) {
            case "a" -> {
                String id  = prompt("Account ID");
                double amt = doublePrompt("Amount");
                String desc = prompt("Description");
                try {
                    Transaction t = service.deposit(id, amt, desc);
                    System.out.println("  ✔ " + t);
                } catch (Exception e) { System.out.println("  ✘ " + e.getMessage()); }
            }
            case "b" -> {
                String id  = prompt("Account ID");
                double amt = doublePrompt("Amount");
                String desc = prompt("Description");
                try {
                    Transaction t = service.withdraw(id, amt, desc);
                    System.out.println("  ✔ " + t);
                } catch (Exception e) { System.out.println("  ✘ " + e.getMessage()); }
            }
            case "c" -> {
                String from = prompt("From account ID");
                String to   = prompt("To account ID");
                double amt  = doublePrompt("Amount");
                try {
                    service.transfer(from, to, amt);
                    System.out.println("  ✔ Transfer complete.");
                } catch (Exception e) { System.out.println("  ✘ " + e.getMessage()); }
            }
            case "d" -> {
                String id = prompt("Account ID");
                SortedSet<Transaction> txns = service.getStore().getTransactions(id);
                if (txns.isEmpty()) System.out.println("  No transactions.");
                else txns.forEach(t -> System.out.println("  " + t));
            }
            default -> System.out.println("  Unknown option.");
        }
    }

    // ── Service Requests ─────────────────────────────────────────────────

    private void requestMenu() {
        System.out.println("\n--- Service Request Queue (PriorityQueue) ---");
        System.out.println("  a. Submit request   b. Process next   c. Peek next   d. Count pending");
        String ch = prompt("Choice").trim().toLowerCase();
        switch (ch) {
            case "a" -> {
                String custId = prompt("Customer ID");
                System.out.println("  Types: LOAN_APPLICATION / SUPPORT_TICKET / FRAUD_ALERT");
                String typeStr = prompt("Request type").toUpperCase();
                ServiceRequest.RequestType type;
                try { type = ServiceRequest.RequestType.valueOf(typeStr); }
                catch (Exception e) { System.out.println("  Invalid type."); return; }
                int pri = (int) doublePrompt("Priority (1-10)");
                String desc = prompt("Description");
                ServiceRequest req = service.submitRequest(custId, type, pri, desc);
                System.out.println("  ✔ Submitted: " + req);
            }
            case "b" -> {
                ServiceRequest req = service.processNextRequest();
                System.out.println(req == null ? "  Queue is empty." : "  ✔ Processed: " + req);
            }
            case "c" -> {
                ServiceRequest req = service.getStore().peekNextRequest();
                System.out.println(req == null ? "  Queue is empty." : "  Next up: " + req);
            }
            case "d" -> System.out.println("  Pending: " + service.getStore().pendingRequestCount());
            default  -> System.out.println("  Unknown option.");
        }
    }

    // ── Audit Trail ───────────────────────────────────────────────────────

    private void viewAuditTrail() {
        System.out.println("\n--- Audit Trail (ArrayDeque) ---");
        List<AuditLog> logs = service.getStore().getAuditTrail();
        if (logs.isEmpty()) { System.out.println("  Empty."); return; }
        // Show last 15
        int from = Math.max(0, logs.size() - 15);
        logs.subList(from, logs.size()).forEach(l -> System.out.println("  " + l));
    }

    // ── Recent Logins ─────────────────────────────────────────────────────

    private void viewRecentLogins() {
        System.out.println("\n--- Recent Login Cache (LinkedHashMap — last 10) ---");
        Map<String, LocalDateTime> logins = service.getStore().getRecentLogins();
        if (logins.isEmpty()) { System.out.println("  No logins recorded."); return; }
        logins.forEach((id, time) ->
                System.out.printf("  Customer %-12s  logged in at %s%n", id, time));
    }

    // ── Undo ──────────────────────────────────────────────────────────────

    private void undoLastTxn() {
        System.out.println("\n--- Undo Last Transaction (Stack / ArrayDeque) ---");
        System.out.println("  Undo stack size: " + service.getStore().undoStackSize());
        System.out.println("  Last txn ID on stack: " + service.getStore().peekLastTxnId());
        String yn = prompt("Confirm undo? (y/n)").trim().toLowerCase();
        if (yn.equals("y")) {
            System.out.println("  " + service.undoLastTransaction());
        }
    }

    // ── Collection Summary ────────────────────────────────────────────────

    private void viewCollectionSummary() {
        InMemoryStore s = service.getStore();
        System.out.println("""
                
                ┌─────────────────────────────────────────────────────────────────────┐
                │                   COLLECTION SUMMARY                               │
                ├──────────────────┬──────────────────┬───────────────┬──────────────┤
                │ Data             │ Collection       │ Write         │ Read         │
                ├──────────────────┼──────────────────┼───────────────┼──────────────┤
                │ Customers        │ HashMap          │ O(1) avg      │ O(1) avg     │
                │ Accounts         │ HashSet          │ O(1) avg      │ O(1) avg     │
                │ Transactions     │ TreeSet / acct   │ O(log n)      │ O(log n+k)   │
                │ Audit Trail      │ ArrayDeque       │ O(1) addLast  │ O(1) peek    │
                │ Service Requests │ PriorityQueue    │ O(log n)      │ O(1) peek    │
                │ Login Cache      │ LinkedHashMap    │ O(1)          │ O(1)         │
                │ Undo Stack       │ ArrayDeque(stack)│ O(1) push     │ O(1) peek    │
                └──────────────────┴──────────────────┴───────────────┴──────────────┘
                """);
        System.out.printf("  Customers     : %d%n", s.getAllCustomers().size());
        System.out.printf("  Accounts      : %d%n", s.getAllAccounts().size());
        System.out.printf("  Audit entries : %d%n", s.getAuditTrail().size());
        System.out.printf("  Pending reqs  : %d%n", s.pendingRequestCount());
        System.out.printf("  Undo stack    : %d%n", s.undoStackSize());
    }

    // ── Utilities ─────────────────────────────────────────────────────────

    private String prompt(String label) {
        System.out.print("  " + label + ": ");
        return sc.nextLine();
    }

    private double doublePrompt(String label) {
        while (true) {
            try { return Double.parseDouble(prompt(label)); }
            catch (NumberFormatException e) { System.out.println("  Please enter a number."); }
        }
    }

    private static String banner() {
        return """
                ╔══════════════════════════════════════════════╗
                ║   🏦  BANK IN-MEMORY STORE — JAVA DEMO       ║
                ║       Collections: HashMap, HashSet,          ║
                ║       TreeSet, ArrayDeque, PriorityQueue,     ║
                ║       LinkedHashMap, Stack (Deque)            ║
                ╚══════════════════════════════════════════════╝""";
    }
}
