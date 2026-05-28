package com.yapi.plugin.parser;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.yapi.plugin.parser.model.YApiInterfaceInfo;

import java.util.*;

public class DtoParser {

    private static final int MAX_DEPTH = 10;
    private static final Set<String> PRIMITIVE_TYPES = Set.of(
            "int", "long", "short", "byte", "float", "double", "boolean", "char",
            "java.lang.String", "java.lang.Integer", "java.lang.Long", "java.lang.Short",
            "java.lang.Byte", "java.lang.Float", "java.lang.Double", "java.lang.Boolean",
            "java.lang.Character", "java.math.BigDecimal", "java.math.BigInteger",
            "java.util.Date", "java.time.LocalDate", "java.time.LocalDateTime",
            "java.time.LocalTime", "java.time.Instant"
    );

    // Types we treat as scalars (no further parsing)
    private static final Set<String> SCALAR_TYPES = Set.of(
            "java.lang.Object", "java.lang.Number", "java.io.Serializable"
    );

    public YApiInterfaceInfo.YApiReqBodyParam parseDto(PsiType type) {
        return parseDto(type, 0, new HashSet<>());
    }

    private YApiInterfaceInfo.YApiReqBodyParam parseDto(PsiType type, int depth, Set<String> visited) {
        if (type == null || depth >= MAX_DEPTH) {
            YApiInterfaceInfo.YApiReqBodyParam body = new YApiInterfaceInfo.YApiReqBodyParam();
            body.setType("object");
            return body;
        }

        if (type instanceof PsiArrayType) {
            return parseArrayType((PsiArrayType) type, depth, visited);
        }

        PsiType deepType = PsiUtil.extractIterableTypeParameter(type, false);
        if (deepType != null) {
            return parseCollectionType(deepType, depth, visited);
        }

        if (type instanceof PsiClassType) {
            PsiClass psiClass = ((PsiClassType) type).resolve();
            if (psiClass != null) {
                return parseClassFields(psiClass, type, depth, visited);
            }
        }

        // Fallback: treat as primitive/string etc
        YApiInterfaceInfo.YApiReqBodyParam body = new YApiInterfaceInfo.YApiReqBodyParam();
        body.setType(mapJavaTypeToYApi(type.getCanonicalText()));
        return body;
    }

    private YApiInterfaceInfo.YApiReqBodyParam parseArrayType(PsiArrayType arrayType, int depth, Set<String> visited) {
        YApiInterfaceInfo.YApiReqBodyParam body = new YApiInterfaceInfo.YApiReqBodyParam();
        body.setType("array");
        PsiType componentType = arrayType.getComponentType();
        YApiInterfaceInfo.YApiProperty item = typeToProperty(componentType, depth + 1, visited);
        body.setProperties(Collections.singletonList(item));
        return body;
    }

    private YApiInterfaceInfo.YApiReqBodyParam parseCollectionType(PsiType elementType, int depth, Set<String> visited) {
        YApiInterfaceInfo.YApiReqBodyParam body = new YApiInterfaceInfo.YApiReqBodyParam();
        body.setType("array");
        YApiInterfaceInfo.YApiProperty item = typeToProperty(elementType, depth + 1, visited);
        if (item != null) {
            body.setProperties(Collections.singletonList(item));
        }
        return body;
    }

    private YApiInterfaceInfo.YApiReqBodyParam parseClassFields(PsiClass psiClass, PsiType type, int depth, Set<String> visited) {
        YApiInterfaceInfo.YApiReqBodyParam body = new YApiInterfaceInfo.YApiReqBodyParam();
        body.setType("object");

        String className = psiClass.getQualifiedName();
        if (className == null || PRIMITIVE_TYPES.contains(className) || SCALAR_TYPES.contains(className)) {
            body.setType(mapJavaTypeToYApi(className));
            return body;
        }

        // Enum handling
        if (psiClass.isEnum()) {
            body.setType("string");
            StringBuilder desc = new StringBuilder("Enum values: ");
            for (PsiField field : psiClass.getFields()) {
                if (field instanceof PsiEnumConstant) {
                    desc.append(field.getName()).append(", ");
                }
            }
            body.setDescription(desc.toString());
            return body;
        }

        if (visited.contains(className)) {
            body.setDescription("(circular reference)");
            return body;
        }
        visited.add(className);

        // 获取泛型类型参数替换器，将 T 替换为实际类型参数
        PsiSubstitutor substitutor = type instanceof PsiClassType
                ? ((PsiClassType) type).resolveGenerics().getSubstitutor()
                : PsiSubstitutor.EMPTY;

        List<YApiInterfaceInfo.YApiProperty> properties = new ArrayList<>();
        List<String> requiredList = new ArrayList<>();

        for (PsiField field : getAllFields(psiClass)) {
            if (shouldSkipField(field)) continue;

            PsiType resolvedType = substitutor.substitute(field.getType());
            YApiInterfaceInfo.YApiProperty prop = fieldToProperty(field, resolvedType, depth + 1, visited);
            if (prop != null) {
                properties.add(prop);
                if (isFieldRequired(field)) {
                    requiredList.add(field.getName());
                }
            }
        }

        body.setProperties(properties);
        if (!requiredList.isEmpty()) {
            body.setRequired(requiredList);
        }
        return body;
    }

