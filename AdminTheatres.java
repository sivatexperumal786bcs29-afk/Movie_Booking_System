package MovieBooking;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.SQLException;

/** Admin: manage theatres and their screens (a new screen gets a full seat layout). */
class AdminTheatres extends JPanel {
    private final AdminUi.DataTable theatres = new AdminUi.DataTable("ID", "Theatre", "Location", "City", "Contact", "Screens");
    private final AdminUi.DataTable screens = new AdminUi.DataTable("ID", "Theatre", "Screen", "Seats", "Layout", "Shows");

    AdminTheatres() {
        setLayout(new BorderLayout());
        setOpaque(false);
        add(AdminUi.header("Theatres and Screens", "Control every theatre, screen and seat layout", Theme.PURPLE, Theme.BLUE), BorderLayout.NORTH);

        Theme.Btn addT = AdminUi.btn("+ Add theatre", Theme.GREEN);
        Theme.Btn delT = AdminUi.btn("Delete theatre", Theme.RED);
        Theme.Btn addS = AdminUi.btn("+ Add screen to selected theatre", Theme.PINK);
        Theme.Btn delS = AdminUi.btn("Delete screen", Theme.RED);
        Theme.Btn viewS = AdminUi.btn("Preview layout", Theme.BLUE);
        Theme.Btn editS = AdminUi.btn("Change layout", Theme.ORANGE);
        addT.addActionListener(e -> addTheatre());
        delT.addActionListener(e -> deleteTheatre());
        addS.addActionListener(e -> addScreen());
        delS.addActionListener(e -> deleteScreen());
        viewS.addActionListener(e -> previewLayout());
        editS.addActionListener(e -> screenForm(null));
        theatres.width(0, 40);
        screens.width(0, 40);

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        top.add(Theme.label("Theatres", 18, Font.BOLD, Theme.NAVY), BorderLayout.NORTH);
        top.add(theatres, BorderLayout.CENTER);
        top.add(AdminUi.scope() > 0 ? AdminUi.buttons(addS) : AdminUi.buttons(addT, delT, addS), BorderLayout.SOUTH);

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setOpaque(false);
        bottom.add(Theme.label("Screens", 18, Font.BOLD, Theme.NAVY), BorderLayout.NORTH);
        bottom.add(screens, BorderLayout.CENTER);
        bottom.add(AdminUi.buttons(viewS, editS, delS), BorderLayout.SOUTH);

        JPanel grid = new JPanel(new GridLayout(2, 1, 0, 18));
        grid.setOpaque(false);
        grid.add(top);
        grid.add(bottom);
        add(AdminUi.body(null, grid), BorderLayout.CENTER);
    }

    void refresh() {
        try {
            theatres.load(AdminDao.table("SELECT t.theatreid, t.name, t.location, t.city, t.contactno, "
                    + "(SELECT COUNT(*) FROM Screen s WHERE s.theatreid = t.theatreid) FROM Theatre t "
                    + (AdminUi.scope() > 0 ? "WHERE t.theatreid = " + AdminUi.scope() + " " : "") + "ORDER BY t.theatreid"));
            screens.load(AdminDao.table("SELECT s.screenid, t.name, s.screenname, "
                    + "(SELECT COUNT(*) FROM Seat x WHERE x.screenid = s.screenid), "
                    + "CONCAT((SELECT COUNT(DISTINCT x.rowno) FROM Seat x WHERE x.screenid = s.screenid), ' rows x ', "
                    + "(SELECT IFNULL(MAX(x.seatnumber), 0) FROM Seat x WHERE x.screenid = s.screenid), ' seats'), "
                    + "(SELECT COUNT(*) FROM `Show` sh WHERE sh.screenid = s.screenid) "
                    + "FROM Screen s JOIN Theatre t ON t.theatreid = s.theatreid "
                    + (AdminUi.scope() > 0 ? "WHERE s.theatreid = " + AdminUi.scope() + " " : "") + "ORDER BY s.screenid"));
        } catch (SQLException ex) {
            AdminUi.error(this, ex);
        }
    }

