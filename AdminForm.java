package MovieBooking;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Admin panel shell: left menu + dashboard and management screens. */
class AdminForm extends JFrame {
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final Theme.Btn[] nav = new Theme.Btn[7];
    private final String[] names = {"Dashboard", "Movies", "Theatres", "Shows", "Bookings", "Pricing", "Staff"};

    private final JPanel dashboard = new JPanel(new BorderLayout());
    private final JPanel statsRow = new JPanel(new GridLayout(2, 3, 16, 16));
    private final AdminUi.DataTable upcoming = new AdminUi.DataTable("Movie", "Theatre / Screen", "Date", "Time", "Sold");
    private List<Object[]> revenue = new ArrayList<>();

    private final AdminMovies movies = new AdminMovies();
    private final AdminTheatres theatres = new AdminTheatres();
    private final AdminShows shows = new AdminShows();
    private final AdminBookings bookings = new AdminBookings();
    private final AdminPricing pricing = new AdminPricing();
    private final AdminStaff staff = new AdminStaff();

    AdminForm() {
        super("CineBook - Admin Panel");
        Theme.frame(this, 1360, 860);
        setLayout(new BorderLayout());
        add(sidebar(), BorderLayout.WEST);

        body.setOpaque(false);
        body.add(buildDashboard(), "0");
        body.add(movies, "1");
        body.add(theatres, "2");
        body.add(shows, "3");
        body.add(bookings, "4");
        body.add(pricing, "5");
        body.add(staff, "6");
        add(body, BorderLayout.CENTER);
        show(0);
    }

    private void show(int i) {
        cards.show(body, String.valueOf(i));
        for (int k = 0; k < names.length; k++) {
            if (k == i) nav[k].style(Theme.PINK, false); else nav[k].style(new Color(0xB9B6D6), true);
        }
        switch (i) {
            case 0 -> refreshDashboard();
            case 1 -> movies.refresh();
            case 2 -> theatres.refresh();
            case 3 -> shows.refresh();
            case 4 -> bookings.refresh();
            case 5 -> pricing.refresh();
            default -> staff.refresh();
        }
    }

    private JPanel sidebar() {
        Theme.Gradient side = new Theme.Gradient(Theme.NAVY, new Color(0x2B1458));
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(new EmptyBorder(22, 18, 22, 18));
        side.setPreferredSize(new Dimension(230, 100));

        JLabel logo = Theme.label("<html><span style='color:#F84464'>Cine</span>Book</html>", 26, Font.BOLD, Color.WHITE);
        JLabel role = Theme.label("ADMIN PANEL", 11, Font.BOLD, new Color(0xB9B6D6));
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);
        role.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(logo);
        side.add(role);
        side.add(Box.createVerticalStrut(26));

        for (int i = 0; i < names.length; i++) {
            int idx = i;
            nav[i] = new Theme.Btn(names[i], new Color(0xB9B6D6), true);
            nav[i].setHorizontalAlignment(SwingConstants.LEFT);
            nav[i].setAlignmentX(Component.LEFT_ALIGNMENT);
            nav[i].setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            nav[i].addActionListener(e -> show(idx));
            side.add(nav[i]);
            side.add(Box.createVerticalStrut(8));
        }
        side.add(Box.createVerticalGlue());

        String uname = Dao.Session.username == null ? "admin" : Dao.Session.username;
        JPanel who = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        who.setOpaque(false);
        who.setAlignmentX(Component.LEFT_ALIGNMENT);
        who.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        who.add(Theme.avatar(Theme.initials(uname), 38));
        who.add(Theme.label(uname, 14, Font.BOLD, Color.WHITE));
        side.add(who);
        side.add(Box.createVerticalStrut(12));

