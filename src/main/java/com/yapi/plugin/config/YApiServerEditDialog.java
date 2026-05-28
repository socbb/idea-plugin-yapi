package com.yapi.plugin.config;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class YApiServerEditDialog extends DialogWrapper {
    private final JBTextField nameField = new JBTextField();
    private final JBTextField urlField = new JBTextField();
    private final JBTextField tokenField = new JBTextField();
    private final JBTextField projectIdField = new JBTextField();

    private final YApiServerConfig serverConfig;

    public YApiServerEditDialog(@Nullable YApiServerConfig existing) {
        super(true);
        this.serverConfig = existing != null ? existing : new YApiServerConfig();
        setTitle(serverConfig.getName() == null || serverConfig.getName().isEmpty()
                ? "Add YApi Server" : "Edit YApi Server");
        initFields();
        init();
    }

    private void initFields() {
        if (serverConfig.getName() != null) nameField.setText(serverConfig.getName());
        if (serverConfig.getUrl() != null) urlField.setText(serverConfig.getUrl());
        if (serverConfig.getToken() != null) tokenField.setText(serverConfig.getToken());
        if (serverConfig.getProjectId() != null) projectIdField.setText(serverConfig.getProjectId());
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        panel.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(new JLabel("URL:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(urlField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        panel.add(new JLabel("Token:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(tokenField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        panel.add(new JLabel("Project ID:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(projectIdField, gbc);

        panel.setPreferredSize(new Dimension(400, 180));
        return panel;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (nameField.getText().trim().isEmpty()) {
            return new ValidationInfo("Name is required", nameField);
        }
        if (urlField.getText().trim().isEmpty()) {
            return new ValidationInfo("URL is required", urlField);
        }
        if (tokenField.getText().trim().isEmpty()) {
            return new ValidationInfo("Token is required", tokenField);
        }
        if (projectIdField.getText().trim().isEmpty()) {
            return new ValidationInfo("Project ID is required", projectIdField);
        }
        return null;
    }

    @NotNull
    public YApiServerConfig getServerConfig() {
        serverConfig.setName(nameField.getText().trim());
        serverConfig.setUrl(urlField.getText().trim());
        serverConfig.setToken(tokenField.getText().trim());
        serverConfig.setProjectId(projectIdField.getText().trim());
        return serverConfig;
    }
}
