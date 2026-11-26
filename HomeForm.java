package MovieBooking;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Main screen after login: Movies / My Bookings / My Profile tabs. */
class HomeForm extends JFrame {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final Theme.Btn[] tabs = new Theme.Btn[3];
    private final JPanel grid = new JPanel();
    private final JPanel bookingsList = new JPanel();
    private final JTextField search = Theme.field(18);
    private final JComboBox<String> genreFilter = new JComboBox<>();
    private final ProfilePanel profile = new ProfilePanel();
    private List<Dao.Movie> movies = new ArrayList<>();

    HomeForm() { this(0); }

    /** tab: 0 = movies, 1 = my bookings, 2 = my profile */
    HomeForm(int tab) {
        super("CineBook - Home");
        Theme.frame(this, 1280, 820);
        setLayout(new BorderLayout());
        add(topBar(), BorderLayout.NORTH);

        body.setOpaque(false);
        body.add(moviesPanel(), "0");
        body.add(bookingsPanel(), "1");
        body.add(profileTab(), "2");
        add(body, BorderLayout.CENTER);
        showTab(tab);
    }

    private void showTab(int i) {
        cards.show(body, String.valueOf(i));
        for (int k = 0; k < tabs.length; k++) {
            if (k == i) tabs[k].style(Theme.PINK, false); else tabs[k].style(Color.WHITE, true);
        }
        if (i == 1) refreshBookings();
    }

