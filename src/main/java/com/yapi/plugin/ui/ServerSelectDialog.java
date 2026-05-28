package com.yapi.plugin.ui;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.JBUI;
import com.yapi.plugin.config.YApiServerConfig;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ServerSelectDialog extends DialogWrapper {

    private final ComboBox<YApiServerConfig> comboBox;

    public ServerSelectDialog(List<YApiServerConfig> servers) {
        super(true);
        setTitle("Select YApi Server");

        comboBox = new ComboBox<>();
        for (YApiServerConfig server : servers) {
            comboBox.addItem(server);
        }

        // Pre-select default
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i).isDefault()) {
                comboBox.setSelectedIndex(i);
                break;
            }
        }

        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(10));
        panel.add(new JLabel("Choose target YApi server:"), BorderLayout.NORTH);
        panel.add(comboBox, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(400, 70));
        return panel;
    }

    @Nullable
    public YApiServerConfig getSelectedServer() {
        return (YApiServerConfig) comboBox.getSelectedItem();
    }
}
