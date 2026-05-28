package com.yapi.plugin.client;

import com.google.gson.*;
import com.yapi.plugin.config.YApiServerConfig;
import com.yapi.plugin.parser.model.YApiInterfaceInfo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class YApiClient {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final Gson GSON = new GsonBuilder().create();

    /**
     * Save or update an API interface in YApi.
     * @param server  YApi server config
     * @param info    The parsed interface info
     * @return response JSON string
     */
    public String createOrUpdateInterface(YApiServerConfig server, YApiInterfaceInfo info) throws IOException, InterruptedException {
        String baseUrl = server.getBaseUrl();
        String url = baseUrl + "/api/interface/save?token=" + server.getToken();

        JsonObject body = buildInterfaceBody(server, info);
        String json = GSON.toJson(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    /**
     * Get the category (menu) tree for a YApi project.
     */
    public String getCatMenu(YApiServerConfig server) throws IOException, InterruptedException {
        String baseUrl = server.getBaseUrl();
        String url = baseUrl + "/api/interface/getCatMenu?project_id=" + server.getProjectId()
                + "&token=" + server.getToken();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    /**
     * Add a new category to a YApi project.
     * @return the response body (contains the new category ID)
     */
    public String addCategory(YApiServerConfig server, String name, String desc) throws IOException, InterruptedException {
        String baseUrl = server.getBaseUrl();
        String url = baseUrl + "/api/interface/add_cat?token=" + server.getToken();

        JsonObject body = new JsonObject();
        body.addProperty("project_id", Integer.parseInt(server.getProjectId()));
        body.addProperty("name", name);
        body.addProperty("desc", desc != null ? desc : "");

        String json = GSON.toJson(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    /**
     * Resolve or create a category. Returns the category ID.
     * The category name is derived from the controller's title (first segment of path).
     */
    public String resolveCategoryId(YApiServerConfig server, String categoryName) throws IOException, InterruptedException {
        // Try to find existing category
        String menuJson = getCatMenu(server);
        String existingId = findCategoryId(menuJson, categoryName);
        if (existingId != null) {
            return existingId;
        }

        // Create new category
        String result = addCategory(server, categoryName, "");
        JsonObject json = JsonParser.parseString(result).getAsJsonObject();
        if (json.has("errcode") && json.get("errcode").getAsInt() == 0) {
            JsonObject data = json.getAsJsonObject("data");
            if (data.has("_id")) {
                return String.valueOf(data.get("_id").getAsInt());
            }
        }

        return "0";
    }

    private String findCategoryId(String menuJson, String name) {
        try {
            JsonObject json = JsonParser.parseString(menuJson).getAsJsonObject();
            if (json.has("errcode") && json.get("errcode").getAsInt() == 0) {
                JsonElement data = json.get("data");
                if (data.isJsonArray()) {
                    return findInCategories(data.getAsJsonArray(), name);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String findInCategories(JsonArray categories, String name) {
        for (JsonElement element : categories) {
            JsonObject cat = element.getAsJsonObject();
            if (cat.has("name") && name.equals(cat.get("name").getAsString())) {
                if (cat.has("_id")) {
                    return String.valueOf(cat.get("_id").getAsInt());
                }
            }
            if (cat.has("list")) {
                String found = findInCategories(cat.getAsJsonArray("list"), name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private JsonObject buildInterfaceBody(YApiServerConfig server, YApiInterfaceInfo info) {
        JsonObject body = new JsonObject();

        body.addProperty("project_id", Integer.parseInt(server.getProjectId()));
        body.addProperty("title", info.getTitle());
        body.addProperty("path", info.getPath());
        body.addProperty("method", info.getMethod());
        body.addProperty("catid", info.getCatId() != null ? Integer.parseInt(info.getCatId()) : 0);

        // Headers
        JsonArray headers = new JsonArray();
        for (YApiInterfaceInfo.YApiHeaderParam h : info.getReqHeaders()) {
            JsonObject hObj = new JsonObject();
            hObj.addProperty("name", h.getName());
            hObj.addProperty("value", h.getValue() != null ? h.getValue() : "");
            hObj.addProperty("desc", h.getDesc());
            hObj.addProperty("required", h.isRequired() ? "1" : "0");
            headers.add(hObj);
        }
        body.add("req_headers", headers);

        // Query params
        JsonArray queryParams = new JsonArray();
        for (YApiInterfaceInfo.YApiReqQueryParam q : info.getReqQuery()) {
            JsonObject qObj = new JsonObject();
            qObj.addProperty("name", q.getName());
            qObj.addProperty("desc", q.getDesc());
            qObj.addProperty("example", q.getExample() != null ? q.getExample() : "");
            qObj.addProperty("required", q.isRequired() ? "1" : "0");
            queryParams.add(qObj);
        }
        body.add("req_query", queryParams);

        // Request body — YApi expects req_body_other as a JSON string, not a JSON object
        if (info.getReqBodyOther() != null && !info.getReqBodyOther().equals("{}")) {
            body.addProperty("req_body_type", info.getReqBodyType() != null ? info.getReqBodyType() : "json");
            body.addProperty("req_body_other", info.getReqBodyOther());
        }

        // Response body — YApi expects res_body as a JSON string
        if (info.getResBody() != null) {
            JsonObject resBody = new JsonObject();
            resBody.addProperty("type", info.getResBody().getType());
            if (info.getResBody().getDescription() != null) {
                resBody.addProperty("description", info.getResBody().getDescription());
            }
            if (info.getResBody().getProperties() != null) {
                if ("array".equals(info.getResBody().getType())) {
                    YApiInterfaceInfo.YApiProperty item = info.getResBody().getProperties().get(0);
                    JsonObject items = new JsonObject();
                    items.addProperty("type", item.getType());
                    if (item.getDescription() != null && !item.getDescription().isEmpty()) {
                        items.addProperty("description", item.getDescription());
                    }
                    if (item.getChildren() != null && !item.getChildren().isEmpty()) {
                        items.add("properties", propertiesToJson(item.getChildren()));
                    }
                    resBody.add("items", items);
                } else {
                    resBody.add("properties", propertiesToJson(info.getResBody().getProperties()));
                }
            }
            if (info.getResBody().getRequired() != null && !info.getResBody().getRequired().isEmpty()) {
                JsonArray required = new JsonArray();
                for (String r : info.getResBody().getRequired()) {
                    required.add(r);
                }
                resBody.add("required", required);
            }
            body.addProperty("res_body", GSON.toJson(resBody));
            body.addProperty("res_body_type", "json");
        }

        return body;
    }

    private JsonElement propertiesToJson(List<YApiInterfaceInfo.YApiProperty> properties) {
        JsonObject obj = new JsonObject();
        for (YApiInterfaceInfo.YApiProperty prop : properties) {
            JsonObject propObj = new JsonObject();
            propObj.addProperty("type", prop.getType());
            if (prop.getDescription() != null && !prop.getDescription().isEmpty()) {
                propObj.addProperty("description", prop.getDescription());
            }
            if (prop.getChildren() != null && !prop.getChildren().isEmpty()) {
                if ("array".equals(prop.getType())) {
                    JsonObject items = new JsonObject();
                    items.addProperty("type", prop.getChildren().get(0).getType());
                    if (prop.getChildren().get(0).getChildren() != null && !prop.getChildren().get(0).getChildren().isEmpty()) {
                        items.add("properties", propertiesToJson(prop.getChildren().get(0).getChildren()));
                    }
                    propObj.add("items", items);
                } else {
                    propObj.add("properties", propertiesToJson(prop.getChildren()));
                }
            }
            if (prop.getRequired() != null && !prop.getRequired().isEmpty()) {
                JsonArray required = new JsonArray();
                for (String r : prop.getRequired()) {
                    required.add(r);
                }
                propObj.add("required", required);
            }
            obj.add(prop.getName(), propObj);
        }
        return obj;
    }
}
