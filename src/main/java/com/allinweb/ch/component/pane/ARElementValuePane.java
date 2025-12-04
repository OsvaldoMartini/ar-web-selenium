package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.model.VariableUserDTO;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.Gson;
import java.awt.*;
import java.sql.Connection;
import java.sql.SQLException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARElementValuePane extends ARPane {

    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final PerformMessage performMessage = PerformMessage.getInstance();
    //    private static final ARNewCommandPane arNewCommandPane = ARNewCommandPane.getInstance();
    protected static volatile ARElementValuePane instance;
    private static final WebSocketSessionManager webSocketSessionManager = WebSocketSessionManager.getInstance();

    private final Gson gson = new Gson();

    // UI components
    JTextField idField;
    JTextField parentField;
    JTextField nameField;
    JTextField valueField;
    JTextField usedVarsField;
    JCheckBox stringCheckBox;
    JCheckBox numericCheckBox;
    JLabel numberFormatLabel;
    JLabel delimeterCSVLabel;
    JComboBox<FormatOption> comboBoxLocalFormat;
    JComboBox<FormatOption> comboBoxCSVColumns;
    JButton insertButton;
    JButton updateButton;
    JButton deleteButton;

    private Connection conn = null;
    private JTable tableView;
    private DefaultTableModel tableModel;
    private SplitDTO splitDTO;
    private int varId;
    private String varValue;
    private int instructionId;
    private String instructionName;
    private String varName;
    private String instructionType;
    private JPanel mainPane;

    private ARElementValuePane() {
        initUIComponents();
        initUIBehaviour();
    }

    public static ARElementValuePane getInstance() {
        if (instance == null) {
            synchronized (ARElementValuePane.class) {
                if (instance == null) {
                    instance = new ARElementValuePane();
                }
            }
        }
        return instance;
    }

    public void initialize(
            SplitDTO splitDTO,
            int varId,
            String varValue,
            int instructionId,
            String instructionName,
            String varName,
            String instructionType) {

        this.splitDTO = splitDTO;
        this.varId = varId;
        this.varValue = varValue;
        this.instructionId = instructionId;
        this.instructionName = instructionName;
        this.varName = varName;
        this.instructionType = instructionType;

        String varTable = splitDTO.getSessionId().equals("componentTasks") ? "component_variable" : "variable";
        int whereId =
                splitDTO.getSessionId().equals("componentTasks") ? splitDTO.getHomeBankingId() : splitDTO.getBotJobId();

        String instrTable = varTable.equals("variable") ? "instruction" : "component_instruction";

        var instructionLoad = performLists.getInstructionById(instrTable, whereId, instructionId);

        if (instructionLoad != null) {
            ErrorMessage errorMessage = performDataBase.loadAllVariablesByCriteria(
                    varTable, whereId, instructionLoad.getId(), instructionLoad.getName());
            if (errorMessage != null) {
                performMessage.errorMessageOperationFailed(errorMessage);
            }
        }

        if (tableModel != null) {
            reloadTableData();
        }

        // Pre-fill fields
        if (idField != null) {
            idField.setText(String.valueOf(varId));
            parentField.setText(instructionName);
            nameField.setText(varName);
        }

        if (valueField != null) {
            if ("GET".equalsIgnoreCase(instructionType)) {
                valueField.setBackground(Color.LIGHT_GRAY);
                valueField.setEditable(false);
                valueField.setText("");
            } else {
                valueField.setBackground(Color.YELLOW);
                valueField.setEditable(true);
                valueField.setText(varValue);
            }
        }

        // Clear selection
        tableView.clearSelection();
        updateButton.setEnabled(false);
        deleteButton.setEnabled(false);
    }

    @Override
    public JPanel getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        mainPane = new JPanel(new BorderLayout(10, 10));
        mainPane.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel gridPane = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Labels
        JLabel idLabel = new JLabel("ID:");
        JLabel parentLabel = new JLabel("Parent:");
        JLabel nameLabel = new JLabel("Var Name:");
        JLabel typeLabel = new JLabel("Type:");
        JLabel valueLabel = new JLabel("Value:");
        JLabel jobsLabel = new JLabel("Used Variables:");
        numberFormatLabel = new JLabel("Currency Format:");
        delimeterCSVLabel = new JLabel("CSV Delimiter:");

        // Fields
        idField = new JTextField(5);
        idField.setEditable(false);

        parentField = new JTextField(15);
        parentField.setEditable(false);

        nameField = new JTextField(15);
        valueField = new JTextField(15);

        usedVarsField = new JTextField(5);
        usedVarsField.setEditable(false);

        // Checkboxes
        stringCheckBox = new JCheckBox("$String");
        numericCheckBox = new JCheckBox("#Numeric");

        // ComboBoxes (with simple FormatOption wrapper)
        comboBoxLocalFormat = new JComboBox<>(new FormatOption[] {
            new FormatOption("American (9,999.99)", "US"), new FormatOption("European (9.999,99)", "EU")
        });
        comboBoxLocalFormat.setEnabled(false);

        comboBoxCSVColumns = new JComboBox<>(
                new FormatOption[] {new FormatOption("Comma: \",\"", ","), new FormatOption("Pipe \"|\"", "|")});

        // Buttons
        insertButton = new JButton("Insert");
        updateButton = new JButton("Update");
        updateButton.setEnabled(false);
        deleteButton = new JButton("Delete");
        deleteButton.setEnabled(false);

        int row = 0;

        gbc.gridx = 0;
        gbc.gridy = row;
        gridPane.add(idLabel, gbc);
        gbc.gridx = 1;
        gridPane.add(idField, gbc);

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gridPane.add(parentLabel, gbc);
        gbc.gridx = 1;
        gridPane.add(parentField, gbc);

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gridPane.add(nameLabel, gbc);
        gbc.gridx = 1;
        gridPane.add(nameField, gbc);

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gridPane.add(valueLabel, gbc);
        gbc.gridx = 1;
        gridPane.add(valueField, gbc);

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gridPane.add(typeLabel, gbc);
        gbc.gridx = 1;
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        typePanel.add(stringCheckBox);
        typePanel.add(numericCheckBox);
        gridPane.add(typePanel, gbc);

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gridPane.add(numberFormatLabel, gbc);
        gbc.gridx = 1;
        gridPane.add(comboBoxLocalFormat, gbc);

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gridPane.add(delimeterCSVLabel, gbc);
        gbc.gridx = 1;
        gridPane.add(comboBoxCSVColumns, gbc);

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gridPane.add(jobsLabel, gbc);
        gbc.gridx = 1;
        gridPane.add(usedVarsField, gbc);

        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(insertButton);
        buttonPanel.add(updateButton);
        buttonPanel.add(deleteButton);
        gridPane.add(buttonPanel, gbc);

        // Table + model
        tableModel =
                new DefaultTableModel(new Object[] {"ID", "Type", "Name", "Value", "Local Format", "CSV Delimiter"}, 0);
        tableView = new JTable(tableModel);
        tableView.setFillsViewportHeight(true);

        JScrollPane tableScroll = new JScrollPane(tableView);

        mainPane.add(gridPane, BorderLayout.NORTH);
        mainPane.add(tableScroll, BorderLayout.CENTER);
    }

    private void reloadTableData() {
        tableModel.setRowCount(0);
        for (VariableUserDTO dto : performLists.getListVariablesUser()) {
            tableModel.addRow(new Object[] {
                dto.getId(), dto.getType(), dto.getName(), dto.getValue(), dto.getLocalFormat(), dto.getDelimiter()
            });
        }
        usedVarsField.setText(String.valueOf(performLists.getListVariablesUser().size()));
    }

    @Override
    public void initUIBehaviour() {
        // INSERT
        insertButton.addActionListener(e -> {
            // TODO: implement DB insert logic equivalent to your original JavaFX code.
            //       After successful insert, reload data:
            // ErrorMessage errorMessage = performDataBase.insertVariableUser(...);
            // if (errorMessage != null) { performMessage.errorMessageOperationFailed(errorMessage); return; }
            // performDataBase.loadAllVariablesByCriteria(...);
            // reloadTableData();
            log.info("Insert clicked – implement DB insert here.");
        });

        // UPDATE
        updateButton.addActionListener(e -> {
            if (tableView.getSelectedRow() < 0) {
                return;
            }

            int id = Integer.parseInt(idField.getText().trim());
            String name = nameField.getText().trim();
            String value = valueField.getText().trim();
            String type = stringCheckBox.isSelected() ? "$String" : "#Numeric";

            FormatOption localFmt = (FormatOption) comboBoxLocalFormat.getSelectedItem();
            String localFormat = localFmt != null ? localFmt.getValue() : null;

            FormatOption csvFmt = (FormatOption) comboBoxCSVColumns.getSelectedItem();
            String delimiter = csvFmt != null ? csvFmt.getValue() : null;

            log.info(
                    "Update variable id={}, name={}, value={}, type={}, format={}, delim={}",
                    id,
                    name,
                    value,
                    type,
                    localFormat,
                    delimiter);

            // TODO: call your original DB update method here.
            // ErrorMessage errorMessage = performDataBase.updateVariableUser(...);
            // if (errorMessage != null) { performMessage.errorMessageOperationFailed(errorMessage); return; }
            // performDataBase.loadAllVariablesByCriteria(...);
            // reloadTableData();

        });

        // DELETE
        deleteButton.addActionListener(e -> {
            if (tableView.getSelectedRow() < 0) {
                return;
            }

            int id = Integer.parseInt(idField.getText().trim());
            log.info("Delete variable id={}", id);

            // TODO: call your original DB delete method here.
            // ErrorMessage errorMessage = performDataBase.deleteVariableUser(id, ...);
            // if (errorMessage != null) { performMessage.errorMessageOperationFailed(errorMessage); return; }
            // performDataBase.loadAllVariablesByCriteria(...);
            // reloadTableData();

            clearData();
        });

        // Numeric/String checkbox behavior
        stringCheckBox.addActionListener(e -> {
            if (stringCheckBox.isSelected()) {
                numericCheckBox.setSelected(false);
                comboBoxLocalFormat.setEnabled(false);
            }
        });
        numericCheckBox.addActionListener(e -> {
            if (numericCheckBox.isSelected()) {
                stringCheckBox.setSelected(false);
                comboBoxLocalFormat.setEnabled(true);
            } else {
                comboBoxLocalFormat.setEnabled(false);
            }
        });

        // Table selection listener
        tableView.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            int selectedRow = tableView.getSelectedRow();
            if (selectedRow >= 0
                    && selectedRow < performLists.getListVariablesUser().size()) {
                VariableUserDTO dto = performLists.getListVariablesUser().get(selectedRow);
                fillFields(dto);
                updateButton.setEnabled(true);
                deleteButton.setEnabled(true);
            } else {
                clearData();
            }
        });
    }

    private void clearData() {
        idField.setText("");
        parentField.setText("");
        nameField.setText("");
        valueField.setText("");
        usedVarsField.setText("");
        stringCheckBox.setSelected(false);
        numericCheckBox.setSelected(false);
        comboBoxLocalFormat.setSelectedIndex(-1);
        comboBoxLocalFormat.setEnabled(false);
        comboBoxCSVColumns.setSelectedIndex(-1);
        deleteButton.setEnabled(false);
        updateButton.setEnabled(false);
        tableView.clearSelection();
    }

    private void fillFields(VariableUserDTO dto) {
        idField.setText(String.valueOf(dto.getId()));
        parentField.setText("(" + dto.getParentId() + ") " + dto.getParentName());
        nameField.setText(dto.getName());
        valueField.setText("$EMPTY".equalsIgnoreCase(dto.getValue()) ? "" : dto.getValue());

        // Select local format option based on stored code
        selectFormatOption(comboBoxLocalFormat, dto.getLocalFormat());
        // Select CSV delimiter based on stored delimiter string
        selectFormatOption(comboBoxCSVColumns, dto.getDelimiter());

        stringCheckBox.setSelected("$String".equals(dto.getType()));
        numericCheckBox.setSelected("#Numeric".equals(dto.getType()));
        comboBoxLocalFormat.setEnabled(numericCheckBox.isSelected());
    }

    private void selectFormatOption(JComboBox<FormatOption> combo, String code) {
        if (code == null) {
            combo.setSelectedIndex(-1);
            return;
        }
        for (int i = 0; i < combo.getItemCount(); i++) {
            FormatOption opt = combo.getItemAt(i);
            if (code.equals(opt.getValue())) {
                combo.setSelectedIndex(i);
                return;
            }
        }
        combo.setSelectedIndex(-1);
    }

    @Override
    public void clearPane(JPanel panel) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                log.info(e.getMessage());
            }
        }
    }

    /**
     * Simple value/label wrapper for the combo boxes.
     */
    private static class FormatOption {
        private final String label;
        private final String value;

        FormatOption(String label, String value) {
            this.label = label;
            this.value = value;
        }

        String getLabel() {
            return label;
        }

        String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
