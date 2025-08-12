package org.bcmoj.client;

import java.util.List;
import java.util.Map;

public record ProblemData(Map<String, Object> problem, List<Map<String, String>> examples) {
}