    private void addTheatre() {
        JTextField name = AdminUi.tf("", 22), loc = AdminUi.tf("", 22), city = AdminUi.tf("Chennai", 22), phone = AdminUi.tf("", 22);
        boolean saved = AdminUi.form(this, "Add theatre", new String[]{"Name", "Location", "City", "Contact no"},
                new JComponent[]{name, loc, city, phone}, () -> {
                    if (name.getText().trim().isEmpty()) return "Theatre name is required.";
                    try {
                        AdminDao.exec("INSERT INTO Theatre (name, location, city, contactno) VALUES (?, ?, ?, ?)",
                                name.getText().trim(), loc.getText().trim(), city.getText().trim(), phone.getText().trim());
                    } catch (SQLException ex) {
                        return "Could not save the theatre: " + AdminUi.message(ex);
                    }
                    return null;
                });
        if (saved) refresh();
    }

    private void deleteTheatre() {
        String id = theatres.sel(0);
        if (id == null) { Theme.warn(this, "Select a theatre first."); return; }
        if (!AdminUi.confirm(this, "Delete theatre \"" + theatres.sel(1) + "\"?\nDelete its screens first.")) return;
        try {
            AdminDao.exec("DELETE FROM Theatre WHERE theatreid = ?", Integer.parseInt(id));
            refresh();
        } catch (SQLException ex) {
            AdminUi.error(this, ex);
        }
    }

    private void addScreen() {
        if (theatres.sel(0) == null) { Theme.warn(this, "Select a theatre in the first table."); return; }
        screenForm(Integer.valueOf(theatres.sel(0)));
    }

    /** theatreId != null: add a new screen to that theatre. theatreId == null: change the layout of the selected screen. */
    private void screenForm(Integer theatreId) {
        boolean adding = theatreId != null;
        String screenId = screens.sel(0);
        if (!adding && screenId == null) { Theme.warn(this, "Select a screen in the second table."); return; }
        JTextField name = AdminUi.tf(adding ? "Screen 1" : screens.sel(2), 16);
        name.setEnabled(adding);
        JSpinner rows = new JSpinner(new SpinnerNumberModel(10, 3, 20, 1));
        JSpinner perRow = new JSpinner(new SpinnerNumberModel(12, 4, 24, 1));
        JSpinner front = new JSpinner(new SpinnerNumberModel(3, 0, 20, 1));
        JSpinner premium = new JSpinner(new SpinnerNumberModel(2, 0, 20, 1));
        JSpinner aisle = new JSpinner(new SpinnerNumberModel(6, 0, 23, 1));
        JLabel note = Theme.label("<html><body style='width:300px'>Rows are A, B, C ... from the screen backwards. "
                + "The first rows are Front, the last rows are Premium, the rest Standard. "
                + "Aisle: a gap is drawn after this seat number (0 = no aisle).</body></html>", 12, Font.PLAIN, Theme.MUTED);
        String title = adding ? "Add screen to " + theatres.sel(1) : "Change layout of " + screens.sel(1) + " / " + screens.sel(2);
        boolean saved = AdminUi.form(this, title,
                new String[]{"Screen name", "Rows", "Seats per row", "Front rows", "Premium rows", "Aisle after seat no.", ""},
                new JComponent[]{name, rows, perRow, front, premium, aisle, note}, () -> {
                    if (name.getText().trim().isEmpty()) return "Screen name is required.";
                    int r = (Integer) rows.getValue(), p = (Integer) perRow.getValue(), f = (Integer) front.getValue(),
                            pr = (Integer) premium.getValue(), a = (Integer) aisle.getValue();
                    try {
                        if (adding) AdminDao.addScreen(theatreId, name.getText().trim(), r, p, f, pr, a);
                        else AdminDao.rebuildLayout(Integer.parseInt(screenId), r, p, f, pr, a);
                    } catch (SQLException ex) {
                        return AdminUi.message(ex);
                    }
                    return null;
                });
        if (saved) refresh();
    }