    private YApiInterfaceInfo.YApiProperty typeToProperty(PsiType type, int depth, Set<String> visited) {
        if (type == null) return null;

        YApiInterfaceInfo.YApiProperty prop = new YApiInterfaceInfo.YApiProperty();
        String typeName = type.getCanonicalText();

        if (PRIMITIVE_TYPES.contains(typeName)) {
            prop.setType(mapJavaTypeToYApi(typeName));
            return prop;
        }

        if (type instanceof PsiArrayType) {
            prop.setType("array");
            PsiType componentType = ((PsiArrayType) type).getComponentType();
            YApiInterfaceInfo.YApiProperty child = typeToProperty(componentType, depth + 1, visited);
            if (child != null) {
                prop.setChildren(Collections.singletonList(child));
            }
            return prop;
        }

        PsiType elementType = PsiUtil.extractIterableTypeParameter(type, false);
        if (elementType != null) {
            prop.setType("array");
            YApiInterfaceInfo.YApiProperty child = typeToProperty(elementType, depth + 1, visited);
            if (child != null) {
                prop.setChildren(Collections.singletonList(child));
            }
            return prop;
        }

        if (type instanceof PsiClassType) {
            PsiClass psiClass = ((PsiClassType) type).resolve();
            if (psiClass != null) {
                return classToProperty(psiClass, (PsiClassType) type, depth, visited);
            }
        }

        prop.setType(mapJavaTypeToYApi(typeName));
        return prop;
    }

    private YApiInterfaceInfo.YApiProperty classToProperty(PsiClass psiClass, PsiClassType classType, int depth, Set<String> visited) {
        YApiInterfaceInfo.YApiProperty prop = new YApiInterfaceInfo.YApiProperty();
        String className = psiClass.getQualifiedName();

        if (className != null && PRIMITIVE_TYPES.contains(className)) {
            prop.setType(mapJavaTypeToYApi(className));
            return prop;
        }

        if (psiClass.isEnum()) {
            prop.setType("string");
            StringBuilder desc = new StringBuilder("Enum: ");
            for (PsiField field : psiClass.getFields()) {
                if (field instanceof PsiEnumConstant) {
                    desc.append(field.getName()).append(", ");
                }
            }
            prop.setDescription(desc.toString());
            return prop;
        }

        if (depth >= MAX_DEPTH || (className != null && visited.contains(className))) {
            prop.setType("object");
            return prop;
        }

        prop.setType("object");
        prop.setChildren(new ArrayList<>());

        if (className != null) visited.add(className);

        // 获取泛型类型参数替换器，将 T 替换为实际类型参数
        PsiSubstitutor substitutor = classType.resolveGenerics().getSubstitutor();

        List<YApiInterfaceInfo.YApiProperty> children = new ArrayList<>();
        List<String> requiredList = new ArrayList<>();

        for (PsiField field : getAllFields(psiClass)) {
            if (shouldSkipField(field)) continue;
            PsiType resolvedType = substitutor.substitute(field.getType());
            YApiInterfaceInfo.YApiProperty child = fieldToProperty(field, resolvedType, depth + 1, visited);
            if (child != null) {
                children.add(child);
                if (isFieldRequired(field)) {
                    requiredList.add(field.getName());
                }
            }
        }

        // 从visited中移除当前类, 仅保留祖先链追踪, 避免兄弟节点误判为循环引用
        if (className != null) visited.remove(className);

        prop.setChildren(children);
        if (!requiredList.isEmpty()) {
            prop.setRequired(requiredList);
        }
        return prop;
    }

    private YApiInterfaceInfo.YApiProperty fieldToProperty(PsiField field, PsiType resolvedType, int depth, Set<String> visited) {
        YApiInterfaceInfo.YApiProperty prop = typeToProperty(resolvedType, depth, visited);
        if (prop == null) return null;

        prop.setName(field.getName());
        prop.setDescription(getFieldDescription(field));
        return prop;
    }

    private List<PsiField> getAllFields(PsiClass psiClass) {
        List<PsiField> fields = new ArrayList<>();
        collectFields(psiClass, fields, new HashSet<>());
        return fields;
    }

