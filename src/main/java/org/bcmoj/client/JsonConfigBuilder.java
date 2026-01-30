package org.bcmoj.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;

public class JsonConfigBuilder {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static String buildConfig(ProblemData problemData, boolean securityCheck, boolean enableO2, int compareMode, boolean errorMode, int errorType, boolean useNewFormat) {
        try {
            ObjectNode config;
            if (useNewFormat) {
                config = buildNewFormatConfig(problemData, securityCheck, enableO2, compareMode);
            } else {
                config = buildOldFormatConfig(problemData, securityCheck, enableO2, compareMode);
            }
            if (errorMode) {
                applyErrorConfigToFormat(config, errorType, useNewFormat);
            }

            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build config", e);
        }
    }

    private static ObjectNode buildNewFormatConfig(ProblemData problemData, boolean securityCheck, boolean enableO2, int compareMode) {
        ObjectNode config = mapper.createObjectNode();

        config.put("time_limit", (Integer) problemData.problem().get("time_limit"));
        config.put("mem_limit", 32768);
        config.put("enable_security_check", securityCheck);
        config.put("enable_o2", enableO2);
        config.put("compare_mode", compareMode);

        ObjectNode checkpoints = mapper.createObjectNode();
        for (int i = 0; i < problemData.examples().size(); i++) {
            Map<String, String> example = problemData.examples().get(i);
            int index = i + 1;
            ObjectNode checkpoint = mapper.createObjectNode();
            checkpoint.put("in", example.get("input").trim());
            checkpoint.put("out", example.get("output").trim());
            checkpoints.set(String.valueOf(index), checkpoint);
        }
        config.set("checkpoints", checkpoints);

        return config;
    }

    private static ObjectNode buildOldFormatConfig(ProblemData problemData, boolean securityCheck, boolean enableO2, int compareMode) {
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
        config.put("enableO2", enableO2);
        config.put("compareMode", compareMode);

        return config;
    }

    private static void applyErrorConfigToFormat(ObjectNode config, int errorType, boolean useNewFormat) {
        if (useNewFormat) {
            applyNewFormatErrorConfig(config, errorType);
        } else {
            applyOldFormatErrorConfig(config, errorType);
        }
    }

    private static void applyNewFormatErrorConfig(ObjectNode config, int errorType) {
        switch (errorType) {
            case 1:
                config.remove("time_limit");
                break;
            case 2:
                config.put("time_limit", -100);
                break;
            case 3:
                config.remove("enable_security_check");
                break;
            case 4:
                config.put("checkpoints", "this should be an object");
                break;
            case 5:
                if (config.has("checkpoints") && config.get("checkpoints").isObject()) {
                    ObjectNode checkpoints = (ObjectNode) config.get("checkpoints");
                    Iterator<String> it = checkpoints.fieldNames();
                    while (it.hasNext()) {
                        String field = it.next();
                        ObjectNode checkpoint = (ObjectNode) checkpoints.get(field);
                        if (checkpoint.has("out")) {
                            checkpoint.remove("out");
                        }
                    }
                }
                break;
            case 6:
                config.set("checkpoints", mapper.createObjectNode());
                break;
        }
    }

    private static void applyOldFormatErrorConfig(ObjectNode config, int errorType) {
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
                if (config.has("checkpoints")) {
                    ObjectNode checkpoints = (ObjectNode) config.get("checkpoints");
                    Iterator<String> it = checkpoints.fieldNames();
                    while (it.hasNext()) {
                        String field = it.next();
                        if (field.endsWith("_out")) {
                            it.remove();
                        }
                    }
                    config.set("checkpoints", checkpoints);
                }
                break;
            case 6:
                config.set("checkpoints", mapper.createObjectNode());
                break;
        }
    }

    public static String applyErrorConfig(String json, boolean errorMode, int errorType, boolean useNewFormat) {
        if (!errorMode) return json;
        try {
            ObjectNode config = (ObjectNode) mapper.readTree(json);
            applyErrorConfigToFormat(config, errorType, useNewFormat);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply error config to custom JSON", e);
        }
    }
}