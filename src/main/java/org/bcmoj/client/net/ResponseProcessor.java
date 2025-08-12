package org.bcmoj.client.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bcmoj.client.EvaluationResult;
import org.bcmoj.client.TestCaseResult;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ResponseProcessor {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static EvaluationResult processResponses(List<String> responses, Map<Integer, String> resultMapping) {
        List<TestCaseResult> testResults = new ArrayList<>();
        int accepted = 0;
        int totalTests = 0;
        double totalTime = 0.0;

        for (String response : responses) {
            try {
                JsonNode data = mapper.readTree(response);
                Iterator<String> fieldNames = data.fieldNames();
                while (fieldNames.hasNext()) {
                    String key = fieldNames.next();
                    if (key.endsWith("_res")) {
                        String index = key.split("_")[0];
                        int resultCode = data.get(key).asInt();
                        String resultText = resultMapping.getOrDefault(resultCode, "Unknown Status");
                        int timeUsed = data.has(index + "_time") ? data.get(index + "_time").asInt() : 0;

                        testResults.add(new TestCaseResult(index, resultText, timeUsed));
                        totalTests++;
                        totalTime += timeUsed;

                        if (resultCode == 1) {
                            accepted++;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to parse response: " + response);
            }
        }
        double averageTime = totalTests > 0 ? totalTime / totalTests : 0.0;
        return new EvaluationResult(testResults, accepted, totalTests, averageTime);
    }
}