    // -------------------------------------------------------------- top bar
    private JPanel topBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.NAVY);
        bar.setBorder(new EmptyBorder(12, 24, 12, 24));

        JLabel logo = Theme.label("<html><span style='color:#F84464'>Cine</span>Book</html>", 26, Font.BOLD, Color.WHITE);
        bar.add(logo, BorderLayout.WEST);

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        nav.setOpaque(false);
        String[] names = {"Movies", "My Bookings", "My Profile"};
        for (int i = 0; i < names.length; i++) {
            int idx = i;
            tabs[i] = new Theme.Btn(names[i], Color.WHITE, true);
            tabs[i].setBorder(new EmptyBorder(8, 20, 8, 20));
            tabs[i].addActionListener(e -> showTab(idx));
            nav.add(tabs[i]);
        }
        bar.add(nav, BorderLayout.CENTER);

        JPanel user = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        user.setOpaque(false);
        String uname = Dao.Session.username == null ? "guest" : Dao.Session.username;
        user.add(Theme.label(uname, 14, Font.BOLD, Color.WHITE));
        user.add(Theme.avatar(Theme.initials(uname), 38));
        Theme.Btn logout = new Theme.Btn("Logout", Theme.ORANGE);
        logout.setBorder(new EmptyBorder(8, 18, 8, 18));
        logout.addActionListener(e -> {
            Dao.Session.clear();
            Theme.go(this, new WelcomeForm());
        });
        user.add(logout);
        bar.add(user, BorderLayout.EAST);
        return bar;
    }

    // ------------------------------------------------------------ movies tab
    private JPanel moviesPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);

        Theme.Gradient banner = new Theme.Gradient(Theme.PINK, Theme.PURPLE);
        banner.setLayout(new BorderLayout());
        banner.setBorder(new EmptyBorder(22, 30, 22, 30));
        JPanel t = new JPanel();
        t.setOpaque(false);
        t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        t.add(Theme.label("Now Showing", 30, Font.BOLD, Color.WHITE));
        t.add(Theme.label("Pick a movie and book your seats", 14, Font.PLAIN, new Color(0xFFE6EC)));
        banner.add(t, BorderLayout.WEST);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        filters.setOpaque(false);
        filters.add(Theme.label("Search", 13, Font.BOLD, Color.WHITE));
        filters.add(search);
        genreFilter.addItem("All genres");
        Theme.style(genreFilter);
        filters.add(genreFilter);
        banner.add(filters, BorderLayout.EAST);
        p.add(banner, BorderLayout.NORTH);

        try {
            movies = Dao.movies();
        } catch (SQLException ex) {
            Theme.error(this, "Database error:\n" + ex.getMessage());
        }
        java.util.TreeSet<String> genres = new java.util.TreeSet<>();
        for (Dao.Movie m : movies) if (!m.genre().isEmpty()) genres.add(m.genre());
        for (String g : genres) genreFilter.addItem(g);

        grid.setOpaque(false);
        grid.setLayout(new GridLayout(0, 4, 22, 22));
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(new EmptyBorder(24, 30, 24, 30));
        wrap.add(grid, BorderLayout.NORTH);
        wrap.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                int cols = Math.max(1, (wrap.getWidth() - 60) / 250);
                if (((GridLayout) grid.getLayout()).getColumns() != cols) {
                    grid.setLayout(new GridLayout(0, cols, 22, 22));
                    grid.revalidate();
                }
            }
        });
        JScrollPane sp = new JScrollPane(wrap);
        sp.setBorder(null);
        sp.getViewport().setOpaque(false);
        sp.setOpaque(false);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getVerticalScrollBar().setUnitIncrement(24);
        p.add(sp, BorderLayout.CENTER);

        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { fillGrid(); }
            public void removeUpdate(DocumentEvent e) { fillGrid(); }
            public void changedUpdate(DocumentEvent e) { fillGrid(); }
        });
        genreFilter.addActionListener(e -> fillGrid());
        fillGrid();
        return p;
    }

    private void fillGrid() {
        grid.removeAll();
        String q = search.getText().trim().toLowerCase();
        String g = (String) genreFilter.getSelectedItem();
        for (Dao.Movie m : movies) {
            if (!q.isEmpty() && !m.title().toLowerCase().contains(q)) continue;
            if (g != null && !g.equals("All genres") && !g.equals(m.genre())) continue;
            grid.add(movieCard(m));
        }
        if (grid.getComponentCount() == 0) {
            grid.add(Theme.label("No movies found.", 16, Font.PLAIN, Theme.MUTED));
        }
        grid.revalidate();
        grid.repaint();
    }

    private JComponent movieCard(Dao.Movie m) {
        Theme.Card card = new Theme.Card();
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(12, 12, 14, 12));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel poster = new JLabel(new ImageIcon(Theme.poster(m.title(), m.poster(), 214, 300)));
        card.add(poster, BorderLayout.NORTH);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        JLabel title = Theme.label(m.title(), 17, Font.BOLD, Theme.NAVY);
        JLabel meta = Theme.label(m.language() + "  \u2022  " + m.genre() + "  \u2022  " + m.duration() + " min", 12, Font.PLAIN, Theme.MUTED);
        JLabel rating = Theme.label(Theme.STAR + " " + String.format("%.1f", m.rating()) + "/10", 13, Font.BOLD, Theme.PINK);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        rating.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(title);
        info.add(Box.createVerticalStrut(2));
        info.add(meta);
        info.add(Box.createVerticalStrut(4));
        info.add(rating);
        card.add(info, BorderLayout.CENTER);

        Theme.Btn book = new Theme.Btn("Book tickets", Theme.PINK);
        book.setBorder(new EmptyBorder(9, 16, 9, 16));
        card.add(book, BorderLayout.SOUTH);

        Runnable open = () -> Theme.go(this, new MovieDetailForm(m));
        book.addActionListener(e -> open.run());
        MouseAdapter click = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { open.run(); }
        };
        poster.addMouseListener(click);
        info.addMouseListener(click);
        return card;
    }

    // ---------------------------------------------------------- bookings tab
    private JPanel bookingsPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);

        Theme.Gradient banner = new Theme.Gradient(Theme.PURPLE, Theme.BLUE);
        banner.setLayout(new BorderLayout());
        banner.setBorder(new EmptyBorder(22, 30, 22, 30));
        JPanel t = new JPanel();
        t.setOpaque(false);
        t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        t.add(Theme.label("My Bookings", 30, Font.BOLD, Color.WHITE));
        t.add(Theme.label(policyText(), 13, Font.PLAIN, new Color(0xE6E9FF)));
        banner.add(t, BorderLayout.WEST);
        p.add(banner, BorderLayout.NORTH);

        bookingsList.setOpaque(false);
        bookingsList.setLayout(new BoxLayout(bookingsList, BoxLayout.Y_AXIS));
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(new EmptyBorder(22, 30, 22, 30));
        wrap.add(bookingsList, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(wrap);
        sp.setBorder(null);
        sp.getViewport().setOpaque(false);
        sp.setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(24);
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    private String policyText() {
        try {
            List<Dao.Policy> pol = Dao.policies();
            StringBuilder sb = new StringBuilder("Cancellation: ");
            for (int i = 0; i < pol.size(); i++) {
                Dao.Policy x = pol.get(i);
                sb.append(x.minHours()).append("h or more before show = ").append(x.percent()).append("% refund   |   ");
            }
            if (!pol.isEmpty()) sb.append("less than ").append(pol.get(pol.size() - 1).minHours()).append("h before show = cannot cancel");
            return sb.toString();
        } catch (SQLException ex) {
            return "Cancellation allowed until a few hours before the show.";
        }
    }

    private void refreshBookings() {
        bookingsList.removeAll();
        try {
            List<Dao.Booking> list = Dao.myBookings();
            List<Dao.Policy> pol = Dao.policies();
            if (list.isEmpty()) {
                bookingsList.add(Theme.label("You have no bookings yet. Go to Movies and book your first show!", 16, Font.PLAIN, Theme.MUTED));
            }
            for (Dao.Booking b : list) {
                bookingsList.add(bookingCard(b, pol));
                bookingsList.add(Box.createVerticalStrut(14));
            }
        } catch (SQLException ex) {
            Theme.error(this, "Database error:\n" + ex.getMessage());
        }
        bookingsList.revalidate();
        bookingsList.repaint();
    }

    private JComponent bookingCard(Dao.Booking b, List<Dao.Policy> pol) {
        Theme.Card card = new Theme.Card();
        card.setLayout(new BorderLayout(18, 0));
        card.setBorder(new EmptyBorder(14, 14, 18, 18));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));

        card.add(new JLabel(new ImageIcon(Theme.poster(b.movie(), b.poster(), 100, 140))), BorderLayout.WEST);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(Theme.label(b.movie() + "   #" + b.id(), 20, Font.BOLD, Theme.NAVY));
        info.add(Box.createVerticalStrut(4));
        info.add(Theme.label(b.theatre() + "  \u2022  " + b.screen(), 14, Font.PLAIN, Theme.MUTED));
        info.add(Theme.label(b.date().format(DATE) + "  \u2022  " + b.time().format(TIME), 14, Font.BOLD, Theme.NAVY));
        info.add(Box.createVerticalStrut(4));
        info.add(Theme.label("Seats: " + b.seats(), 14, Font.PLAIN, Theme.NAVY));
        info.add(Theme.label("Total: " + Theme.money(b.total()), 14, Font.BOLD, Theme.PINK));
        card.add(info, BorderLayout.CENTER);

        JPanel side = new JPanel();
        side.setOpaque(false);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        boolean future = b.when().isAfter(LocalDateTime.now());
        JLabel pill;
        JLabel note = Theme.label(" ", 12, Font.PLAIN, Theme.MUTED);
        Theme.Btn cancel = null;
        if ("CANCELLED".equalsIgnoreCase(b.status())) {
            pill = Theme.pill("CANCELLED", new Color(0xFDE3E3), Theme.RED);
            note.setText("Refund: " + Theme.money(b.refund()));
        } else if (!future) {
            pill = Theme.pill("WATCHED", new Color(0xE8E8EE), Theme.MUTED);
        } else {
            pill = Theme.pill("CONFIRMED", new Color(0xDDF5E3), new Color(0x1B8A37));
            int percent = Dao.refundPercent(b.when(), pol);
            if (percent >= 0) {
                cancel = new Theme.Btn("Cancel ticket", Theme.RED, true);
                note.setText("Refund " + percent + "% = " + Theme.money(b.total() * percent / 100.0));
                int pc = percent;
                cancel.addActionListener(e -> doCancel(b, pc));
            } else {
                cancel = new Theme.Btn("Cancel closed", Theme.RED, true);
                cancel.setEnabled(false);
                note.setText("Less than 2 hours left");
            }
            cancel.setBorder(new EmptyBorder(8, 18, 8, 18));
        }
        pill.setAlignmentX(Component.RIGHT_ALIGNMENT);
        note.setAlignmentX(Component.RIGHT_ALIGNMENT);
        side.add(pill);
        side.add(Box.createVerticalGlue());
        side.add(note);
        if (cancel != null) {
            cancel.setAlignmentX(Component.RIGHT_ALIGNMENT);
            side.add(Box.createVerticalStrut(6));
            side.add(cancel);
        }
        card.add(side, BorderLayout.EAST);
        return card;
    }

    private void doCancel(Dao.Booking b, int percent) {
        double refund = Math.round(b.total() * percent / 100.0);
        int ok = JOptionPane.showConfirmDialog(this,
                "Cancel booking #" + b.id() + " (" + b.movie() + ")?\nYou will get " + percent + "% refund = "
                        + Theme.money(refund) + ".",
                "Cancel ticket", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;
        try {
            Dao.cancel(b, percent);
            Theme.info(this, "Booking cancelled. Refund: " + Theme.money(refund));
        } catch (SQLException ex) {
            Theme.error(this, ex.getMessage());
        }
        refreshBookings();
    }

    // ----------------------------------------------------------- profile tab
    private JPanel profileTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);

        Theme.Gradient banner = new Theme.Gradient(Theme.ORANGE, Theme.PINK);
        banner.setLayout(new FlowLayout(FlowLayout.LEFT, 18, 18));
        String uname = Dao.Session.username == null ? "guest" : Dao.Session.username;
        banner.add(Theme.avatar(Theme.initials(uname), 64));
        JPanel t = new JPanel();
        t.setOpaque(false);
        t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        t.add(Theme.label("My Profile", 28, Font.BOLD, Color.WHITE));
        t.add(Theme.label("Username: " + uname, 14, Font.PLAIN, new Color(0xFFF0E0)));
        banner.add(t);
        p.add(banner, BorderLayout.NORTH);

        try {
            profile.load(Dao.loadProfile());
        } catch (SQLException ex) {
            Theme.error(this, "Database error:\n" + ex.getMessage());
        }

        Theme.Card card = new Theme.Card();
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(24, 30, 28, 30));
        card.add(profile, BorderLayout.CENTER);
        Theme.Btn save = new Theme.Btn("Save changes", Theme.GREEN);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        south.setOpaque(false);
        south.setBorder(new EmptyBorder(14, 0, 0, 0));
        south.add(save);
        card.add(south, BorderLayout.SOUTH);
        save.addActionListener(e -> {
            Dao.Profile np = profile.read();
            if (np == null) return;
            try {
                Dao.saveProfile(np);
                Theme.info(this, "Profile saved.");
            } catch (SQLException ex) {
                Theme.error(this, "Database error:\n" + ex.getMessage());
            }
        });

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.setBorder(new EmptyBorder(24, 30, 24, 30));
        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.NORTH;
        gc.weightx = 1;
        gc.weighty = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        center.add(card, gc);
        JScrollPane sp = new JScrollPane(center);
        sp.setBorder(null);
        sp.getViewport().setOpaque(false);
        sp.setOpaque(false);
        p.add(sp, BorderLayout.CENTER);
        return p;
    }
}