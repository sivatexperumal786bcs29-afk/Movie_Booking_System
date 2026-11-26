package MovieBooking;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Theatre employee window (own theatre only).
 *   Counter staff : Counter booking, Verify ticket (booking number), Today's shows.
 *   Manager       : Movies, Shows, Screens and seat layout, Seat control, Today's shows.
 */
class StaffForm extends JFrame {
    private final AdminUi.DataTable sellTable = new AdminUi.DataTable(
            "ID", "Movie", "Screen", "Date", "Time", "Base price", "Free", "Sold", "Blocked");
    private final AdminUi.DataTable controlTable = new AdminUi.DataTable(
            "ID", "Movie", "Screen", "Date", "Time", "Base price", "Free", "Sold", "Blocked");
    private final AdminUi.DataTable entryTable = new AdminUi.DataTable(
            "Booking", "Customer", "Phone", "Movie", "Screen", "Time", "Seats", "Sold at", "Entry");
    private final AdminUi.DataTable todayTable = new AdminUi.DataTable(
            "ID", "Movie", "Screen", "Time", "Status", "Sold", "Blocked", "Free", "Counter sales", "Online sales", "Entered");
    private final JTextField bookingField = Theme.field(10);
    private final JLabel verdict = Theme.label(" ", 20, Font.BOLD, Theme.MUTED);
    private final JLabel details = Theme.label(" ", 14, Font.PLAIN, Theme.NAVY);
    private final Theme.Btn allow = AdminUi.btn("Allow entry", Theme.GREEN);
    private int verifiedId = -1;
    private final JTabbedPane tabs = new JTabbedPane();
    private final List<Runnable> refreshers = new ArrayList<>();

    StaffForm() {
        super("CineBook - Staff");
        Theme.frame(this, 1360, 860);
        setLayout(new BorderLayout());
        add(topBar(), BorderLayout.NORTH);

        UIManager.put("TabbedPane.contentAreaColor", Theme.BG);
        tabs.setBackground(Theme.BG);
        tabs.setFont(Theme.font(Font.BOLD, 14));
        if (Dao.Session.isManager()) {
            AdminMovies movies = new AdminMovies();
            AdminShows shows = new AdminShows();
            AdminTheatres screens = new AdminTheatres();
            tab("Movies", movies, movies::refresh);
            tab("Shows", shows, shows::refresh);
            tab("Screens & seats", screens, screens::refresh);
            tab("Seat control", controlTab(), () -> load(controlTable));
            tab("Today's shows", todayTab(), () -> load(todayTable));
        } else {
            tab("Counter booking", sellTab(), () -> load(sellTable));
            tab("Verify ticket", entryTab(), () -> load(entryTable));
            tab("Today's shows", todayTab(), () -> load(todayTable));
        }
        tabs.addChangeListener(e -> refresh());
        add(tabs, BorderLayout.CENTER);
        refresh();
    }

    private void tab(String title, JComponent c, Runnable refresh) {
        tabs.addTab(title, c);
        refreshers.add(refresh);
    }

    private void refresh() {
        int i = tabs.getSelectedIndex();
        if (i >= 0 && i < refreshers.size()) refreshers.get(i).run();
    }

    private void load(AdminUi.DataTable t) {
        try {
            if (t == sellTable || t == controlTable) t.load(StaffDao.upcomingShows());
            else if (t == entryTable) t.load(StaffDao.todayBookings());
            else t.load(StaffDao.todayShows());
        } catch (SQLException ex) {
            AdminUi.error(this, ex);
        }
    }

