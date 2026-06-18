package banking.model;

import java.util.Objects;

/**
 * Represents a bank account.
 * Stored in a HashSet → O(1) lookup by accountId.
 */
public class Account {

    public enum AccountType { SAVINGS, CURRENT, LOAN }

    private final String accountId;
    private final String customerId;
    private String name;
    private AccountType type;
    private double balance;

    public Account(String accountId, String customerId, String name,
                   AccountType type, double initialBalance) {
        this.accountId  = accountId;
        this.customerId = customerId;
        this.name       = name;
        this.type       = type;
        this.balance    = initialBalance;
    }

    // --- Getters / Setters ---
    public String getAccountId()  { return accountId;  }
    public String getCustomerId() { return customerId; }
    public String getName()       { return name;       }
    public AccountType getType()  { return type;       }
    public double getBalance()    { return balance;    }

    public void setBalance(double balance) { this.balance = balance; }
    public void setName(String name)       { this.name   = name;    }

    // HashSet relies on these two methods for O(1) bucket-based lookup
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account)) return false;
        return accountId.equals(((Account) o).accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId);
    }

    @Override
    public String toString() {
        return String.format("Account[id=%s, customer=%s, name=%s, type=%s, balance=%.2f]",
                accountId, customerId, name, type, balance);
    }
}
