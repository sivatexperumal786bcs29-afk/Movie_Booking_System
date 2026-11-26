
package MovieBooking;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;

/** Small UI helpers shared by the admin screens. */
final class AdminUi {
    private AdminUi() {}

    /** Styled read-only table inside a scroll pane. */
    static class DataTable extends JScrollPane {
        final DefaultTableModel model;
        final JTable table;

        DataTable(String... cols) {
            model = new DefaultTableModel(cols, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            table = new JTable(model);
            table.setRowHeight(36);
            table.setFont(Theme.font(Font.PLAIN, 14));
            table.setForeground(Theme.NAVY);
            table.setShowVerticalLines(false);
            table.setShowHorizontalLines(false);
            table.setIntercellSpacing(new Dimension(0, 0));
            table.setSelectionBackground(new Color(0xFFE3E9));
            table.setSelectionForeground(Theme.NAVY);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

            DefaultTableCellRenderer body = new DefaultTableCellRenderer() {
                @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
                    super.getTableCellRendererComponent(t, v, sel, false, row, col);
                    setBorder(new EmptyBorder(0, 12, 0, 12));
                    if (!sel) setBackground(row % 2 == 0 ? Color.WHITE : new Color(0xF7F7FC));
                    String s = v == null ? "" : v.toString();
                    setForeground(Theme.NAVY);
                    if (s.equals("CONFIRMED") || s.equals("ACTIVE")) setForeground(new Color(0x1B8A37));
                    else if (s.equals("CANCELLED")) setForeground(Theme.RED);
                    setFont(s.equals("CONFIRMED") || s.equals("ACTIVE") || s.equals("CANCELLED")
                            ? Theme.font(Font.BOLD, 13) : Theme.font(Font.PLAIN, 14));
                    return this;
                }
            };
            table.setDefaultRenderer(Object.class, body);

            JTableHeader h = table.getTableHeader();
            h.setReorderingAllowed(false);
            h.setPreferredSize(new Dimension(100, 40));
            h.setDefaultRenderer(new DefaultTableCellRenderer() {
                @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
                    super.getTableCellRendererComponent(t, v, false, false, row, col);
                    setBackground(Theme.NAVY);
                    setForeground(Color.WHITE);
                    setFont(Theme.font(Font.BOLD, 13));
                    setBorder(new EmptyBorder(0, 12, 0, 12));
                    return this;
                }
            });

            setViewportView(table);
            getViewport().setBackground(Color.WHITE);
            setBorder(new LineBorder(Theme.LINE, 1, true));
        }

        void load(List<Object[]> rows) {
            model.setRowCount(0);
            for (Object[] r : rows) {
                Object[] x = new Object[r.length];
                for (int i = 0; i < r.length; i++) x[i] = r[i] == null ? "" : String.valueOf(r[i]);
                model.addRow(x);
            }
        }

        /** Text of a column in the selected row, or null when nothing is selected. */
        String sel(int col) {
            int r = table.getSelectedRow();
            return r < 0 ? null : String.valueOf(model.getValueAt(r, col));
        }

