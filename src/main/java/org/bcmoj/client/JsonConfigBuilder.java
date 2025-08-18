package org.bcmoj.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

public class JsonConfigBuilder {
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * 构建评测配置 JSON
     *
     * @param problemData   题目信息
     * @param securityCheck 是否启用安全检查
     * @param enableO2      是否启用 O2 优化
     * @param compareMode   输出比较模式 (1~4)
     * @param errorMode     是否启用错误注入
     * @param errorType     错误类型编号
     * @return 格式化后的 JSON 字符串
     */
    public static String buildConfig(ProblemData problemData,
                                     boolean securityCheck,
                                     boolean enableO2,
                                     int compareMode,
                                     boolean errorMode,
                                     int errorType) {
        try {
            ObjectNode config = mapper.createObjectNode();
            ObjectNode checkpoints = mapper.createObjectNode();

            // 构造 checkpoints
            for (int i = 0; i < problemData.examples().size(); i++) {
                Map<String, String> example = problemData.examples().get(i);
                int index = i + 1;
                checkpoints.put(index + "_in", example.get("input").trim());
                checkpoints.put(index + "_out", example.get("output").trim());
            }

            // 正常配置
            config.put("timeLimit", (Integer) problemData.problem().get("time_limit"));
            config.set("checkpoints", checkpoints);
            config.put("securityCheck", securityCheck);
            config.put("enableO2", enableO2);
            config.put("compareMode", compareMode);

            // 错误注入逻辑
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
