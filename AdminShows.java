package MovieBooking;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** Admin: create shows (seats and prices are generated automatically) and cancel shows with refunds. */
class AdminShows extends JPanel {
    private final AdminUi.DataTable table = new AdminUi.DataTable(
            "ID", "Movie", "Theatre", "Screen", "Date", "Time", "Base price", "Seats sold", "Status");
    private final JCheckBox upcoming = new JCheckBox("Upcoming only", true);

    AdminShows() {
        setLayout(new BorderLayout());
        setOpaque(false);
        add(AdminUi.header("Shows", "Schedule a movie on a screen. Seats and prices are created automatically.", Theme.ORANGE, Theme.PINK), BorderLayout.NORTH);

        Theme.Btn add = AdminUi.btn("+ Add show", Theme.GREEN);
        Theme.Btn all = AdminUi.btn(AdminUi.scope() > 0 ? "+ Show on all my screens" : "+ Show on ALL screens", Theme.PURPLE);
        Theme.Btn cancel = AdminUi.btn("Cancel selected show", Theme.RED);
        all.addActionListener(e -> addEverywhere());
        add.addActionListener(e -> addShow());
        cancel.addActionListener(e -> cancelShow());
        upcoming.setOpaque(false);
        upcoming.setFont(Theme.font(Font.BOLD, 13));
        upcoming.addActionListener(e -> refresh());
        table.width(0, 40);
        table.width(1, 160);
        add(AdminUi.body(AdminUi.buttons(add, all, cancel, upcoming), table), BorderLayout.CENTER);
    }

    void refresh() {
        java.util.List<String> cond = new java.util.ArrayList<>();
        if (upcoming.isSelected()) cond.add("sh.showdate >= CURDATE()");
        if (AdminUi.scope() > 0) cond.add("sc.theatreid = " + AdminUi.scope());
        String where = cond.isEmpty() ? "" : "WHERE " + String.join(" AND ", cond) + " ";
        try {
            table.load(AdminDao.table("SELECT sh.showid, m.title, t.name, sc.screenname, sh.showdate, sh.starttime, sh.baseprice, "
                    + "CONCAT(ROUND(100 * SUM(CASE WHEN ss.status = 'AVAILABLE' THEN 0 ELSE 1 END) / COUNT(ss.showseatid)), '%'), sh.status "
                    + "FROM `Show` sh JOIN Movie m ON m.movieid = sh.movieid "
                    + "JOIN Screen sc ON sc.screenid = sh.screenid JOIN Theatre t ON t.theatreid = sc.theatreid "
                    + "LEFT JOIN ShowSeat ss ON ss.showid = sh.showid " + where
                    + "GROUP BY sh.showid, m.title, t.name, sc.screenname, sh.showdate, sh.starttime, sh.baseprice, sh.status "
                    + "ORDER BY sh.showdate, sh.starttime, t.name"));
        } catch (SQLException ex) {
            AdminUi.error(this, ex);
        }
    }

    private void addShow() {
        List<Object[]> movies, screens;
        try {
            movies = AdminDao.table("SELECT movieid, title FROM Movie ORDER BY title");
            screens = AdminDao.table("SELECT s.screenid, t.name, s.screenname FROM Screen s "
                    + "JOIN Theatre t ON t.theatreid = s.theatreid "
                    + (AdminUi.scope() > 0 ? "WHERE s.theatreid = " + AdminUi.scope() + " " : "") + "ORDER BY t.name, s.screenname");
        } catch (SQLException ex) {
            AdminUi.error(this, ex);
            return;
        }
        if (movies.isEmpty() || screens.isEmpty()) { Theme.warn(this, "Add a movie and a screen first."); return; }

        JComboBox<String> mc = new JComboBox<>();
        for (Object[] m : movies) mc.addItem(m[0] + " - " + m[1]);
        JComboBox<String> sc = new JComboBox<>();
        for (Object[] s : screens) sc.addItem(s[0] + " - " + s[1] + " / " + s[2]);
        DatePicker date = new DatePicker(LocalDate.now(), LocalDate.now().plusYears(1), LocalDate.now().plusDays(1));
        date.setDate(LocalDate.now().plusDays(1));
        JComboBox<String> time = new JComboBox<>(new String[]{"10:30", "14:00", "18:30", "22:00"});
        time.setEditable(true);
        JTextField price = AdminUi.tf("200", 14);

        boolean saved = AdminUi.form(this, "Add show", new String[]{"Movie", "Screen", "Date", "Time (hh:mm)", "Base price"},
                new JComponent[]{mc, sc, date, time, price}, () -> {
                    LocalDate d = date.getDate();
                    if (d == null) return "Pick the show date from the calendar.";
                    if (d.isBefore(LocalDate.now())) return "The show date is in the past.";
                    String t = String.valueOf(time.getSelectedItem()).trim();
                    if (!t.matches("([01]\\d|2[0-3]):[0-5]\\d")) return "Enter the time like 18:30.";
                    double base;
                    try {
                        base = Double.parseDouble(price.getText().trim());
                    } catch (NumberFormatException ex) {
                        return "Base price must be a number above 0.";
                    }
                    if (base <= 0) return "Base price must be a number above 0.";
                    int movieId = Integer.parseInt(((String) mc.getSelectedItem()).split(" - ")[0]);
                    int screenId = Integer.parseInt(((String) sc.getSelectedItem()).split(" - ")[0]);
                    try {
                        AdminDao.addShow(movieId, screenId, d, t, base);
                    } catch (SQLException ex) {
                        return AdminUi.message(ex);
                    }
                    return null;
                });
        if (saved) refresh();
    }