    // ------------------------------------------------------------------ chrome
    private JPanel topBar() {
        Theme.Gradient bar = new Theme.Gradient(Theme.NAVY, new Color(0x4B1D8F));
        bar.setLayout(new BorderLayout());
        bar.setBorder(new EmptyBorder(16, 30, 16, 30));
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        String role = Dao.Session.isManager() ? "Manager" : "Counter";
        left.add(Theme.label("<html><span style='color:#F84464'>Cine</span>Book  " + role + "</html>", 26, Font.BOLD, Color.WHITE));
        left.add(Theme.label(Dao.Session.theatreName + "  •  you can work only on this theatre", 13, Font.PLAIN,
                new Color(0xD7D2F0)));
        bar.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 4));
        right.setOpaque(false);
        String u = Dao.Session.username == null ? "staff" : Dao.Session.username;
        right.add(Theme.avatar(Theme.initials(u), 38));
        right.add(Theme.label(u, 14, Font.BOLD, Color.WHITE));
        Theme.Btn logout = new Theme.Btn("Logout", Theme.ORANGE);
        logout.addActionListener(e -> {
            Dao.Session.clear();
            Theme.go(this, new WelcomeForm());
        });
        right.add(logout);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JButton refreshBtn() {
        Theme.Btn b = AdminUi.btn("Refresh", Theme.PURPLE);
        b.addActionListener(e -> refresh());
        return b;
    }

    // --------------------------------------------------------- counter booking
    private JComponent sellTab() {
        Theme.Btn sell = AdminUi.btn("Sell tickets for selected show", Theme.GREEN);
        sell.addActionListener(e -> sell());
        sellTable.width(0, 40);
        sellTable.width(1, 180);
        JLabel note = Theme.label("Walk-in customers: pick a show, enter name and phone, choose seats and collect cash.",
                13, Font.PLAIN, Theme.MUTED);
        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        top.add(AdminUi.buttons(sell, refreshBtn()), BorderLayout.NORTH);
        top.add(note, BorderLayout.SOUTH);
        return AdminUi.body(top, sellTable);
    }

    private void sell() {
        String id = sellTable.sel(0);
        if (id == null) { Theme.warn(this, "Select a show first."); return; }
        JTextField name = AdminUi.tf("", 20), phone = AdminUi.tf("", 20);
        boolean ok = AdminUi.form(this, "Walk-in customer", new String[]{"Customer name", "Phone (10 digits)"},
                new JComponent[]{name, phone}, () -> {
                    if (name.getText().trim().length() < 2) return "Enter the customer's name.";
                    if (!phone.getText().trim().matches("\\d{10}")) return "Phone must be 10 digits.";
                    return null;
                });
        if (!ok) return;
        try {
            Dao.Show show = Dao.show(Integer.parseInt(id));
            Dao.Movie movie = findMovie(show.movieId());
            Dao.newWalkin(name.getText().trim(), phone.getText().trim());
            Theme.go(this, new SeatForm(movie, show, 0, SeatForm.Mode.COUNTER));
        } catch (SQLException ex) {
            AdminUi.error(this, ex);
        }
    }

    private Dao.Movie findMovie(int movieId) throws SQLException {
        for (Dao.Movie m : Dao.movies()) if (m.id() == movieId) return m;
        throw new SQLException("Movie not found.");
    }

    // --------------------------------------------------------- manager: seat control
    private JComponent controlTab() {
        Theme.Btn open = AdminUi.btn("Open seat map of selected show", Theme.PINK);
        open.addActionListener(e -> {
            String id = controlTable.sel(0);
            if (id == null) { Theme.warn(this, "Select a show first."); return; }
            try {
                Dao.Show show = Dao.show(Integer.parseInt(id));
                Theme.go(this, new SeatForm(findMovie(show.movieId()), show, 0, SeatForm.Mode.CONTROL));
            } catch (SQLException ex) {
                AdminUi.error(this, ex);
            }
        });
        controlTable.width(0, 40);
        controlTable.width(1, 180);
        JLabel note = Theme.label("Block a broken seat, a VIP hold or a maintenance seat. Blocked seats cannot be booked online or at the counter.",
                13, Font.PLAIN, Theme.MUTED);
        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        top.add(AdminUi.buttons(open, refreshBtn()), BorderLayout.NORTH);
        top.add(note, BorderLayout.SOUTH);
        return AdminUi.body(top, controlTable);
    }

    // --------------------------------------------------------- counter: verify ticket
    private JComponent entryTab() {
        Theme.Btn verify = AdminUi.btn("Verify ticket", Theme.PINK);
        verify.addActionListener(e -> {
            String t = bookingField.getText().trim();
            if (!t.matches("\\d+")) { showVerdict(null, "Type the booking number printed on the ticket.", false); return; }
            verify(Integer.parseInt(t));
        });
        bookingField.addActionListener(e -> verify.doClick());
        allow.setEnabled(false);
        allow.addActionListener(e -> allowEntry());

        entryTable.width(0, 60);
        entryTable.width(3, 170);
        entryTable.width(6, 120);
        entryTable.table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && entryTable.sel(0) != null) bookingField.setText(entryTable.sel(0));
        });

        JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        line.setOpaque(false);
        line.add(Theme.label("Booking no.", 13, Font.BOLD, Theme.NAVY));
        line.add(bookingField);
        line.add(verify);
        line.add(allow);
        line.add(refreshBtn());

        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setOpaque(false);
        card.add(verdict, BorderLayout.NORTH);
        card.add(details, BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(line, BorderLayout.NORTH);
        top.add(card, BorderLayout.CENTER);
        top.add(Theme.label("Today's bookings of this theatre (click a row to fill the booking number)", 13, Font.PLAIN, Theme.MUTED),
                BorderLayout.SOUTH);
        return AdminUi.body(top, entryTable);
    }

    private void verify(int bookingId) {
        try {
            StaffDao.Ticket t = StaffDao.verify(bookingId);
            verifiedId = t.ok() ? t.id() : -1;
            showVerdict("<html>Booking #" + t.id() + "  •  " + t.customer() + " (" + t.phone() + ")<br>"
                    + t.movie() + "  •  " + t.screen() + "  •  " + t.date() + "  " + t.time()
                    + "<br>Seats: " + t.seats() + "  •  " + Theme.money(t.total()) + "  •  sold " + t.channel().toLowerCase()
                    + "</html>", t.verdict(), t.ok());
        } catch (SQLException ex) {
            verifiedId = -1;
            showVerdict(null, AdminUi.message(ex), false);
        }
    }

    private void showVerdict(String info, String text, boolean ok) {
        verdict.setForeground(ok ? new Color(0x1B8A37) : Theme.RED);
        verdict.setText(text);
        details.setText(info == null ? " " : info);
        allow.setEnabled(ok);
        if (!ok) verifiedId = verifiedId == -1 ? -1 : verifiedId;
    }

    private void allowEntry() {
        if (verifiedId < 0) return;
        try {
            String msg = StaffDao.checkIn(verifiedId);
            verifiedId = -1;
            allow.setEnabled(false);
            bookingField.setText("");
            verdict.setForeground(new Color(0x1B8A37));
            verdict.setText(msg);
            details.setText(" ");
        } catch (SQLException ex) {
            showVerdict(null, AdminUi.message(ex), false);
        }
        load(entryTable);
    }

    // --------------------------------------------------------- today
    private JComponent todayTab() {
        todayTable.width(0, 40);
        todayTable.width(1, 180);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(AdminUi.buttons(refreshBtn()), BorderLayout.NORTH);
        return AdminUi.body(top, todayTable);
    }
}