        Theme.Btn logout = new Theme.Btn("Logout", Theme.ORANGE);
        logout.setAlignmentX(Component.LEFT_ALIGNMENT);
        logout.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        logout.addActionListener(e -> {
            Dao.Session.clear();
            Theme.go(this, new WelcomeForm());
        });
        side.add(logout);
        return side;
    }

    // ------------------------------------------------------------ dashboard
    private JPanel buildDashboard() {
        dashboard.setOpaque(false);
        dashboard.add(AdminUi.header("Dashboard", "Sales, shows and customers at a glance", Theme.PINK, Theme.PURPLE), BorderLayout.NORTH);

        statsRow.setOpaque(false);
        JComponent chart = new JComponent() {
            @Override protected void paintComponent(Graphics g) { paintChart((Graphics2D) Theme.aa(g), getWidth(), getHeight()); }
        };
        chart.setPreferredSize(new Dimension(100, 250));

        Theme.Card chartCard = new Theme.Card();
        chartCard.setLayout(new BorderLayout());
        chartCard.setBorder(new EmptyBorder(16, 20, 20, 20));
        chartCard.add(Theme.label("Revenue by movie", 18, Font.BOLD, Theme.NAVY), BorderLayout.NORTH);
        chartCard.add(chart, BorderLayout.CENTER);

        JPanel lower = new JPanel(new BorderLayout(0, 8));
        lower.setOpaque(false);
        lower.add(Theme.label("Upcoming shows", 18, Font.BOLD, Theme.NAVY), BorderLayout.NORTH);
        lower.add(upcoming, BorderLayout.CENTER);

        JPanel mid = new JPanel(new GridBagLayout());
        mid.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH;
        gc.weighty = 1;
        gc.gridx = 0; gc.weightx = 0.42; gc.insets = new Insets(0, 0, 0, 18);
        mid.add(chartCard, gc);
        gc.gridx = 1; gc.weightx = 0.58; gc.insets = new Insets(0, 0, 0, 0);
        mid.add(lower, gc);

        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(22, 30, 26, 30));
        content.add(statsRow, BorderLayout.NORTH);
        content.add(mid, BorderLayout.CENTER);
        dashboard.add(content, BorderLayout.CENTER);
        return dashboard;
    }

    private void refreshDashboard() {
        try {
            double rev = AdminDao.scalar("SELECT IFNULL(SUM(totalamount), 0) FROM Booking WHERE status = 'CONFIRMED'");
            double bk = AdminDao.scalar("SELECT COUNT(*) FROM Booking WHERE status = 'CONFIRMED'");
            double ref = AdminDao.scalar("SELECT IFNULL(SUM(refundamount), 0) FROM Booking WHERE status = 'CANCELLED'");
            double cust = AdminDao.scalar("SELECT COUNT(*) FROM Customer");
            double mov = AdminDao.scalar("SELECT COUNT(*) FROM Movie");
            double sh = AdminDao.scalar("SELECT COUNT(*) FROM `Show` WHERE status = 'ACTIVE' AND showdate >= CURDATE()");
            statsRow.removeAll();
            statsRow.add(stat("Revenue", Theme.money(rev), Theme.GREEN));
            statsRow.add(stat("Confirmed bookings", String.valueOf((long) bk), Theme.PINK));
            statsRow.add(stat("Refunds paid", Theme.money(ref), Theme.ORANGE));
            statsRow.add(stat("Customers", String.valueOf((long) cust), Theme.BLUE));
            statsRow.add(stat("Movies", String.valueOf((long) mov), Theme.PURPLE));
            statsRow.add(stat("Upcoming shows", String.valueOf((long) sh), Theme.NAVY));
            statsRow.revalidate();

            revenue = AdminDao.table("SELECT m.title, SUM(b.totalamount) AS rev FROM Booking b "
                    + "JOIN `Show` sh ON sh.showid = b.showid JOIN Movie m ON m.movieid = sh.movieid "
                    + "WHERE b.status = 'CONFIRMED' GROUP BY m.title ORDER BY rev DESC LIMIT 7");

            List<Object[]> rows = AdminDao.table("SELECT m.title, CONCAT(t.name, ' / ', sc.screenname), sh.showdate, sh.starttime, "
                    + "CONCAT(ROUND(100 * SUM(CASE WHEN ss.status = 'AVAILABLE' THEN 0 ELSE 1 END) / COUNT(ss.showseatid)), '%') "
                    + "FROM `Show` sh JOIN Movie m ON m.movieid = sh.movieid JOIN Screen sc ON sc.screenid = sh.screenid "
                    + "JOIN Theatre t ON t.theatreid = sc.theatreid LEFT JOIN ShowSeat ss ON ss.showid = sh.showid "
                    + "WHERE sh.status = 'ACTIVE' AND sh.showdate >= CURDATE() "
                    + "GROUP BY sh.showid, m.title, t.name, sc.screenname, sh.showdate, sh.starttime "
                    + "ORDER BY sh.showdate, sh.starttime LIMIT 12");
            upcoming.load(rows);
            dashboard.repaint();
        } catch (SQLException ex) {
            AdminUi.error(this, ex);
        }
    }

    private JComponent stat(String label, String value, Color c) {
        Theme.Card card = new Theme.Card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(16, 20, 18, 20));
        JLabel v = Theme.label(value, 32, Font.BOLD, c);
        JLabel l = Theme.label(label, 13, Font.PLAIN, Theme.MUTED);
        card.add(v);
        card.add(l);
        return card;
    }

    private void paintChart(Graphics2D g, int w, int h) {
        if (revenue.isEmpty()) {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(Font.PLAIN, 14));
            g.drawString("No confirmed bookings yet.", 10, 40);
            g.dispose();
            return;
        }
        double max = 0;
        for (Object[] r : revenue) max = Math.max(max, Double.parseDouble(String.valueOf(r[1])));
        int rowH = Math.max(26, Math.min(40, h / revenue.size()));
        int labelW = 150, valW = 80;
        Color[] cols = {Theme.PINK, Theme.PURPLE, Theme.BLUE, Theme.GREEN, Theme.ORANGE, Theme.RED, Theme.NAVY};
        for (int i = 0; i < revenue.size(); i++) {
            int y = 12 + i * rowH;
            String title = String.valueOf(revenue.get(i)[0]);
            double v = Double.parseDouble(String.valueOf(revenue.get(i)[1]));
            g.setFont(Theme.font(Font.PLAIN, 13));
            g.setColor(Theme.NAVY);
            FontMetrics fm = g.getFontMetrics();
            String shown = title.length() > 18 ? title.substring(0, 17) + "..." : title;
            g.drawString(shown, 0, y + (rowH - 8) / 2 + fm.getAscent() / 2);
            int bw = (int) ((w - labelW - valW) * (v / max));
            g.setColor(cols[i % cols.length]);
            g.fillRoundRect(labelW, y, Math.max(4, bw), rowH - 10, 10, 10);
            g.setColor(Theme.NAVY);
            g.setFont(Theme.font(Font.BOLD, 13));
            g.drawString(Theme.money(v), labelW + Math.max(4, bw) + 8, y + (rowH - 8) / 2 + fm.getAscent() / 2);
        }
        g.dispose();
    }
}