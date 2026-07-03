package com.allinweb.ch.ai;

import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * Minimal OpenAI-compatible chat-completions client (works with Together AI, OpenAI, and
 * Anthropic's OpenAI-compatible endpoint). Raw {@code java.net.http.HttpClient} + Gson —
 * intentionally no SDK. Configuration comes from ARWeb.config ({@code ai_endpoint},
 * {@code ai_api_key}, {@code ai_model}, {@code ai_max_blocks}); the env var
 * {@code AR_AI_API_KEY} overrides the config key. The API key is never logged.
 */
@Slf4j
public final class AiChatClient {

    public static final String DEFAULT_ENDPOINT = "https://api.together.xyz/v1/chat/completions";
    public static final String DEFAULT_MODEL = "meta-llama/Llama-3.3-70B-Instruct-Turbo";
    public static final int DEFAULT_MAX_BLOCKS = 30;

    private static final Gson GSON = new Gson();

    public record AiConfig(String endpoint, String apiKey, String model, int maxBlocks) {}

    /** Reads ai_* properties, applies defaults; throws when no API key is configured. */
    public static AiConfig fromProperties(ARPropertyManager propertyManager) throws GenFlowException {
        String endpoint = propertyManager.getProperty(ARPropertyEnum.AI_ENDPOINT);
        String model = propertyManager.getProperty(ARPropertyEnum.AI_MODEL);
        String maxBlocksStr = propertyManager.getProperty(ARPropertyEnum.AI_MAX_BLOCKS);

        String apiKey = System.getenv("AR_AI_API_KEY");
        if (Strings.isNullOrEmpty(apiKey)) {
            apiKey = propertyManager.getProperty(ARPropertyEnum.AI_API_KEY);
        }
        if (Strings.isNullOrEmpty(apiKey)) {
            throw new GenFlowException(
                    "GEN FLOW - Missing Configuration",
                    "No AI API key configured. Set 'ai_api_key' in ARWeb.config "
                            + "(Configuration page) or the AR_AI_API_KEY environment variable.");
        }

        int maxBlocks = DEFAULT_MAX_BLOCKS;
        if (!Strings.isNullOrEmpty(maxBlocksStr)) {
            try {
                maxBlocks = Math.max(1, Integer.parseInt(maxBlocksStr.trim()));
            } catch (NumberFormatException ignored) {
            }
        }

        return new AiConfig(
                Strings.isNullOrEmpty(endpoint) ? DEFAULT_ENDPOINT : endpoint.trim(),
                apiKey.trim(),
                Strings.isNullOrEmpty(model) ? DEFAULT_MODEL : model.trim(),
                maxBlocks);
    }

    /** POSTs an OpenAI-compatible chat completion, returns {@code choices[0].message.content}. */
    public String chat(AiConfig cfg, String systemPrompt, String userPrompt) throws GenFlowException {
        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.model());
        body.addProperty("temperature", 0.2);
        body.addProperty("max_tokens", 16000);

        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", systemPrompt);
        messages.add(system);
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", userPrompt);
        messages.add(user);
        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(cfg.endpoint()))
                // Large navigation-surface generations (max_tokens ~16k, 40+ blocks) routinely run
                // past 2 min on 70B-class models — 120s was timing out mid-stream.
                .timeout(Duration.ofSeconds(300))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + cfg.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();

        HttpResponse<String> response;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            log.info("GEN FLOW — calling {} model={}", cfg.endpoint(), cfg.model());
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new GenFlowException(
                    "GEN FLOW - AI Request Failed", "Could not reach " + cfg.endpoint() + ": " + e.getMessage(), e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String head = response.body() == null
                    ? ""
                    : response.body().substring(0, Math.min(500, response.body().length()));
            throw new GenFlowException(
                    "GEN FLOW - AI Request Failed",
                    "HTTP " + response.statusCode() + " from " + cfg.endpoint() + ": " + head);
        }

        try {
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            return root.getAsJsonArray("choices")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content")
                    .getAsString();
        } catch (Exception e) {
            throw new GenFlowException(
                    "GEN FLOW - AI Response Unreadable",
                    "Unexpected response shape from " + cfg.endpoint() + ": " + e.getMessage(),
                    e);
        }
    }
}
