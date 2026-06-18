package banking.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a single financial transaction.
 * Stored in a TreeSet (sorted by timestamp, then txnId) → O(log n) insert/lookup,
 * but gives free chronological ordering without any extra sort step.
 */
public class Transaction implements Comparable<Transaction> {

    public enum TxnType { CREDIT, DEBIT, TRANSFER }

    private final String        txnId;
    private final String        accountId;
    private final double        amount;
    private final TxnType       type;
    private final LocalDateTime timestamp;
    private final String        description;

    public Transaction(String txnId, String accountId, double amount,
                       TxnType type, LocalDateTime timestamp, String description) {
        this.txnId       = txnId;
        this.accountId   = accountId;
        this.amount      = amount;
        this.type        = type;
        this.timestamp   = timestamp;
        this.description = description;
    }

    // TreeSet uses this for ordering (newer first within same timestamp → lexicographic txnId)
    @Override
    public int compareTo(Transaction other) {
        int cmp = this.timestamp.compareTo(other.timestamp);
        if (cmp != 0) return cmp;
        return this.txnId.compareTo(other.txnId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction)) return false;
        return txnId.equals(((Transaction) o).txnId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(txnId);
    }

    // --- Getters ---
    public String        getTxnId()       { return txnId;       }
    public String        getAccountId()   { return accountId;   }
    public double        getAmount()      { return amount;      }
    public TxnType       getType()        { return type;        }
    public LocalDateTime getTimestamp()   { return timestamp;   }
    public String        getDescription() { return description; }

    @Override
    public String toString() {
        return String.format("Txn[id=%s, acct=%s, type=%s, amount=%.2f, time=%s, desc=%s]",
                txnId, accountId, type, amount, timestamp, description);
    }
}
