package org.bcmoj.client.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bcmoj.client.CodingClient;
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
        long totalMemory = 0;

        for (String response : responses) {
            try {
                JsonNode data = mapper.readTree(response);
                boolean isNewFormat = isResponseNewFormat(data);

                if (isNewFormat) {
                    //new format
                    JsonNode checkpointsNode = data.get("checkpoints");
                    Iterator<Map.Entry<String, JsonNode>> checkpointsFields = checkpointsNode.fields();
                    while (checkpointsFields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = checkpointsFields.next();
                        String index = entry.getKey();
                        JsonNode testCaseData = entry.getValue();

                        int resultCode = testCaseData.get("res").asInt();
                        String resultText = resultMapping.getOrDefault(resultCode, "Unknown Status");
                        double timeUsed = testCaseData.get("time").asDouble();
                        long memoryUsed = testCaseData.get("mem").asLong();

                        testResults.add(new TestCaseResult(index, resultText, timeUsed, memoryUsed));
                        totalTests++;
                        totalTime += timeUsed;
                        totalMemory += memoryUsed;

                        if (resultCode == 1) {
                            accepted++;
                        }
                    }
                } else {
                    //old
                    Iterator<String> fieldNames = data.fieldNames();
                    while (fieldNames.hasNext()) {
                        String key = fieldNames.next();
                        if (key.endsWith("_res")) {
                            String index = key.split("_")[0];
                            int resultCode = data.get(key).asInt();
                            String resultText = resultMapping.getOrDefault(resultCode, "Unknown Status");
                            double timeUsed = data.has(index + "_time") ? data.get(index + "_time").asDouble() : 0.0;
                            long memoryUsed = data.has(index + "_mem") ? data.get(index + "_mem").asLong() : 0L;

                            testResults.add(new TestCaseResult(index, resultText, timeUsed, memoryUsed));
                            totalTests++;
                            totalTime += timeUsed;
                            totalMemory += memoryUsed;

                            if (resultCode == 1) {
                                accepted++;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                CodingClient.log("Failed to parse response: " + response + ". Error: " + e.getMessage());
            }
        }

        double averageTime = totalTests > 0 ? totalTime / totalTests : 0.0;
        long averageMemory = totalTests > 0 ? totalMemory / totalTests : 0L;
        return new EvaluationResult(testResults, accepted, totalTests, averageTime, averageMemory);
    }

    private static boolean isResponseNewFormat(JsonNode data) {
        if (!data.has("checkpoints")) {
            return false;
        }

        JsonNode checkpointsNode = data.get("checkpoints");
        if (!checkpointsNode.isObject()) {
            return false;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = checkpointsNode.fields();
        if (fields.hasNext()) {
            JsonNode firstCheckpoint = fields.next().getValue();
            return firstCheckpoint.isObject() && firstCheckpoint.has("res") && firstCheckpoint.has("time") && firstCheckpoint.has("mem");
        }

        return false;
    }
}