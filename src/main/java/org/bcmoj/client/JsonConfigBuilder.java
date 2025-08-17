package org.bcmoj.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

public class JsonConfigBuilder {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static String buildConfig(ProblemData problemData, boolean securityCheck, boolean errorMode, int errorType) {
        try {
            ObjectNode config = mapper.createObjectNode();
            ObjectNode checkpoints = mapper.createObjectNode();
            for (int i = 0; i < problemData.examples().size(); i++) {
                Map<String, String> example = problemData.examples().get(i);
                int index = i + 1;
                checkpoints.put(index + "_in", example.get("input").trim());
                checkpoints.put(index + "_out", example.get("output").trim());
            }
            config.put("timeLimit", (Integer) problemData.problem().get("time_limit"));
            config.set("checkpoints", checkpoints);
            config.put("securityCheck", securityCheck);

            if (errorMode) {
                switch (errorType) {
                    case 1:
                        config.remove("timeLimit");
                        break;
                    case 2:
                        config.put("timeLimit", -100);
                        break;
                    case 3:
                        config.remove("securityCheck");
                        break;
                    case 4:
                        config.put("checkpoints", "this should be an object");
                        break;
                    case 5:
                        ObjectNode invalidCheckpoints = mapper.createObjectNode();
                        for (int i = 0; i < problemData.examples().size(); i++) {
                            Map<String, String> example = problemData.examples().get(i);
                            invalidCheckpoints.put((i + 1) + "_in", example.get("input").trim());
                        }
                        config.set("checkpoints", invalidCheckpoints);
                        break;
                    case 6:
                        config.set("checkpoints", mapper.createObjectNode());
                        break;
                }
            }
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build config", e);
        }
    }
}
