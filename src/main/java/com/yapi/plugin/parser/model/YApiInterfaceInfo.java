package com.yapi.plugin.parser.model;

import java.util.ArrayList;
import java.util.List;

public class YApiInterfaceInfo {
    private String title;
    private String path;
    private String method;
    private String catId;
    private List<YApiHeaderParam> reqHeaders = new ArrayList<>();
    private List<YApiReqQueryParam> reqQuery = new ArrayList<>();
    private List<YApiReqBodyParam> reqBodyForm = new ArrayList<>();
    private String reqBodyType = "json";
    private String reqBodyOther;
    private YApiReqBodyParam resBody;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getCatId() { return catId; }
    public void setCatId(String catId) { this.catId = catId; }

    public List<YApiHeaderParam> getReqHeaders() { return reqHeaders; }
    public void setReqHeaders(List<YApiHeaderParam> reqHeaders) { this.reqHeaders = reqHeaders; }

    public List<YApiReqQueryParam> getReqQuery() { return reqQuery; }
    public void setReqQuery(List<YApiReqQueryParam> reqQuery) { this.reqQuery = reqQuery; }

    public List<YApiReqBodyParam> getReqBodyForm() { return reqBodyForm; }
    public void setReqBodyForm(List<YApiReqBodyParam> reqBodyForm) { this.reqBodyForm = reqBodyForm; }

    public String getReqBodyType() { return reqBodyType; }
    public void setReqBodyType(String reqBodyType) { this.reqBodyType = reqBodyType; }

    public String getReqBodyOther() { return reqBodyOther; }
    public void setReqBodyOther(String reqBodyOther) { this.reqBodyOther = reqBodyOther; }

    public YApiReqBodyParam getResBody() { return resBody; }
    public void setResBody(YApiReqBodyParam resBody) { this.resBody = resBody; }

    // ---- YApi JSON schema types ----

    public static class YApiHeaderParam {
        private String name;
        private String value;
        private String desc;
        private boolean required;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getDesc() { return desc; }
        public void setDesc(String desc) { this.desc = desc; }
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
    }

    public static class YApiReqQueryParam {
        private String name;
        private String desc;
        private String example;
        private boolean required;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDesc() { return desc; }
        public void setDesc(String desc) { this.desc = desc; }
        public String getExample() { return example; }
        public void setExample(String example) { this.example = example; }
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
    }

    public static class YApiReqBodyParam {
        private String type;
        private String description;
        private List<YApiProperty> properties;
        private List<String> required;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<YApiProperty> getProperties() { return properties; }
        public void setProperties(List<YApiProperty> properties) { this.properties = properties; }
        public List<String> getRequired() { return required; }
        public void setRequired(List<String> required) { this.required = required; }
    }

    public static class YApiProperty {
        private String type;
        private String description;
        private String name;
        private List<YApiProperty> children;
        private List<String> required;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<YApiProperty> getChildren() { return children; }
        public void setChildren(List<YApiProperty> children) { this.children = children; }
        public List<String> getRequired() { return required; }
        public void setRequired(List<String> required) { this.required = required; }
    }
}
