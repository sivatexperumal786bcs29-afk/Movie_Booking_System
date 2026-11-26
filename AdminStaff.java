package MovieBooking;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;

/** Admin: create and manage theatre employees (each one works for exactly one theatre). */
class AdminStaff extends JPanel {
    private final AdminUi.DataTable table = new AdminUi.DataTable(
            "ID", "Name", "Phone", "Position", "Theatre", "Username", "Status", "Counter sales");

    AdminStaff() {
        setLayout(new BorderLayout());
        setOpaque(false);
        add(AdminUi.header("Theatre Staff", "Create employee logins. Each employee can work only on their own theatre.",
                Theme.BLUE, Theme.PURPLE), BorderLayout.NORTH);

        Theme.Btn add = AdminUi.btn("+ Add staff", Theme.GREEN);
        Theme.Btn toggle = AdminUi.btn("Activate / Deactivate", Theme.ORANGE);
        Theme.Btn del = AdminUi.btn("Delete staff", Theme.RED);
        add.addActionListener(e -> addStaff());
        toggle.addActionListener(e -> toggle());
        del.addActionListener(e -> delete());
        table.width(0, 40);
        table.width(1, 160);
        JLabel note = Theme.label("Staff can: sell tickets at the counter, block / unblock seats, check customers in at the entry "
                + "and see today's shows. An inactive employee cannot log in.", 13, Font.PLAIN, Theme.MUTED);
        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        top.add(AdminUi.buttons(add, toggle, del), BorderLayout.NORTH);
        top.add(note, BorderLayout.SOUTH);
        add(AdminUi.body(top, table), BorderLayout.CENTER);
    }

    void refresh() {
        try {
            table.load(StaffDao.staffList());
        } catch (SQLException ex) {
            AdminUi.error(this, ex);
        }
    }

    private void addStaff() {
        List<Object[]> theatres;
        try {
            theatres = StaffDao.theatres();
        } catch (SQLException ex) {
            AdminUi.error(this, ex);
            return;
        }
        if (theatres.isEmpty()) { Theme.warn(this, "Add a theatre first."); return; }
        JTextField name = AdminUi.tf("", 20), phone = AdminUi.tf("", 20), user = AdminUi.tf("", 20);
        JPasswordField pass = Theme.password(20);
        JComboBox<String> position = new JComboBox<>(new String[]{"Counter", "Manager"});
        JComboBox<String> theatre = new JComboBox<>();
        for (Object[] t : theatres) theatre.addItem(t[0] + " - " + t[1]);
        JLabel rule = Theme.label("<html><body style='width:260px'>" + Validate.PASSWORD_RULE + "</body></html>",
                12, Font.PLAIN, Theme.MUTED);

        boolean saved = AdminUi.form(this, "Add staff",
                new String[]{"Employee name", "Phone (10 digits)", "Position", "Theatre", "Login username", "Login password", ""},
                new JComponent[]{name, phone, position, theatre, user, pass, rule}, () -> {
                    if (name.getText().trim().length() < 2) return "Enter the employee's name.";
                    if (!phone.getText().trim().matches("\\d{10}")) return "Phone must be 10 digits.";
                    if (user.getText().trim().length() < 3) return "Username must be at least 3 characters.";
                    String problem = Validate.password(new String(pass.getPassword()));
                    if (problem != null) return problem;
                    int theatreId = Integer.parseInt(((String) theatre.getSelectedItem()).split(" - ")[0]);
                    try {
                        if (!StaffDao.addStaff(name.getText().trim(), phone.getText().trim(), (String) position.getSelectedItem(),
                                theatreId, user.getText().trim(), new String(pass.getPassword())))
                            return "That username is already taken. Choose another.";
                    } catch (SQLException ex) {
                        return "Could not save the employee: " + AdminUi.message(ex);
                    }
                    return null;
                });
        if (saved) refresh();
    }

    private void toggle() {
        String id = table.sel(0);
        if (id == null) { Theme.warn(this, "Select an employee first."); return; }
        boolean nowActive = "ACTIVE".equals(table.sel(6));
        try {
            StaffDao.setActive(Integer.parseInt(id), !nowActive);
            refresh();
        } catch (SQLException ex) {
            AdminUi.error(this, ex);
        }
    }

    private void delete() {
        String id = table.sel(0);
        if (id == null) { Theme.warn(this, "Select an employee first."); return; }
        if (!AdminUi.confirm(this, "Delete employee \"" + table.sel(1) + "\" and the login \"" + table.sel(5) + "\"?")) return;
        try {
            StaffDao.deleteStaff(Integer.parseInt(id));
            refresh();
        } catch (SQLException ex) {
            AdminUi.error(this, ex);
        }
    }
}