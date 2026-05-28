package com.yapi.plugin.parser;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.yapi.plugin.parser.model.YApiInterfaceInfo;

import java.util.*;

public class SpringControllerParser {

    private static final String REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping";
    private static final String GET_MAPPING = "org.springframework.web.bind.annotation.GetMapping";
    private static final String POST_MAPPING = "org.springframework.web.bind.annotation.PostMapping";
    private static final String PUT_MAPPING = "org.springframework.web.bind.annotation.PutMapping";
    private static final String DELETE_MAPPING = "org.springframework.web.bind.annotation.DeleteMapping";
    private static final String PATCH_MAPPING = "org.springframework.web.bind.annotation.PatchMapping";

    private static final String REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";
    private static final String CONTROLLER = "org.springframework.stereotype.Controller";

    private static final String REQUEST_PARAM = "org.springframework.web.bind.annotation.RequestParam";
    private static final String PATH_VARIABLE = "org.springframework.web.bind.annotation.PathVariable";
    private static final String REQUEST_BODY = "org.springframework.web.bind.annotation.RequestBody";
    private static final String REQUEST_HEADER = "org.springframework.web.bind.annotation.RequestHeader";

    private final DtoParser dtoParser = new DtoParser();
    private final PsiClass controllerClass;
    private final Set<String> visitedMethods = new HashSet<>();

    public SpringControllerParser(PsiClass controllerClass) {
        this.controllerClass = controllerClass;
    }

    public boolean isController() {
        return hasAnnotation(controllerClass, REST_CONTROLLER) || hasAnnotation(controllerClass, CONTROLLER);
    }

    public List<YApiInterfaceInfo> parseClass() {
        List<YApiInterfaceInfo> interfaces = new ArrayList<>();
        if (!isController()) return interfaces;

        String classPath = getRequestMappingPath(controllerClass);
        String classTitle = getControllerTitle();

        for (PsiMethod method : controllerClass.getMethods()) {
            if (method.isConstructor() || method.hasModifierProperty(PsiModifier.PRIVATE)) continue;

            String signature = method.getName() + "(" + method.getParameterList().getText() + ")";
            if (visitedMethods.contains(signature)) continue;
            visitedMethods.add(signature);

            List<YApiInterfaceInfo> methodInterfaces = parseMethod(method, classPath, classTitle);
            interfaces.addAll(methodInterfaces);
        }

        return interfaces;
    }

    public YApiInterfaceInfo parseMethod(PsiMethod method) {
        if (!isController()) return null;
        String classPath = getRequestMappingPath(controllerClass);
        String classTitle = getControllerTitle();
        List<YApiInterfaceInfo> list = parseMethod(method, classPath, classTitle);
        return list.isEmpty() ? null : list.get(0);
    }

    private List<YApiInterfaceInfo> parseMethod(PsiMethod method, String classPath, String classTitle) {
        List<YApiInterfaceInfo> results = new ArrayList<>();

        Map<String, String> methodMappings = getMethodMappings(method);
        if (methodMappings.isEmpty()) return results;

        for (Map.Entry<String, String> entry : methodMappings.entrySet()) {
            String httpMethod = entry.getKey();
            String methodPath = entry.getValue();

            YApiInterfaceInfo info = new YApiInterfaceInfo();
            info.setTitle(buildMethodTitle(method, classTitle));
            info.setMethod(httpMethod);
            info.setPath(buildFullPath(classPath, methodPath));

            // Parse request parameters
            parseRequestParameters(method, info);

            // Parse request body
            parseRequestBody(method, info);

            // Parse response body
            parseResponseBody(method, info);

            results.add(info);
        }

        return results;
    }

