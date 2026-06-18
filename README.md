# 🏦 Banking In-Memory Store — Java Assignment

A clean, console-based Java project demonstrating **7 different Java Collections**
for storing banking data in memory.

---

## Project Structure

```
BankingApp/
├── run.sh                          ← compile + run in one step
├── BankingApp.jar                  ← pre-built JAR (java -jar BankingApp.jar)
└── src/main/java/banking/
    ├── Main.java                   ← entry point + seed data
    ├── model/
    │   ├── Customer.java
    │   ├── Account.java
    │   ├── Transaction.java
    │   └── AuditLog.java
    ├── store/
    │   ├── InMemoryStore.java      ← ALL collections live here ★
    │   └── ServiceRequest.java
    ├── service/
    │   └── BankingService.java     ← business logic
    └── ui/
        └── ConsoleMenu.java        ← interactive menu
```

---

## How to Run

### Option A — Run the pre-built JAR (easiest)
```bash
java -jar BankingApp.jar
```

### Option B — Compile & run manually
```bash
bash run.sh
```

### Option C — Step by step
```bash
mkdir -p out
find src -name "*.java" | xargs javac --release 17 -d out
java -cp out banking.Main
```

> Requires **Java 17+** (`java -version` to check)

---

## Collections Used & Why

| Data | Collection | Write | Read | Reason |
|---|---|---|---|---|
| **Customers** | `HashMap<String, Customer>` | O(1) avg | O(1) avg | Fast lookup by `customerId` key |
| **Accounts** | `HashSet<Account>` + index | O(1) avg | O(1) avg | Uniqueness by `accountId`; no order needed |
| **Transactions** | `TreeSet<Transaction>` per account | O(log n) | O(log n+k) | Auto-sorted by timestamp; range queries free |
| **Audit Trail** | `ArrayDeque<AuditLog>` | O(1) addLast | O(1) peek | Append to tail, read/expire from head |
| **Service Requests** | `PriorityQueue<ServiceRequest>` | O(log n) | O(1) peek | FRAUD_ALERT (pri=10) always ahead of tickets (pri=3) |
| **Login Cache** | `LinkedHashMap<String, DateTime>` | O(1) | O(1) | Insertion order + auto-evict oldest entry (LRU) |
| **Undo Stack** | `ArrayDeque` used as Stack | O(1) push | O(1) peek | LIFO — last txn is first undone |

---

## Features (Menu Options)

```
1. Customer Management
   a. Add customer
   b. Find customer by ID
   c. List all customers
   d. Login (records in LinkedHashMap cache)

2. Account Management
   a. Open a new account (SAVINGS / CURRENT / LOAN)
   b. View account by ID
   c. List all accounts for a customer

3. Transactions
   a. Deposit (CREDIT) — balance increases
   b. Withdraw (DEBIT) — balance decreases, checks funds
   c. Transfer — atomic debit+credit across accounts
   d. View full history — sorted chronologically (TreeSet)

4. Service Requests (PriorityQueue demo)
   a. Submit request with a priority 1–10
   b. Process next (highest priority served first)
   c. Peek next (without removing)
   d. Count pending requests

5. View Audit Trail — last 15 entries from ArrayDeque

6. Recent Login Cache — LinkedHashMap (auto-evicts after 10 entries)

7. Undo Last Transaction — pops from the ArrayDeque/Stack

8. Collection Summary — live counts + complexity table
```

---

## Key Design Points

- **No database, no files** — everything is in-memory as per the assignment.
- **Layered architecture**: `model` → `store` → `service` → `ui`
- The `InMemoryStore` class is the single source of truth for all collections.
  Read the Javadoc table at the top of that file first.
- Transactions inside a `TreeSet` give you free chronological ordering
  and cheap date-range queries (`subSet()`) — no sorting step needed.
- The `PriorityQueue` ensures a FRAUD_ALERT (priority 10) is always
  processed before a SUPPORT_TICKET (priority 3), regardless of insertion order.
- The `LinkedHashMap` login cache auto-evicts the oldest entry once it
  exceeds 10 entries (override of `removeEldestEntry`).
