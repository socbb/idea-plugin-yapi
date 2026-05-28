package com.yapi.plugin.config;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class YApiSettingsConfigurable implements Configurable {

    private final Project project;
    private YApiSettingsPanel panel;

    public YApiSettingsConfigurable(Project project) {
        this.project = project;
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "YApi Doc Generator";
    }

    @Override
    public @Nullable JComponent createComponent() {
        panel = new YApiSettingsPanel();
        YApiSettings settings = YApiSettings.getInstance(project);
        panel.setServers(settings.servers);
        return panel;
    }

    @Override
    public boolean isModified() {
        if (panel == null) return false;
        YApiSettings settings = YApiSettings.getInstance(project);
        return !panel.getServers().equals(settings.servers);
    }

    @Override
    public void apply() {
        if (panel == null) return;
        YApiSettings settings = YApiSettings.getInstance(project);
        settings.servers = panel.getServers();
    }

    @Override
    public void reset() {
        if (panel == null) return;
        YApiSettings settings = YApiSettings.getInstance(project);
        panel.setServers(settings.servers);
    }
}
