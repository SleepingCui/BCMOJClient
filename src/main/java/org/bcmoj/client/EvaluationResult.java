package org.bcmoj.client;


import java.util.List;

public record EvaluationResult(List<TestCaseResult> testResults, int accepted, int totalTests, double averageTime) {
}