    private void collectFields(PsiClass psiClass, List<PsiField> fields, Set<String> seen) {
        if (psiClass == null || "java.lang.Object".equals(psiClass.getQualifiedName())) return;
        for (PsiField field : psiClass.getFields()) {
            if (!seen.contains(field.getName())) {
                seen.add(field.getName());
                fields.add(field);
            }
        }
        PsiClass superClass = psiClass.getSuperClass();
        if (superClass != null) {
            collectFields(superClass, fields, seen);
        }
    }

    private boolean shouldSkipField(PsiField field) {
        if (field.hasModifierProperty(PsiModifier.STATIC)) return true;
        if (field.hasModifierProperty(PsiModifier.TRANSIENT)) return true;
        if (hasAnnotation(field, "com.fasterxml.jackson.annotation.JsonIgnore")) return true;
        if (hasAnnotation(field, "com.fasterxml.jackson.annotation.JsonIgnoreProperties")) return true;
        // Swagger @ApiModelProperty(hidden = true)
        if (isAnnotationAttributeTrue(field, "io.swagger.annotations.ApiModelProperty", "hidden")) return true;
        return false;
    }

    private boolean isFieldRequired(PsiField field) {
        if (hasAnnotation(field, "javax.validation.constraints.NotNull")
                || hasAnnotation(field, "jakarta.validation.constraints.NotNull")
                || hasAnnotation(field, "org.springframework.lang.NonNull")) {
            return true;
        }
        if (hasAnnotation(field, "javax.validation.constraints.NotBlank")
                || hasAnnotation(field, "jakarta.validation.constraints.NotBlank")
                || hasAnnotation(field, "javax.validation.constraints.NotEmpty")
                || hasAnnotation(field, "jakarta.validation.constraints.NotEmpty")) {
            return true;
        }
        // Swagger @ApiModelProperty(required = true)
        if (isAnnotationAttributeTrue(field, "io.swagger.annotations.ApiModelProperty", "required")) {
            return true;
        }
        // Swagger 3 @Schema(required = true)
        if (isAnnotationAttributeTrue(field, "io.swagger.v3.oas.annotations.media.Schema", "required")) {
            return true;
        }
        return false;
    }

    private boolean hasAnnotation(PsiField field, String qualifiedName) {
        PsiAnnotation[] annotations = field.getAnnotations();
        for (PsiAnnotation annotation : annotations) {
            if (qualifiedName.equals(annotation.getQualifiedName())) return true;
        }
        return false;
    }

    private boolean isAnnotationAttributeTrue(PsiField field, String annotationName, String attrName) {
        PsiAnnotation annotation = field.getAnnotation(annotationName);
        if (annotation == null) return false;
        PsiAnnotationMemberValue value = annotation.findAttributeValue(attrName);
        if (value == null) return false;
        return "true".equals(value.getText());
    }

    private String getFieldDescription(PsiField field) {
        // Try to extract JavaDoc or @ApiModelProperty annotation
        PsiAnnotation apiModel = field.getAnnotation("io.swagger.annotations.ApiModelProperty");
        if (apiModel != null) {
            PsiAnnotationMemberValue value = apiModel.findAttributeValue("value");
            if (value != null) {
                String text = value.getText();
                // Remove surrounding quotes
                if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
                    return text.substring(1, text.length() - 1);
                }
                return text;
            }
        }

        // Check Swagger 3 @Schema annotation
        PsiAnnotation schema = field.getAnnotation("io.swagger.v3.oas.annotations.media.Schema");
        if (schema != null) {
            PsiAnnotationMemberValue value = schema.findAttributeValue("description");
            if (value != null) {
                String text = value.getText();
                if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
                    return text.substring(1, text.length() - 1);
                }
                return text;
            }
        }

        return "";
    }

    private String mapJavaTypeToYApi(String javaType) {
        if (javaType == null) return "string";
        switch (javaType) {
            case "int": case "long": case "short": case "byte":
            case "java.lang.Integer": case "java.lang.Long":
            case "java.lang.Short": case "java.lang.Byte":
            case "java.math.BigInteger":
                return "integer";
            case "float": case "double":
            case "java.lang.Float": case "java.lang.Double":
            case "java.math.BigDecimal":
                return "number";
            case "boolean": case "java.lang.Boolean":
                return "boolean";
            case "char": case "java.lang.Character":
            case "java.lang.String":
                return "string";
            case "java.util.Date": case "java.time.LocalDate":
            case "java.time.LocalDateTime": case "java.time.Instant":
            case "java.time.LocalTime":
                return "string";
            default:
                return "object";
        }
    }
}
