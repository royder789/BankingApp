package banking.store;

import banking.model.*;

import java.time.LocalDateTime;
import java.util.*;


public class InMemoryStore {


    private final Map<String, Customer> customerMap = new HashMap<>();

    private final Set<Account> accountSet = new HashSet<>();

    private final Map<String, Account> accountIndex = new HashMap<>();


    private final Map<String, TreeSet<Transaction>> transactionLedger = new HashMap<>();


    private final Deque<AuditLog> auditTrail = new ArrayDeque<>();
    private static final int MAX_AUDIT_SIZE = 500;


    private final PriorityQueue<ServiceRequest> requestQueue =
            new PriorityQueue<>(Comparator.comparingInt(r -> -r.getPriority()));


    private final Map<String, LocalDateTime> recentLogins =
            new LinkedHashMap<>(16, 0.75f, false) {  // false = insertion order
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, LocalDateTime> eldest) {
                    return size() > 10; // keep last 10 logins
                }
            };


    private final Deque<String> undoStack = new ArrayDeque<>();  // stores txnIds



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

    public SortedSet<Transaction> getTransactions(String accountId) {
        TreeSet<Transaction> ledger = transactionLedger.get(accountId);
        return ledger == null ? Collections.emptySortedSet()
                              : Collections.unmodifiableSortedSet(ledger);
    }


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



    private void audit(String actor, String action, String details) {
        if (auditTrail.size() >= MAX_AUDIT_SIZE) {
            auditTrail.pollFirst();   // evict oldest — O(1)
        }
        auditTrail.addLast(new AuditLog(LocalDateTime.now(), actor, action, details));
    }


    public List<AuditLog> getAuditTrail() {
        return new ArrayList<>(auditTrail);
    }



    public void enqueueRequest(ServiceRequest req) {
        requestQueue.offer(req);        
        audit("SYSTEM", "REQUEST_QUEUED",
                "Request " + req.getRequestId() + " priority=" + req.getPriority());
    }


    public ServiceRequest peekNextRequest() {
        return requestQueue.peek();
    }  public ServiceRequest processNextRequest() {
        ServiceRequest req = requestQueue.poll();
        if (req != null) audit("SYSTEM", "REQUEST_PROCESSED", "Processed: " + req.getRequestId());
        return req;
    }

    public int pendingRequestCount() {
        return requestQueue.size();
    }


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


    public String peekLastTxnId() {
        return undoStack.isEmpty() ? null : undoStack.peek();
    }


    public String popLastTxnId() {
        return undoStack.isEmpty() ? null : undoStack.pop();
    }

    public int undoStackSize() {
        return undoStack.size();
    }
}
