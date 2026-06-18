package banking.store;

/**
 * A loan application or support ticket queued for processing.
 * PriorityQueue orders these by priority (higher number = served first).
 */
public class ServiceRequest {

    public enum RequestType { LOAN_APPLICATION, SUPPORT_TICKET, FRAUD_ALERT }

    private final String      requestId;
    private final String      customerId;
    private final RequestType type;
    private final int         priority;   // 1 (low) → 10 (urgent)
    private final String      description;

    public ServiceRequest(String requestId, String customerId,
                          RequestType type, int priority, String description) {
        this.requestId   = requestId;
        this.customerId  = customerId;
        this.type        = type;
        this.priority    = Math.max(1, Math.min(10, priority)); // clamp 1–10
        this.description = description;
    }

    public String      getRequestId()   { return requestId;   }
    public String      getCustomerId()  { return customerId;  }
    public RequestType getType()        { return type;        }
    public int         getPriority()    { return priority;    }
    public String      getDescription() { return description; }

    @Override
    public String toString() {
        return String.format("Request[id=%s, cust=%s, type=%s, pri=%d, desc=%s]",
                requestId, customerId, type, priority, description);
    }
}