    private void parseRequestParameters(PsiMethod method, YApiInterfaceInfo info) {
        for (PsiParameter param : method.getParameterList().getParameters()) {
            // @RequestParam — Spring 默认 required = true
            PsiAnnotation requestParam = findAnnotation(param, REQUEST_PARAM, "RequestParam");
            if (requestParam != null) {
                YApiInterfaceInfo.YApiReqQueryParam queryParam = new YApiInterfaceInfo.YApiReqQueryParam();
                queryParam.setName(getAnnotationValue(requestParam, "value", param.getName()));
                queryParam.setRequired(getAnnotationBooleanValue(requestParam, "required", true));
                queryParam.setExample(getAnnotationValue(requestParam, "defaultValue", ""));
                queryParam.setDesc("");
                info.getReqQuery().add(queryParam);
                continue;
            }

            // @PathVariable
            PsiAnnotation pathVar = findAnnotation(param, PATH_VARIABLE, "PathVariable");
            if (pathVar != null) {
                YApiInterfaceInfo.YApiReqQueryParam pathParam = new YApiInterfaceInfo.YApiReqQueryParam();
                pathParam.setName(getAnnotationValue(pathVar, "value", param.getName()));
                pathParam.setRequired(true);
                pathParam.setDesc("path variable");
                info.getReqQuery().add(pathParam);
                continue;
            }

            // @RequestHeader
            PsiAnnotation reqHeader = findAnnotation(param, REQUEST_HEADER, "RequestHeader");
            if (reqHeader != null) {
                YApiInterfaceInfo.YApiHeaderParam headerParam = new YApiInterfaceInfo.YApiHeaderParam();
                headerParam.setName(getAnnotationValue(reqHeader, "value", param.getName()));
                headerParam.setRequired(getAnnotationBooleanValue(reqHeader, "required", false));
                headerParam.setDesc("");
                info.getReqHeaders().add(headerParam);
                continue;
            }
        }
    }

    private void parseRequestBody(PsiMethod method, YApiInterfaceInfo info) {
        for (PsiParameter param : method.getParameterList().getParameters()) {
            if (findAnnotation(param, REQUEST_BODY, "RequestBody") != null) {
                PsiType type = param.getType();
                if (!"org.springframework.web.multipart.MultipartFile".equals(type.getCanonicalText())) {
                    YApiInterfaceInfo.YApiReqBodyParam body = dtoParser.parseDto(type);
                    info.setReqBodyOther(toJsonSchema(body));
                    info.setReqBodyType("json");
                }
            }
        }
    }

    private void parseResponseBody(PsiMethod method, YApiInterfaceInfo info) {
        PsiType returnType = method.getReturnType();
        if (returnType == null || "void".equals(returnType.getCanonicalText())) {
            return;
        }

        // 直接传入完整返回值类型（包含泛型包装类，如 RestResult<T>），
        // DtoParser 通过 PsiSubstitutor 自动替换泛型参数 T 为实际类型，
        // 从而生成包含包装层（code/message/data）+ 内部数据类型的完整 JSON Schema。
        YApiInterfaceInfo.YApiReqBodyParam resBody = dtoParser.parseDto(returnType);
        info.setResBody(resBody);
    }

    private Map<String, String> getMethodMappings(PsiMethod method) {
        Map<String, String> mappings = new LinkedHashMap<>();

        // @GetMapping
        PsiAnnotation getMapping = findAnnotation(method, GET_MAPPING, "GetMapping");
        if (getMapping != null) {
            for (String path : getAnnotationValues(getMapping)) {
                mappings.put("GET", path);
            }
            return mappings;
        }

        // @PostMapping
        PsiAnnotation postMapping = findAnnotation(method, POST_MAPPING, "PostMapping");
        if (postMapping != null) {
            for (String path : getAnnotationValues(postMapping)) {
                mappings.put("POST", path);
            }
            return mappings;
        }

        // @PutMapping
        PsiAnnotation putMapping = findAnnotation(method, PUT_MAPPING, "PutMapping");
        if (putMapping != null) {
            for (String path : getAnnotationValues(putMapping)) {
                mappings.put("PUT", path);
            }
            return mappings;
        }

        // @DeleteMapping
        PsiAnnotation deleteMapping = findAnnotation(method, DELETE_MAPPING, "DeleteMapping");
        if (deleteMapping != null) {
            for (String path : getAnnotationValues(deleteMapping)) {
                mappings.put("DELETE", path);
            }
            return mappings;
        }

        // @PatchMapping
        PsiAnnotation patchMapping = findAnnotation(method, PATCH_MAPPING, "PatchMapping");
        if (patchMapping != null) {
            for (String path : getAnnotationValues(patchMapping)) {
                mappings.put("PATCH", path);
            }
            return mappings;
        }

        // @RequestMapping with method
        PsiAnnotation requestMapping = findAnnotation(method, REQUEST_MAPPING, "RequestMapping");
        if (requestMapping != null) {
            List<String> httpMethods = getAnnotationMethodValues(requestMapping);
            for (String path : getAnnotationValues(requestMapping)) {
                for (String methodName : httpMethods) {
                    mappings.put(methodName, path);
                }
            }
        }

        return mappings;
    }

