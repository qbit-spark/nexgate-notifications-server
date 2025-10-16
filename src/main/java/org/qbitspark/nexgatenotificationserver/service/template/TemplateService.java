package org.qbitspark.nexgatenotificationserver.service.template;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class TemplateService {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    private static final Pattern LOOP_PATTERN = Pattern.compile(
            "\\{\\{#each (\\w+)\\}\\}(.+?)\\{\\{/each\\}\\}",
            Pattern.DOTALL
    );
    private static final Pattern IF_PATTERN = Pattern.compile(
            "\\{\\{#if (\\w+(?:\\.\\w+)*)\\}\\}(.+?)(?:\\{\\{else\\}\\}(.+?))?\\{\\{/if\\}\\}",
            Pattern.DOTALL
    );

    public String renderEmailTemplate(String templateName, Map<String, Object> data) {
        String template = loadTemplate("email/" + templateName + ".html");
        if (template == null) {
            log.warn("⚠️ Email template not found: {}, using fallback", templateName);
            template = getFallbackEmailTemplate(templateName);
        }
        return renderTemplate(template, data);
    }

    public String renderSmsTemplate(String templateName, Map<String, Object> data) {
        String template = loadTemplate("sms/" + templateName + ".txt");
        if (template == null) {
            log.warn("⚠️ SMS template not found: {}, using fallback", templateName);
            template = getFallbackSmsTemplate(templateName);
        }
        return renderTemplate(template, data);
    }

    private String loadTemplate(String path) {
        try {
            ClassPathResource resource = new ClassPathResource("templates/" + path);
            byte[] bytes = resource.getInputStream().readAllBytes();
            String content = new String(bytes, StandardCharsets.UTF_8);
            log.info("✅ Loaded template: {}", path);
            return content;
        } catch (IOException e) {
            log.warn("❌ Failed to load template: {}", path);
            return null;
        }
    }

    private String renderTemplate(String template, Map<String, Object> data) {
        // Step 1: Process loops first
        template = processLoops(template, data);

        // Step 2: Process conditionals
        template = processConditionals(template, data);

        // Step 3: Process simple placeholders
        template = processPlaceholders(template, data);

        return template;
    }

    private String processLoops(String template, Map<String, Object> data) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = LOOP_PATTERN.matcher(template);

        while (matcher.find()) {
            String listName = matcher.group(1);
            String itemTemplate = matcher.group(2);

            Object listObj = data.get(listName);
            StringBuilder loopResult = new StringBuilder();

            if (listObj instanceof List) {
                List<?> list = (List<?>) listObj;
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);

                    // Create context for this iteration
                    String itemContent = itemTemplate;

                    // Replace {{this.property}} with actual values
                    if (item instanceof Map) {
                        Map<?, ?> itemMap = (Map<?, ?>) item;
                        itemContent = replaceItemPlaceholders(itemContent, itemMap, i, list.size());
                    } else {
                        // Simple value like String, Number
                        itemContent = itemContent.replace("{{this}}", String.valueOf(item));
                        itemContent = itemContent.replace("{{index}}", String.valueOf(i));
                        itemContent = itemContent.replace("{{count}}", String.valueOf(list.size()));
                    }

                    loopResult.append(itemContent);
                }
            } else {
                log.warn("⚠️ Loop variable '{}' is not a list", listName);
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(loopResult.toString()));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private String replaceItemPlaceholders(String template, Map<?, ?> itemData, int index, int total) {
        // Replace {{this.property}}
        for (Map.Entry<?, ?> entry : itemData.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
            template = template.replace("{{this." + key + "}}", value);
        }

        // Replace special loop variables
        template = template.replace("{{index}}", String.valueOf(index + 1)); // 1-based for display
        template = template.replace("{{index0}}", String.valueOf(index)); // 0-based
        template = template.replace("{{count}}", String.valueOf(total));
        template = template.replace("{{isFirst}}", String.valueOf(index == 0));
        template = template.replace("{{isLast}}", String.valueOf(index == total - 1));

        return template;
    }

    private String processConditionals(String template, Map<String, Object> data) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = IF_PATTERN.matcher(template);

        while (matcher.find()) {
            String condition = matcher.group(1);
            String ifContent = matcher.group(2);
            String elseContent = matcher.group(3); // might be null

            String resolvedValue = resolveValue(condition, data);
            boolean isTrue = isTruthy(resolvedValue);

            String replacement = isTrue ? ifContent : (elseContent != null ? elseContent : "");
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private boolean isTruthy(String value) {
        if (value == null || value.isEmpty()) return false;
        if (value.equalsIgnoreCase("false")) return false;
        if (value.equals("0")) return false;
        return true;
    }

    private String processPlaceholders(String template, Map<String, Object> data) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);

        while (matcher.find()) {
            String placeholder = matcher.group(1).trim();
            String value = resolveValue(placeholder, data);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private String resolveValue(String path, Map<String, Object> data) {
        String[] keys = path.split("\\.");
        Object current = data;

        for (String key : keys) {
            if (current == null) {
                return "";
            }

            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(key);
            } else if (current instanceof List) {
                current = resolveListValue((List<?>) current, key, path);
            } else {
                return "";
            }
        }

        return formatValue(current);
    }

    private Object resolveListValue(List<?> list, String key, String fullPath) {
        if (key.matches("\\d+")) {
            int index = Integer.parseInt(key);
            if (index >= 0 && index < list.size()) {
                return list.get(index);
            } else {
                return "";
            }
        }

        switch (key.toLowerCase()) {
            case "first": return list.isEmpty() ? "" : list.get(0);
            case "last": return list.isEmpty() ? "" : list.get(list.size() - 1);
            case "size":
            case "count":
            case "length": return list.size();
            default: return "";
        }
    }

    private String formatValue(Object value) {
        if (value == null) return "";
        if (value instanceof List) return String.valueOf(((List<?>) value).size());
        if (value instanceof Map) return "[object]";
        return value.toString();
    }

    private String getFallbackEmailTemplate(String templateName) {
        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body>
                    <h2>Notification from Nexgate</h2>
                    <p>Template: %s</p>
                    <p>This is a fallback template.</p>
                </body>
                </html>
                """.formatted(templateName);
    }

    private String getFallbackSmsTemplate(String templateName) {
        return "Nexgate notification. Template: " + templateName;
    }
}