    /** Draws the seat map of the selected screen so the admin can see the arrangement. */
    private void previewLayout() {
        String id = screens.sel(0);
        if (id == null) { Theme.warn(this, "Select a screen first."); return; }
        java.util.List<Object[]> seats;
        try {
            seats = AdminDao.table("SELECT rowno, seatnumber, category, gapbefore FROM Seat WHERE screenid = ? "
                    + "ORDER BY rowno, seatnumber", Integer.parseInt(id));
        } catch (SQLException ex) {
            AdminUi.error(this, ex);
            return;
        }
        if (seats.isEmpty()) { Theme.warn(this, "This screen has no seats."); return; }
        java.util.Map<String, java.util.List<Object[]>> byRow = new java.util.LinkedHashMap<>();
        for (Object[] s : seats) byRow.computeIfAbsent(String.valueOf(s[0]), k -> new java.util.ArrayList<>()).add(s);

        JComponent map = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.aa(g);
                int cell = 22, gap = 4, aisle = 22, y = 46, left = 34;
                g2.setPaint(new GradientPaint(left, 0, Theme.PINK, getWidth() - left, 0, Theme.PURPLE));
                g2.fillRoundRect(left + 40, 10, getWidth() - 2 * left - 80, 10, 10, 10);
                g2.setColor(Theme.MUTED);
                g2.setFont(Theme.font(Font.PLAIN, 11));
                g2.drawString("SCREEN", getWidth() / 2 - 20, 38);
                for (java.util.Map.Entry<String, java.util.List<Object[]>> e : byRow.entrySet()) {
                    int x = left;
                    g2.setColor(Theme.NAVY);
                    g2.setFont(Theme.font(Font.BOLD, 11));
                    g2.drawString(e.getKey(), 8, y + 15);
                    for (Object[] s : e.getValue()) {
                        if (Integer.parseInt(String.valueOf(s[3])) == 1) x += aisle;
                        String cat = String.valueOf(s[2]);
                        g2.setColor(cat.equals("Premium") ? Theme.PURPLE : cat.equals("Front") ? Theme.ORANGE : Theme.GREEN);
                        g2.fillRoundRect(x, y, cell, cell - 4, 6, 6);
                        x += cell + gap;
                    }
                    y += cell + gap;
                }
                g2.dispose();
            }
        };
        int maxSeats = 0;
        for (java.util.List<Object[]> r : byRow.values()) maxSeats = Math.max(maxSeats, r.size());
        map.setPreferredSize(new Dimension(Math.max(420, 34 + maxSeats * 26 + 60), 60 + byRow.size() * 26));

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 6));
        legend.add(Theme.label("Front", 12, Font.BOLD, Theme.ORANGE));
        legend.add(Theme.label("Standard", 12, Font.BOLD, Theme.GREEN));
        legend.add(Theme.label("Premium", 12, Font.BOLD, Theme.PURPLE));
        JPanel box = new JPanel(new BorderLayout());
        box.add(new JScrollPane(map), BorderLayout.CENTER);
        box.add(legend, BorderLayout.SOUTH);
        JOptionPane.showMessageDialog(this, box, screens.sel(1) + " / " + screens.sel(2) + "  -  "
                + screens.sel(3) + " seats, " + screens.sel(4), JOptionPane.PLAIN_MESSAGE);
    }

    private void deleteScreen() {
        String id = screens.sel(0);
        if (id == null) { Theme.warn(this, "Select a screen first."); return; }
        if (!AdminUi.confirm(this, "Delete " + screens.sel(1) + " / " + screens.sel(2) + " and all its seats?\n"
                + "A screen that has shows cannot be deleted.")) return;
        try {
            AdminDao.exec("DELETE FROM Seat WHERE screenid = ?", Integer.parseInt(id));
            AdminDao.exec("DELETE FROM Screen WHERE screenid = ?", Integer.parseInt(id));
            refresh();
        } catch (SQLException ex) {
            AdminUi.error(this, ex);
        }
    }
}