    private String getRequestMappingPath(PsiClass psiClass) {
        PsiAnnotation annotation = findAnnotation(psiClass, REQUEST_MAPPING, "RequestMapping");
        if (annotation == null) return "";
        return getFirstAnnotationValue(annotation);
    }

    private String getControllerTitle() {
        // Try @Api(tags = "xxx") — tags is String[], so handle array
        PsiAnnotation apiAnn = controllerClass.getAnnotation("io.swagger.annotations.Api");
        if (apiAnn != null) {
            String val = extractFirstString(apiAnn.findAttributeValue("tags"));
            if (val != null && !val.isEmpty()) return val;
            // Also try "value" attribute
            val = extractFirstString(apiAnn.findAttributeValue("value"));
            if (val != null && !val.isEmpty()) return val;
        }
        // Try Swagger 3 @Tag
        PsiAnnotation tagAnn = controllerClass.getAnnotation("io.swagger.v3.oas.annotations.tags.Tag");
        if (tagAnn != null) {
            String val = extractFirstString(tagAnn.findAttributeValue("name"));
            if (val != null && !val.isEmpty()) return val;
        }

        // Fallback: use class name
        String name = controllerClass.getName();
        if (name != null && name.endsWith("Controller")) {
            name = name.substring(0, name.length() - "Controller".length());
        }
        return name != null ? name : "Unknown";
    }

    private String buildMethodTitle(PsiMethod method, String classTitle) {
        // 1. Try @ApiOperation("黑名单管理-列表")
        PsiAnnotation apiOp = method.getAnnotation("io.swagger.annotations.ApiOperation");
        if (apiOp != null) {
            String val = getAnnotationValue(apiOp, "value", null);
            if (val != null && !val.isEmpty()) return val;
        }
        // 2. Try Swagger 3 @Operation
        PsiAnnotation operation = method.getAnnotation("io.swagger.v3.oas.annotations.Operation");
        if (operation != null) {
            String val = getAnnotationValue(operation, "summary", null);
            if (val != null && !val.isEmpty()) return val;
        }
        // 3. Fallback: ControllerTitle.methodName
        return classTitle + "." + method.getName();
    }

    private String buildFullPath(String classPath, String methodPath) {
        StringBuilder sb = new StringBuilder();
        if (!classPath.isEmpty()) {
            sb.append(classPath.startsWith("/") ? classPath : "/" + classPath);
        }
        if (!methodPath.isEmpty()) {
            sb.append(methodPath.startsWith("/") ? methodPath : "/" + methodPath);
        }
        String path = sb.toString().replaceAll("/+", "/");
        return path.isEmpty() ? "/" : path;
    }

    // ---- Annotation helpers ----

    private boolean hasAnnotation(PsiClass psiClass, String qualifiedName) {
        // Try full qualified name first
        PsiAnnotation annotation = psiClass.getAnnotation(qualifiedName);
        if (annotation != null) return true;

        // Fallback: check by short name
        String shortName = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
        PsiModifierList modifierList = psiClass.getModifierList();
        if (modifierList == null) return false;
        return findAnnotationByShortName(modifierList, shortName) != null;
    }

