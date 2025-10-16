package org.qbitspark.nexgatenotificationserver.service.template;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
public class TemplateService {

    public String renderEmailTemplate(String templateName, Map<String, Object> data) {
        String template = loadTemplate("email/" + templateName + ".html");
        return renderTemplate(template, data);
    }

    public String renderSmsTemplate(String templateName, Map<String, Object> data) {
        String template = loadTemplate("sms/" + templateName + ".txt");
        return renderTemplate(template, data);
    }

    private String loadTemplate(String path) {
        try {
            ClassPathResource resource = new ClassPathResource("templates/" + path);
            byte[] bytes = resource.getInputStream().readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load template: {}", path, e);
            throw new RuntimeException("Template not found: " + path);
        }
    }

    private String renderTemplate(String template, Map<String, Object> data) {
        String result = template;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
}