package com.enterprise.governance.core;

/**
 * Value Object representing a governance decision.
 */
public class GovernanceDecision {
    
    public enum Action { 
        ALLOW,  // Query is allowed to execute
        BLOCK,  // Query is blocked from execution
        WARN    // Query triggers a warning but is allowed
    }
    
    private final Action action;
    private final String reason;

    public GovernanceDecision(Action action, String reason) {
        this.action = action;
        this.reason = reason;
    }

    public Action getAction() {
        return action;
    }

    public boolean isBlock() {
        return action == Action.BLOCK;
    }

    public String getReason() {
        return reason;
    }
    
    public static GovernanceDecision allow() {
        return new GovernanceDecision(Action.ALLOW, null);
    }
    
    public static GovernanceDecision block(String reason) {
        return new GovernanceDecision(Action.BLOCK, reason);
    }
    
    public static GovernanceDecision warn(String reason) {
        return new GovernanceDecision(Action.WARN, reason);
    }

    @Override
    public String toString() {
        return "GovernanceDecision{" +
                "action=" + action +
                ", reason='" + reason + '\'' +
                '}';
    }
}