    /**
     * Find annotation by qualified name, with short-name fallback.
     */
    private PsiAnnotation findAnnotation(PsiModifierListOwner owner, String qualifiedName, String shortName) {
        PsiAnnotation ann = owner.getAnnotation(qualifiedName);
        if (ann != null) return ann;
        return findAnnotationByShortName(owner.getModifierList(), shortName);
    }

    private PsiAnnotation findAnnotationByShortName(PsiModifierList modifierList, String shortName) {
        if (modifierList == null) return null;
        for (PsiAnnotation ann : modifierList.getAnnotations()) {
            String qn = ann.getQualifiedName();
            // qn 是完整限定名(如 org.springframework.web.bind.annotation.GetMapping), 
            // shortName 是短名(如 GetMapping), 需用 endsWith 匹配
            if (qn != null && (qn.endsWith("." + shortName) || qn.equals(shortName))) return ann;
            // Unresolved annotation — check text
            if (qn == null) {
                String annText = ann.getText();
                if (annText != null && annText.contains(shortName)) return ann;
            }
        }
        return null;
    }

    private List<String> getAnnotationValues(PsiAnnotation annotation) {
        List<String> values = new ArrayList<>();

        // Try "value" attribute
        PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
        if (value != null) {
            values.addAll(extractStrings(value));
        }

        // Try "path" attribute
        PsiAnnotationMemberValue path = annotation.findAttributeValue("path");
        if (path != null) {
            List<String> pathVals = extractStrings(path);
            if (!pathVals.isEmpty()) {
                // path overrides value if set
                return pathVals;
            }
        }

        // Default
        if (values.isEmpty()) {
            values.add("");
        }

        return values;
    }

    private String getFirstAnnotationValue(PsiAnnotation annotation) {
        List<String> values = getAnnotationValues(annotation);
        return values.isEmpty() ? "" : values.get(0);
    }

