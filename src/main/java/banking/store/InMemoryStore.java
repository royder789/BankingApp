package banking.store;

import banking.model.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ════════════════════════════════════════════════════════════════
 *  IN-MEMORY STORE  —  All collections live here
 * ════════════════════════════════════════════════════════════════
 *
 *  Collection          | Used For              | Why / Complexity
 * ---------------------+-----------------------+------------------------------
 *  HashMap             | Customers             | O(1) get/put by customerId
 *  HashSet             | Accounts              | O(1) add/contains/remove
 *  HashMap<String,     |                       |
 *    TreeSet<Txn>>     | Transactions          | O(log n) insert; free chrono
 *                      |                       | order per account
 *  ArrayDeque          | Audit Trail           | O(1) addLast / peekFirst
 *  PriorityQueue       | Pending Requests      | O(log n) insert; O(1) peek
 *                      | (loan / support)      | highest-priority served first
 *  LinkedHashMap       | Recent Logins cache   | O(1) access + insertion order
 *                      | (LRU-style)           | oldest entry easy to evict
 *  Stack               | Undo last operation   | O(1) push/pop (LIFO)
 * ════════════════════════════════════════════════════════════════
 */
public class InMemoryStore {

    // ── 1. HashMap: Customer directory  ─────────────────────────────────────
    // Key = customerId  →  O(1) average get / put / remove
    private final Map<String, Customer> customerMap = new HashMap<>();

    // ── 2. HashSet: Account registry ────────────────────────────────────────
    // Equality is based on accountId (see Account.equals/hashCode)
    // O(1) average add / contains / remove
    private final Set<Account> accountSet = new HashSet<>();

    // Helper: quick lookup of Account object by id (avoids scanning the Set)
    private final Map<String, Account> accountIndex = new HashMap<>();

    // ── 3. TreeSet per account: Transaction ledger ───────────────────────────
    // Each account gets its own TreeSet; entries auto-sort by (timestamp, txnId)
    // Insert / lookup:  O(log n)
    // Range queries (e.g. "txns between date A and B"): O(log n + k)
    private final Map<String, TreeSet<Transaction>> transactionLedger = new HashMap<>();

    // ── 4. ArrayDeque: Audit Trail ───────────────────────────────────────────
    // addLast() → O(1);  peekFirst() / pollFirst() → O(1)
    // Perfect for append-only event log; can cap size cheaply.
    private final Deque<AuditLog> auditTrail = new ArrayDeque<>();
    private static final int MAX_AUDIT_SIZE = 500;

    // ── 5. PriorityQueue: Service / Loan Request Queue ───────────────────────
    // Min-heap on priority value; O(log n) offer/poll, O(1) peek
    // Highest numerical priority = served first (we negate for min-heap trick)
    private final PriorityQueue<ServiceRequest> requestQueue =
            new PriorityQueue<>(Comparator.comparingInt(r -> -r.getPriority()));

