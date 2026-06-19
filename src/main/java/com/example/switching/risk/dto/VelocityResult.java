package com.example.switching.risk.dto;

/**
 * Velocity check result returned by {@link com.example.switching.risk.service.VelocityCheckService#checkVelocity}.
 */
public class VelocityResult {

    private final boolean withinLimits;

    /** Name of the first breached check type, e.g. "COUNT_HOURLY". Null if withinLimits=true. */
    private final String breachedRule;

    private final double currentValue;
    private final double limitValue;

    public VelocityResult(boolean withinLimits, String breachedRule,
                          double currentValue, double limitValue) {
        this.withinLimits = withinLimits;
        this.breachedRule = breachedRule;
        this.currentValue = currentValue;
        this.limitValue = limitValue;
    }

    public static VelocityResult ok() {
        return new VelocityResult(true, null, 0, 0);
    }

    public static VelocityResult breached(String rule, double current, double limit) {
        return new VelocityResult(false, rule, current, limit);
    }

    public boolean isWithinLimits() { return withinLimits; }
    public String  getBreachedRule() { return breachedRule; }
    public double  getCurrentValue() { return currentValue; }
    public double  getLimitValue()   { return limitValue; }
}