    private String getAnnotationValue(PsiAnnotation annotation, String attribute, String defaultValue) {
        PsiAnnotationMemberValue memberValue = annotation.findAttributeValue(attribute);
        if (memberValue == null) return defaultValue;
        String text = memberValue.getText();
        // Strip quotes
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1);
        }
        return text;
    }

    private boolean getAnnotationBooleanValue(PsiAnnotation annotation, String attribute, boolean defaultValue) {
        PsiAnnotationMemberValue memberValue = annotation.findAttributeValue(attribute);
        if (memberValue == null) return defaultValue;
        return "true".equals(memberValue.getText());
    }

    private List<String> extractStrings(PsiAnnotationMemberValue value) {
        List<String> result = new ArrayList<>();
        if (value instanceof PsiLiteralExpression) {
            String text = extractRawText(value);
            if (text != null && !text.isEmpty()) {
                result.add(text);
            } else {
                result.add("");
            }
        } else if (value instanceof PsiArrayInitializerMemberValue) {
            PsiArrayInitializerMemberValue array = (PsiArrayInitializerMemberValue) value;
            for (PsiAnnotationMemberValue item : array.getInitializers()) {
                String text = extractRawText(item);
                if (text != null) {
                    result.add(text);
                }
            }
        } else {
            result.add("");
        }
        return result;
    }

    private String extractRawText(PsiAnnotationMemberValue value) {
        String text = value.getText();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1);
        }
        return text;
    }

    /**
     * 解析 @RequestMapping(method = ...) 的 HTTP 方法值，支持单值和数组的 RequestMethod 枚举或字符串。
     */
    private List<String> getAnnotationMethodValues(PsiAnnotation annotation) {
        List<String> methods = new ArrayList<>();
        PsiAnnotationMemberValue value = annotation.findAttributeValue("method");
        if (value == null) {
            methods.add("GET");
            return methods;
        }

        List<String> rawValues = new ArrayList<>();
        if (value instanceof PsiArrayInitializerMemberValue) {
            PsiArrayInitializerMemberValue array = (PsiArrayInitializerMemberValue) value;
            for (PsiAnnotationMemberValue item : array.getInitializers()) {
                rawValues.add(extractRawText(item));
            }
        } else {
            rawValues.add(extractRawText(value));
        }

        for (String raw : rawValues) {
            if (raw == null || raw.isEmpty()) continue;
            // e.g. "RequestMethod.POST" → "POST", or "POST" directly
            String method = raw.contains("RequestMethod.")
                    ? raw.substring(raw.lastIndexOf("RequestMethod.") + "RequestMethod.".length())
                    : raw;
            method = method.toUpperCase();
            if (Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "TRACE").contains(method)) {
                methods.add(method);
            } else {
                methods.add("GET");
            }
        }
        return methods;
    }

    /**
     * Extract first string from annotation member value, handling both single values and arrays.
     */
    private String extractFirstString(PsiAnnotationMemberValue value) {
        if (value == null) return null;
        if (value instanceof PsiLiteralExpression) {
            return extractRawText(value);
        }
        if (value instanceof PsiArrayInitializerMemberValue arr) {
            if (arr.getInitializers().length > 0) {
                return extractRawText(arr.getInitializers()[0]);
            }
        }
        return null;
    }

    // ---- JSON helpers ----

    private String toJsonSchema(YApiInterfaceInfo.YApiReqBodyParam body) {
        if (body == null) return "{}";

        com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
        obj.addProperty("type", body.getType() != null ? body.getType() : "object");
        if (body.getDescription() != null) {
            obj.addProperty("description", body.getDescription());
        }

        // Convert properties list → JSON object (keyed by field name)
        if (body.getProperties() != null && !body.getProperties().isEmpty()) {
            if ("array".equals(body.getType())) {
                // Array root type → use "items" per JSON Schema
                YApiInterfaceInfo.YApiProperty item = body.getProperties().get(0);
                com.google.gson.JsonObject items = new com.google.gson.JsonObject();
                items.addProperty("type", item.getType());
                if (item.getDescription() != null && !item.getDescription().isEmpty()) {
                    items.addProperty("description", item.getDescription());
                }
                if (item.getChildren() != null && !item.getChildren().isEmpty()) {
                    items.add("properties", propertiesToJsonObj(item.getChildren()));
                }
                obj.add("items", items);
            } else {
                obj.add("properties", propertiesToJsonObj(body.getProperties()));
            }
        }

        // Required fields
        if (body.getRequired() != null && !body.getRequired().isEmpty()) {
            com.google.gson.JsonArray req = new com.google.gson.JsonArray();
            for (String r : body.getRequired()) req.add(r);
            obj.add("required", req);
        }

        return new com.google.gson.Gson().toJson(obj);
    }

    private com.google.gson.JsonObject propertiesToJsonObj(List<YApiInterfaceInfo.YApiProperty> properties) {
        com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
        for (YApiInterfaceInfo.YApiProperty prop : properties) {
            com.google.gson.JsonObject propObj = new com.google.gson.JsonObject();
            propObj.addProperty("type", prop.getType());
            if (prop.getDescription() != null && !prop.getDescription().isEmpty()) {
                propObj.addProperty("description", prop.getDescription());
            }
            if (prop.getChildren() != null && !prop.getChildren().isEmpty()) {
                if ("array".equals(prop.getType())) {
                    // Array items
                    com.google.gson.JsonObject items = new com.google.gson.JsonObject();
                    YApiInterfaceInfo.YApiProperty first = prop.getChildren().get(0);
                    items.addProperty("type", first.getType());
                    if (first.getDescription() != null && !first.getDescription().isEmpty()) {
                        items.addProperty("description", first.getDescription());
                    }
                    if (first.getChildren() != null && !first.getChildren().isEmpty()) {
                        items.add("properties", propertiesToJsonObj(first.getChildren()));
                    }
                    propObj.add("items", items);
                } else {
                    propObj.add("properties", propertiesToJsonObj(prop.getChildren()));
                }
            }
            if (prop.getRequired() != null && !prop.getRequired().isEmpty()) {
                com.google.gson.JsonArray req = new com.google.gson.JsonArray();
                for (String r : prop.getRequired()) req.add(r);
                propObj.add("required", req);
            }
            obj.add(prop.getName(), propObj);
        }
        return obj;
    }
}
