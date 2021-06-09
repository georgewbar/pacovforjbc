package pacovfor$jbc.analysis.testrequirements;

public class AbstractTestRequirement implements TestRequirement {
    /**
     * IMPORTANT: this field should be "volatile" to make the concurrent writes
     * available to subsequent reads when outputting coverage info. This should not
     * affect the happens-before relationships with the instrumented code, because
     * the instrumented code ONLY writes to this field and does not read from it.
     * Only the shutdown hook reads from this field.
     */
    private volatile boolean covered;

    protected AbstractTestRequirement() {
        this.covered = false;
    }

    public boolean isCovered() {
        return covered;
    }

    public void setCovered(boolean covered) {
        this.covered = covered;
    }
}