        void width(int col, int w) { table.getColumnModel().getColumn(col).setPreferredWidth(w); }
    }

    static JPanel header(String title, String sub, Color a, Color b) {
        Theme.Gradient g = new Theme.Gradient(a, b);
        g.setLayout(new BoxLayout(g, BoxLayout.Y_AXIS));
        g.setBorder(new EmptyBorder(22, 30, 22, 30));
        JLabel t = Theme.label(title, 30, Font.BOLD, Color.WHITE);
        JLabel s = Theme.label(sub, 14, Font.PLAIN, new Color(0xFFF0F4));
        g.add(t);
        g.add(s);
        return g;
    }

    /** 0 for the admin (sees every theatre); the theatre id for a theatre manager (sees only his own theatre). */
    static int scope() { return Dao.Session.isStaff() ? Dao.Session.theatreId : 0; }

    static JPanel buttons(JComponent... items) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        p.setOpaque(false);
        for (JComponent c : items) p.add(c);
        return p;
    }

    static Theme.Btn btn(String text, Color c) {
        Theme.Btn b = new Theme.Btn(text, c);
        b.setBorder(new EmptyBorder(9, 18, 9, 18));
        return b;
    }

    /** Page body: padded panel with buttons on top and content in the centre. */
    static JPanel body(JComponent top, JComponent center) {
        JPanel p = new JPanel(new BorderLayout(0, 14));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(22, 30, 26, 30));
        if (top != null) p.add(top, BorderLayout.NORTH);
        p.add(center, BorderLayout.CENTER);
        return p;
    }

    static JTextField tf(String text, int cols) {
        JTextField f = Theme.field(cols);
        f.setText(text == null ? "" : text);
        return f;
    }

    /**
     * Form dialog that stays open on problems (the typed values stay). When OK is pressed, onOk runs the checks and the save and returns
     * an error message or null when everything worked. The error is printed in red INSIDE the form (no pop-up),
     * the typed values stay, and the dialog only closes when the save worked or the user presses Cancel.
     * Returns true when saved, false when the user cancelled.
     */
    static boolean form(Component parent, String title, String[] labels, JComponent[] comps,
                        java.util.function.Supplier<String> onOk) {
        return form(parent, title, labels, comps, onOk, false);
    }

    /** Same as form(), but the error is printed in red inside the dialog (used only by Admin > Add movie). */
    static boolean formInline(Component parent, String title, String[] labels, JComponent[] comps,
                              java.util.function.Supplier<String> onOk) {
        return form(parent, title, labels, comps, onOk, true);
    }

    private static boolean form(Component parent, String title, String[] labels, JComponent[] comps,
                                java.util.function.Supplier<String> onOk, boolean inline) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog d = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(new EmptyBorder(20, 24, 8, 24));
        for (int i = 0; i < labels.length; i++) {
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridx = 0; gc.gridy = i; gc.anchor = GridBagConstraints.NORTHWEST; gc.insets = new Insets(6, 0, 6, 16);
            p.add(Theme.label(labels[i], 13, Font.BOLD, Theme.NAVY), gc);
            gc = new GridBagConstraints();
            gc.gridx = 1; gc.gridy = i; gc.weightx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.insets = new Insets(6, 0, 6, 0);
            p.add(comps[i], gc);
        }

        JLabel error = Theme.label(" ", 13, Font.BOLD, Theme.RED);
        error.setBorder(new EmptyBorder(6, 24, 4, 24));
        Theme.Btn ok = btn("OK", Theme.GREEN);
        Theme.Btn cancel = btn("Cancel", Theme.MUTED);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        bar.setBackground(Color.WHITE);
        bar.setBorder(new EmptyBorder(0, 14, 8, 14));
        bar.add(ok);
        bar.add(cancel);
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(Color.WHITE);
        south.add(error, BorderLayout.NORTH);
        south.add(bar, BorderLayout.SOUTH);

        boolean[] saved = {false};
        ok.addActionListener(e -> {
            String problem = onOk.get();
            if (problem == null) {
                saved[0] = true;
                d.dispose();
            } else if (!inline) {
                JOptionPane.showMessageDialog(d, problem, "Warning", JOptionPane.WARNING_MESSAGE);
            } else {
                error.setText("<html><body style='width:" + Math.max(300, p.getPreferredSize().width - 40) + "px'>"
                        + problem.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>") + "</body></html>");
                d.pack();
            }
        });
        cancel.addActionListener(e -> d.dispose());
        d.getRootPane().setDefaultButton(ok);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setLayout(new BorderLayout());
        d.add(p, BorderLayout.CENTER);
        d.add(south, BorderLayout.SOUTH);
        d.pack();
        d.setLocationRelativeTo(parent);
        d.setVisible(true);
        return saved[0];
    }

    /** Friendly text for a database error. */
    static String message(Exception e) {
        if (e instanceof SQLIntegrityConstraintViolationException) {
            return "This record is used by other data (shows, seats or bookings), so it cannot be removed or changed.";
        }
        return e.getMessage();
    }

    static boolean confirm(Component parent, String msg) {
        return JOptionPane.showConfirmDialog(parent, msg, "Confirm", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    static void error(Component parent, Exception e) {
        Theme.error(parent, message(e));
    }
}