package com.yapi.plugin.config;

import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class YApiSettingsPanel extends JPanel {
    private final ServerTableModel tableModel;
    private final JBTable table;

    public YApiSettingsPanel() {
        super(new BorderLayout());
        setBorder(JBUI.Borders.empty(10));

        tableModel = new ServerTableModel();
        table = new JBTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(120);
        table.getColumnModel().getColumn(1).setPreferredWidth(250);
        table.getColumnModel().getColumn(2).setPreferredWidth(200);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        table.getColumnModel().getColumn(4).setPreferredWidth(50);

        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(table)
                .setAddAction(anActionButton -> addServer())
                .setRemoveAction(anActionButton -> removeServer())
                .setEditAction(anActionButton -> editServer())
                .disableUpDownActions();

        JLabel hint = new JLabel(
                "Configure YApi servers. The token is used for API authentication. " +
                "Check 'Default' to pre-select a server when generating docs.");
        hint.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
        hint.setBorder(JBUI.Borders.empty(0, 0, 10, 0));

        add(hint, BorderLayout.NORTH);
        add(decorator.createPanel(), BorderLayout.CENTER);
    }

    public void setServers(List<YApiServerConfig> servers) {
        tableModel.setServers(servers);
    }

    public List<YApiServerConfig> getServers() {
        return tableModel.getServers();
    }

    private void addServer() {
        YApiServerEditDialog dialog = new YApiServerEditDialog(null);
        if (dialog.showAndGet()) {
            tableModel.addServer(dialog.getServerConfig());
        }
    }

    private void removeServer() {
        int row = table.getSelectedRow();
        if (row >= 0) {
            tableModel.removeServer(row);
        }
    }

    private void editServer() {
        int row = table.getSelectedRow();
        if (row >= 0) {
            YApiServerConfig server = tableModel.getServers().get(row);
            YApiServerEditDialog dialog = new YApiServerEditDialog(server);
            if (dialog.showAndGet()) {
                tableModel.updateServer(row, dialog.getServerConfig());
            }
        }
    }

    private static class ServerTableModel extends AbstractTableModel {
        private final String[] columns = {"Name", "URL", "Token", "Project ID", "Default"};
        private List<YApiServerConfig> servers = new ArrayList<>();

        public void setServers(List<YApiServerConfig> servers) {
            this.servers = new ArrayList<>(servers);
            fireTableDataChanged();
        }

        public List<YApiServerConfig> getServers() {
            // Return defensive copy
            List<YApiServerConfig> copy = new ArrayList<>();
            for (YApiServerConfig s : servers) {
                YApiServerConfig c = new YApiServerConfig();
                c.setName(s.getName());
                c.setUrl(s.getUrl());
                c.setToken(s.getToken());
                c.setProjectId(s.getProjectId());
                c.setDefault(s.isDefault());
                copy.add(c);
            }
            return copy;
        }

        public void addServer(YApiServerConfig server) {
            servers.add(server);
            fireTableRowsInserted(servers.size() - 1, servers.size() - 1);
        }

        public void removeServer(int index) {
            servers.remove(index);
            fireTableRowsDeleted(index, index);
        }

        public void updateServer(int index, YApiServerConfig server) {
            servers.set(index, server);
            fireTableRowsUpdated(index, index);
        }

        @Override
        public int getRowCount() {
            return servers.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 4 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 4;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            YApiServerConfig server = servers.get(rowIndex);
            switch (columnIndex) {
                case 0: return server.getName();
                case 1: return server.getUrl();
                case 2: return maskToken(server.getToken());
                case 3: return server.getProjectId();
                case 4: return server.isDefault();
                default: return "";
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 4) {
                boolean checked = (Boolean) aValue;
                if (checked) {
                    // Uncheck all others
                    for (int i = 0; i < servers.size(); i++) {
                        servers.get(i).setDefault(i == rowIndex);
                    }
                    fireTableDataChanged();
                } else {
                    servers.get(rowIndex).setDefault(false);
                    fireTableCellUpdated(rowIndex, columnIndex);
                }
            }
        }

        private String maskToken(String token) {
            if (token == null || token.length() <= 8) return "****";
            return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
        }
    }
}