    /** One movie, one date and time, on every screen of every theatre at once. */
    private void addEverywhere() {
        List<Object[]> movies;
        double screens;
        try {
            movies = AdminDao.table("SELECT movieid, title FROM Movie ORDER BY title");
            screens = AdminDao.scalar("SELECT COUNT(*) FROM Screen" + (AdminUi.scope() > 0 ? " WHERE theatreid = " + AdminUi.scope() : ""));
        } catch (SQLException ex) {
            AdminUi.error(this, ex);
            return;
        }
        if (movies.isEmpty() || screens == 0) { Theme.warn(this, "Add a movie and a screen first."); return; }

        JComboBox<String> mc = new JComboBox<>();
        for (Object[] m : movies) mc.addItem(m[0] + " - " + m[1]);
        DatePicker date = new DatePicker(LocalDate.now(), LocalDate.now().plusYears(1), LocalDate.now().plusDays(1));
        date.setDate(LocalDate.now().plusDays(1));
        JComboBox<String> time = new JComboBox<>(new String[]{"10:30", "14:00", "18:30", "22:00"});
        time.setEditable(true);
        JTextField price = AdminUi.tf("200", 14);
        JLabel note = Theme.label("<html><body style='width:300px'>The show is created on all " + (long) screens
                + " screens of every theatre. A screen that is busy at that time is skipped and listed in the report.</body></html>",
                12, Font.PLAIN, Theme.MUTED);

        final java.util.concurrent.atomic.AtomicReference<List<AdminDao.Placement>> report = new java.util.concurrent.atomic.AtomicReference<>();
        boolean ok = AdminUi.form(this, "Show on ALL screens", new String[]{"Movie", "Date", "Time (hh:mm)", "Base price", ""},
                new JComponent[]{mc, date, time, price, note}, () -> {
                    LocalDate d = date.getDate();
                    if (d == null) return "Pick the show date from the calendar.";
                    String t = String.valueOf(time.getSelectedItem()).trim();
                    if (!t.matches("([01]\\d|2[0-3]):[0-5]\\d")) return "Enter the time like 18:30.";
                    double base;
                    try {
                        base = Double.parseDouble(price.getText().trim());
                    } catch (NumberFormatException ex) {
                        return "Base price must be a number above 0.";
                    }
                    if (base <= 0) return "Base price must be a number above 0.";
                    int movieId = Integer.parseInt(((String) mc.getSelectedItem()).split(" - ")[0]);
                    try {
                        report.set(AdminDao.addShowEverywhere(movieId, d, t, base));
                    } catch (SQLException ex) {
                        return AdminUi.message(ex);
                    }
                    return null;
                });
        if (!ok) return;
        refresh();
        showReport(report.get());
    }

    private void showReport(List<AdminDao.Placement> list) {
        AdminUi.DataTable t = new AdminUi.DataTable("Theatre", "Screen", "Seats", "Result");
        int created = 0, seats = 0;
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        for (AdminDao.Placement p : list) {
            if (p.created()) { created++; seats += p.seats(); }
            rows.add(new Object[]{p.theatre(), p.screen(), p.seats(), (p.created() ? "CREATED  " : "SKIPPED  ") + p.note()});
        }
        t.load(rows);
        t.setPreferredSize(new Dimension(760, 320));
        JPanel box = new JPanel(new BorderLayout(0, 10));
        box.add(Theme.label(created + " of " + list.size() + " screens now run this movie  (" + seats + " seats on sale)",
                16, Font.BOLD, created == list.size() ? new Color(0x1B8A37) : Theme.ORANGE), BorderLayout.NORTH);
        box.add(t, BorderLayout.CENTER);
        JOptionPane.showMessageDialog(this, box, "Release report", JOptionPane.PLAIN_MESSAGE);
    }

    private void cancelShow() {
        String id = table.sel(0);
        if (id == null) { Theme.warn(this, "Select a show first."); return; }
        if ("CANCELLED".equals(table.sel(8))) { Theme.warn(this, "This show is already cancelled."); return; }
        if (!AdminUi.confirm(this, "Cancel the " + table.sel(5) + " show of \"" + table.sel(1) + "\" on " + table.sel(4)
                + "?\nEvery confirmed booking is cancelled with a 100% refund.")) return;
        try {
            int n = AdminDao.cancelShow(Integer.parseInt(id));
            Theme.info(this, "Show cancelled. " + n + " booking(s) refunded in full.");
            refresh();
        } catch (SQLException ex) {
            AdminUi.error(this, ex);
        }
    }
}

