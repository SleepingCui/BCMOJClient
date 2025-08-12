package org.bcmoj.client;

public class TestCaseResult {
    private final String index;
    private final String resultText;
    private final int timeUsed;

    public TestCaseResult(String index, int resultCode, String resultText, int timeUsed) {
        this.index = index;
        this.resultText = resultText;
        this.timeUsed = timeUsed;
    }

    public String getIndex() { return index; }

    public String getResultText() { return resultText; }
    public int getTimeUsed() { return timeUsed; }
}