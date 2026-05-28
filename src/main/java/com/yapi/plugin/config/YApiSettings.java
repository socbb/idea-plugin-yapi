package com.yapi.plugin.config;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(
    name = "YApiSettings",
    storages = {@Storage("yapi-settings.xml")}
)
public class YApiSettings implements PersistentStateComponent<YApiSettings> {

    public List<YApiServerConfig> servers = new ArrayList<>();

    public static YApiSettings getInstance(@NotNull Project project) {
        return project.getService(YApiSettings.class);
    }

    @Nullable
    @Override
    public YApiSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull YApiSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    @Nullable
    public YApiServerConfig getDefaultServer() {
        return servers.stream().filter(YApiServerConfig::isDefault).findFirst()
                .orElse(servers.isEmpty() ? null : servers.get(0));
    }

    public void setDefaultServer(@NotNull YApiServerConfig config) {
        for (YApiServerConfig s : servers) {
            s.setDefault(s.equals(config));
        }
    }
}
