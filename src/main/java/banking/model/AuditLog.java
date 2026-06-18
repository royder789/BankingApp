package banking.model;

import java.time.LocalDateTime;

/**
 * A single audit-log entry.
 * Stored in an ArrayDeque → O(1) append to tail, O(1) peek/remove from head.
 * Perfect for a rolling log: add at the end, read/expire from the front.
 */
public class AuditLog {

    private final LocalDateTime time;
    private final String        actor;       // who did the action
    private final String        action;      // what they did
    private final String        details;     // free-text details

    public AuditLog(LocalDateTime time, String actor, String action, String details) {
        this.time    = time;
        this.actor   = actor;
        this.action  = action;
        this.details = details;
    }

    public LocalDateTime getTime()    { return time;    }
    public String        getActor()   { return actor;   }
    public String        getAction()  { return action;  }
    public String        getDetails() { return details; }

    @Override
    public String toString() {
        return String.format("[%s] %s → %s | %s", time, actor, action, details);
    }
}