    // ── 6. LinkedHashMap: Recent Login Cache (insertion-order LRU) ──────────
    // Access and insertion both O(1); oldest entry at head → easy eviction
    private final Map<String, LocalDateTime> recentLogins =
            new LinkedHashMap<>(16, 0.75f, false) {  // false = insertion order
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, LocalDateTime> eldest) {
                    return size() > 10; // keep last 10 logins
                }
            };

    // ── 7. Stack: Undo last transfer / operation ────────────────────────────
    // O(1) push / pop / peek  (LIFO — last action is first undone)
    private final Deque<String> undoStack = new ArrayDeque<>();  // stores txnIds

    // ════════════════════════════════════════════════════════════
    //  CUSTOMER operations
    // ════════════════════════════════════════════════════════════

    public void addCustomer(Customer c) {
        customerMap.put(c.getCustomerId(), c);
        audit("SYSTEM", "ADD_CUSTOMER", "Added customer: " + c.getCustomerId());
    }

    public Optional<Customer> getCustomer(String customerId) {
        return Optional.ofNullable(customerMap.get(customerId));
    }

    public Collection<Customer> getAllCustomers() {
        return Collections.unmodifiableCollection(customerMap.values());
    }

    public boolean removeCustomer(String customerId) {
        Customer removed = customerMap.remove(customerId);
        if (removed != null) {
            audit("SYSTEM", "REMOVE_CUSTOMER", "Removed customer: " + customerId);
        }
        return removed != null;
    }

    // ════════════════════════════════════════════════════════════
    //  ACCOUNT operations
    // ════════════════════════════════════════════════════════════

    public boolean addAccount(Account a) {
        if (accountIndex.containsKey(a.getAccountId())) return false;
        accountSet.add(a);
        accountIndex.put(a.getAccountId(), a);
        transactionLedger.put(a.getAccountId(), new TreeSet<>());
        audit("SYSTEM", "OPEN_ACCOUNT", "Account opened: " + a.getAccountId());
        return true;
    }

    public Optional<Account> getAccount(String accountId) {
        return Optional.ofNullable(accountIndex.get(accountId));
    }

    /** All accounts belonging to a customer — O(n) scan but simple for small data. */
    public List<Account> getAccountsForCustomer(String customerId) {
        List<Account> result = new ArrayList<>();
        for (Account a : accountSet) {
            if (a.getCustomerId().equals(customerId)) result.add(a);
        }
        return result;
    }

    public Set<Account> getAllAccounts() {
        return Collections.unmodifiableSet(accountSet);
    }

    public boolean accountExists(String accountId) {
        return accountIndex.containsKey(accountId);   // O(1)
    }

    // ════════════════════════════════════════════════════════════
    //  TRANSACTION operations
    // ════════════════════════════════════════════════════════════

    public boolean addTransaction(Transaction txn) {
        TreeSet<Transaction> ledger = transactionLedger.get(txn.getAccountId());
        if (ledger == null) return false;
        ledger.add(txn);                                  // O(log n)
        undoStack.push(txn.getTxnId());                   // O(1)
        audit("SYSTEM", "TXN_" + txn.getType(),
                "Txn " + txn.getTxnId() + " on acct " + txn.getAccountId()
                + " amount=" + txn.getAmount());
        return true;
    }

    /** All transactions for an account, naturally sorted by time. */
    public SortedSet<Transaction> getTransactions(String accountId) {
        TreeSet<Transaction> ledger = transactionLedger.get(accountId);
        return ledger == null ? Collections.emptySortedSet()
                              : Collections.unmodifiableSortedSet(ledger);
    }

    /** Chronological range query — O(log n + k). */
    public SortedSet<Transaction> getTransactionsBetween(
            String accountId, LocalDateTime from, LocalDateTime to) {

        TreeSet<Transaction> ledger = transactionLedger.get(accountId);
        if (ledger == null) return Collections.emptySortedSet();

        // Dummy sentinels for range bounds
        Transaction low  = new Transaction("", accountId, 0,
                Transaction.TxnType.DEBIT, from, "");
        Transaction high = new Transaction("\uFFFF", accountId, 0,
                Transaction.TxnType.DEBIT, to, "");

        return Collections.unmodifiableSortedSet(ledger.subSet(low, true, high, true));
    }

    // ════════════════════════════════════════════════════════════
    //  AUDIT TRAIL operations
    // ════════════════════════════════════════════════════════════

    private void audit(String actor, String action, String details) {
        if (auditTrail.size() >= MAX_AUDIT_SIZE) {
            auditTrail.pollFirst();   // evict oldest — O(1)
        }
        auditTrail.addLast(new AuditLog(LocalDateTime.now(), actor, action, details));
    }

    /** Returns all audit logs, oldest first. */
    public List<AuditLog> getAuditTrail() {
        return new ArrayList<>(auditTrail);
    }

    // ════════════════════════════════════════════════════════════
    //  SERVICE REQUEST QUEUE (PriorityQueue)
    // ════════════════════════════════════════════════════════════

    public void enqueueRequest(ServiceRequest req) {
        requestQueue.offer(req);         // O(log n)
        audit("SYSTEM", "REQUEST_QUEUED",
                "Request " + req.getRequestId() + " priority=" + req.getPriority());
    }

    /** Returns the highest-priority pending request. O(1) peek. */
    public ServiceRequest peekNextRequest() {
        return requestQueue.peek();
    }

    /** Removes and returns the highest-priority request. O(log n). */
    public ServiceRequest processNextRequest() {
        ServiceRequest req = requestQueue.poll();
        if (req != null) audit("SYSTEM", "REQUEST_PROCESSED", "Processed: " + req.getRequestId());
        return req;
    }

    public int pendingRequestCount() {
        return requestQueue.size();
    }

    // ════════════════════════════════════════════════════════════
    //  RECENT LOGIN CACHE (LinkedHashMap)
    // ════════════════════════════════════════════════════════════

    public void recordLogin(String customerId) {
        recentLogins.put(customerId, LocalDateTime.now());  // O(1)
        audit(customerId, "LOGIN", "Customer logged in");
    }

    public Optional<LocalDateTime> getLastLogin(String customerId) {
        return Optional.ofNullable(recentLogins.get(customerId));
    }

    public Map<String, LocalDateTime> getRecentLogins() {
        return Collections.unmodifiableMap(recentLogins);
    }

    // ════════════════════════════════════════════════════════════
    //  UNDO STACK  (ArrayDeque used as Stack)
    // ════════════════════════════════════════════════════════════

    /** Returns the txnId of the last transaction (does NOT remove it). */
    public String peekLastTxnId() {
        return undoStack.isEmpty() ? null : undoStack.peek();
    }

    /**
     * Pops the last txnId from the undo stack.
     * Actual reversal logic lives in BankingService.
     */
    public String popLastTxnId() {
        return undoStack.isEmpty() ? null : undoStack.pop();
    }

    public int undoStackSize() {
        return undoStack.size();
    }

    // ════════════════════════════════════════════════════════════
    //  STREAM API — read-only queries / reports over the collections
    //  (No new data structures here — Streams just process the
    //   existing HashMap / HashSet / TreeSet contents declaratively)
    // ════════════════════════════════════════════════════════════

    /**
     * Total balance across ALL accounts in the bank.
     * Stream pipeline: accountSet -> map(getBalance) -> sum
     */
    public double getTotalBankBalance() {
        return accountSet.stream()
                .mapToDouble(Account::getBalance)
                .sum();
    }

    /**
     * Returns accounts sorted by balance, highest first.
     * Stream pipeline: stream -> sorted(comparator) -> collect to List
     */
    public List<Account> getAccountsSortedByBalanceDesc() {
        return accountSet.stream()
                .sorted(Comparator.comparingDouble(Account::getBalance).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Filters accounts by type (SAVINGS / CURRENT / LOAN) using Stream.filter.
     */
    public List<Account> getAccountsByType(Account.AccountType type) {
        return accountSet.stream()
                .filter(a -> a.getType() == type)
                .collect(Collectors.toList());
    }

    /**
     * Groups all accounts by their AccountType.
     * Stream pipeline: stream -> Collectors.groupingBy
     */
    public Map<Account.AccountType, List<Account>> groupAccountsByType() {
        return accountSet.stream()
                .collect(Collectors.groupingBy(Account::getType));
    }

    /**
     * Counts accounts per type — same idea as groupingBy but with a
     * downstream counting collector instead of collecting full lists.
     */
    public Map<Account.AccountType, Long> countAccountsByType() {
        return accountSet.stream()
                .collect(Collectors.groupingBy(Account::getType, Collectors.counting()));
    }

    /**
     * Flattens every TreeSet ledger into a single stream of all
     * transactions in the entire bank (across all accounts).
     * Uses Stream.flatMap to merge the nested Map<String, TreeSet<Transaction>>.
     */
    public List<Transaction> getAllTransactions() {
        return transactionLedger.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toList());
    }

    /**
     * Total amount moved (CREDIT + DEBIT + TRANSFER) for one account.
     * Stream pipeline: filter by account -> mapToDouble -> sum
     */
    public double getTotalTransactionVolume(String accountId) {
        TreeSet<Transaction> ledger = transactionLedger.get(accountId);
        if (ledger == null) return 0.0;
        return ledger.stream()
                .mapToDouble(Transaction::getAmount)
                .sum();
    }

    /**
     * Finds the single largest transaction for an account.
     * Stream pipeline: stream -> max(comparator) -> Optional
     */
    public Optional<Transaction> getLargestTransaction(String accountId) {
        TreeSet<Transaction> ledger = transactionLedger.get(accountId);
        if (ledger == null) return Optional.empty();
        return ledger.stream()
                .max(Comparator.comparingDouble(Transaction::getAmount));
    }

    /**
     * Returns only CREDIT transactions for an account, using Stream.filter.
     */
    public List<Transaction> getCreditsOnly(String accountId) {
        TreeSet<Transaction> ledger = transactionLedger.get(accountId);
        if (ledger == null) return Collections.emptyList();
        return ledger.stream()
                .filter(t -> t.getType() == Transaction.TxnType.CREDIT)
                .collect(Collectors.toList());
    }

    /**
     * Top N customers ranked by their TOTAL balance across all their accounts.
     * Combines two streams: group accounts by customerId, sum balances, sort, limit.
     */
    public List<Map.Entry<String, Double>> getTopNCustomersByBalance(int n) {
        Map<String, Double> totals = accountSet.stream()
                .collect(Collectors.groupingBy(
                        Account::getCustomerId,
                        Collectors.summingDouble(Account::getBalance)));

        return totals.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    /**
     * Searches the audit trail for entries matching a keyword in the action,
     * using Stream.filter + String::contains.
     */
    public List<AuditLog> searchAuditTrail(String keyword) {
        return auditTrail.stream()
                .filter(log -> log.getAction().toLowerCase().contains(keyword.toLowerCase())
                        || log.getDetails().toLowerCase().contains(keyword.toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Builds a simple String report combining several stream pipelines.
     * Demonstrates joining results from multiple streams into one output.
     */
    public String buildBankSummaryReport() {
        long totalAccounts = accountSet.stream().count();
        double totalBalance = getTotalBankBalance();
        long totalTxns = getAllTransactions().size();

        String typeBreakdown = countAccountsByType().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));

        return String.format(
                "Accounts: %d | Total Balance: %.2f | Total Txns: %d | By Type: [%s]",
                totalAccounts, totalBalance, totalTxns, typeBreakdown);
    }
}
