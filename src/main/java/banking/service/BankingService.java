package banking.service;

import banking.model.*;
import banking.store.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service layer — orchestrates operations on the InMemoryStore.
 * All business rules (sufficient balance, account existence, etc.) live here.
 */
public class BankingService {

    private final InMemoryStore store;
    private final AtomicInteger txnCounter     = new AtomicInteger(1000);
    private final AtomicInteger reqCounter     = new AtomicInteger(1);

    public BankingService(InMemoryStore store) {
        this.store = store;
    }

    // ── Customer ─────────────────────────────────────────────────────────────

    public Customer createCustomer(String name, String email, String phone) {
        String id = "CUST" + System.nanoTime() % 10_000;
        Customer c = new Customer(id, name, email, phone);
        store.addCustomer(c);
        return c;
    }

    public Optional<Customer> findCustomer(String id) {
        return store.getCustomer(id);
    }

    // ── Account ──────────────────────────────────────────────────────────────

    public Account openAccount(String customerId, Account.AccountType type, double initialDeposit) {
        if (store.getCustomer(customerId).isEmpty()) {
            throw new IllegalArgumentException("Customer not found: " + customerId);
        }
        String id = "ACC" + System.nanoTime() % 100_000;
        String custName = store.getCustomer(customerId).get().getName();
        Account acc = new Account(id, customerId, custName + "'s " + type, type, initialDeposit);
        store.addAccount(acc);
        return acc;
    }

    // ── Transactions ─────────────────────────────────────────────────────────

    /** Credit (deposit) money into an account. */
    public Transaction deposit(String accountId, double amount, String description) {
        Account acc = requireAccount(accountId);
        acc.setBalance(acc.getBalance() + amount);
        return recordTxn(accountId, amount, Transaction.TxnType.CREDIT, description);
    }

    /** Debit (withdraw) money from an account. */
    public Transaction withdraw(String accountId, double amount, String description) {
        Account acc = requireAccount(accountId);
        if (acc.getBalance() < amount) {
            throw new IllegalStateException("Insufficient funds. Balance: " + acc.getBalance());
        }
        acc.setBalance(acc.getBalance() - amount);
        return recordTxn(accountId, amount, Transaction.TxnType.DEBIT, description);
    }

    /** Transfer between two accounts; records two transactions. */
    public void transfer(String fromId, String toId, double amount) {
        Account from = requireAccount(fromId);
        Account to   = requireAccount(toId);
        if (from.getBalance() < amount) {
            throw new IllegalStateException("Insufficient funds for transfer.");
        }
        from.setBalance(from.getBalance() - amount);
        to.setBalance(to.getBalance() + amount);
        recordTxn(fromId, amount, Transaction.TxnType.TRANSFER, "Transfer to " + toId);
        recordTxn(toId,   amount, Transaction.TxnType.TRANSFER, "Transfer from " + fromId);
    }

    /**
     * Undo the very last transaction (pop from undo stack).
     * Reverses only CREDIT / DEBIT (not TRANSFER, for simplicity).
     */
    public String undoLastTransaction() {
        String txnId = store.popLastTxnId();
        if (txnId == null) return "Nothing to undo.";

        // Find the transaction across all accounts
        for (Account acc : store.getAllAccounts()) {
            for (Transaction t : store.getTransactions(acc.getAccountId())) {
                if (t.getTxnId().equals(txnId)) {
                    if (t.getType() == Transaction.TxnType.CREDIT) {
                        acc.setBalance(acc.getBalance() - t.getAmount());
                    } else if (t.getType() == Transaction.TxnType.DEBIT) {
                        acc.setBalance(acc.getBalance() + t.getAmount());
                    }
                    return "Undone transaction: " + txnId;
                }
            }
        }
        return "Transaction not found for undo: " + txnId;
    }

    // ── Service Requests ─────────────────────────────────────────────────────

    public ServiceRequest submitRequest(String customerId, ServiceRequest.RequestType type,
                                        int priority, String description) {
        String id = "REQ" + reqCounter.getAndIncrement();
        ServiceRequest req = new ServiceRequest(id, customerId, type, priority, description);
        store.enqueueRequest(req);
        return req;
    }

    public ServiceRequest processNextRequest() {
        return store.processNextRequest();
    }

    // ── Login tracking ───────────────────────────────────────────────────────

    public void login(String customerId) {
        if (store.getCustomer(customerId).isEmpty()) {
            throw new IllegalArgumentException("Customer not found: " + customerId);
        }
        store.recordLogin(customerId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Account requireAccount(String accountId) {
        return store.getAccount(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
    }

    private Transaction recordTxn(String accountId, double amount,
                                   Transaction.TxnType type, String desc) {
        String id  = "TXN" + txnCounter.getAndIncrement();
        Transaction txn = new Transaction(id, accountId, amount, type, LocalDateTime.now(), desc);
        store.addTransaction(txn);
        return txn;
    }

    // Expose store for display layer
    public InMemoryStore getStore() { return store; }
}
