package MovieBooking;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Theatre seat map with live availability and dynamic prices. */
class SeatForm extends JFrame {
    /** CUSTOMER = online booking, COUNTER = staff sells at the counter, CONTROL = staff blocks/unblocks seats. */
    enum Mode { CUSTOMER, COUNTER, CONTROL }

    private static final int MAX_SEATS = 8;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE, dd MMM  \u2022  hh:mm a", Locale.ENGLISH);

    private final Dao.Movie movie;
    private final Dao.Show show;
    private List<Dao.Seat> seats = new ArrayList<>();
    private final List<Dao.Seat> chosen = new ArrayList<>();
    private double factor = 1.0;
    private int want;   // seats the customer asked for (1..MAX_SEATS), 0 until chosen
    private final Mode mode;

    private final JLabel selLabel = Theme.label("No seats selected", 15, Font.BOLD, Theme.NAVY);
    private final JLabel totalLabel = Theme.label(Theme.money(0), 22, Font.BOLD, Theme.PINK);
    private final Theme.Btn pay = new Theme.Btn("Select seats", Theme.GREEN);
    private final Theme.Btn countBtn = new Theme.Btn("Seats: -", Theme.PURPLE, true);
    private final Theme.Btn blockBtn = new Theme.Btn("Block selected", Theme.RED);
    private final Theme.Btn unblockBtn = new Theme.Btn("Unblock selected", Theme.GREEN);

    SeatForm(Dao.Movie m, Dao.Show s) { this(m, s, 0, Mode.CUSTOMER); }

    SeatForm(Dao.Movie m, Dao.Show s, int count) { this(m, s, count, Mode.CUSTOMER); }

    /** count = number of seats already chosen (0 asks the customer when the window opens). */
    SeatForm(Dao.Movie m, Dao.Show s, int count, Mode mode) {
        super((mode == Mode.CONTROL ? "Seat control - " : "Choose seats - ") + m.title());
        this.movie = m;
        this.show = s;
        this.want = count;
        this.mode = mode;
        Theme.frame(this, 1280, 860);
        setLayout(new BorderLayout());

        try {
            seats = Dao.seats(s.id());
        } catch (SQLException ex) {
            Theme.error(this, "Database error:\n" + ex.getMessage());
        }
        long sold = seats.stream().filter(x -> !x.available() && !x.blocked()).count();
        long usable = seats.stream().filter(x -> !x.blocked()).count();
        double ratio = usable == 0 ? 0 : (double) sold / usable;
        factor = Dao.demandFactor(ratio);

        add(topBar(ratio), BorderLayout.NORTH);
        add(seatArea(), BorderLayout.CENTER);
        add(bottomBar(), BorderLayout.SOUTH);
        refreshTotals();

        if (want == 0 && !seats.isEmpty() && mode != Mode.CONTROL) {
            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowOpened(java.awt.event.WindowEvent e) {
                    SwingUtilities.invokeLater(() -> {
                        int n = askCount(2);
                        if (n == 0) { goBack(); return; }
                        want = n;
                        refreshTotals();
                    });
                }
            });
        }
    }

    // ------------------------------------------------- how many seats dialog
    /** Shows 1..MAX_SEATS buttons. Returns the chosen number, or 0 when cancelled. */
    private int askCount(int preselect) {
        JDialog d = new JDialog(this, "How many seats?", true);
        int[] result = {0};
        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setBackground(Color.WHITE);
        body.setBorder(new EmptyBorder(22, 28, 20, 28));
        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel t = Theme.label("How many seats do you want?", 22, Font.BOLD, Theme.NAVY);
        JLabel sub = Theme.label(movie.title() + "  \u2022  you can book up to " + MAX_SEATS + " seats at once",
                13, Font.PLAIN, Theme.MUTED);
        t.setAlignmentX(Component.CENTER_ALIGNMENT);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        head.add(t);
        head.add(Box.createVerticalStrut(4));
        head.add(sub);
        body.add(head, BorderLayout.NORTH);

        JPanel nums = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 6));
        nums.setOpaque(false);
        for (int i = 1; i <= MAX_SEATS; i++) {
            int n = i;
            Theme.Btn b = new Theme.Btn(String.valueOf(i), i == preselect ? Theme.PINK : Theme.PURPLE, i != preselect);
            b.setFont(Theme.font(Font.BOLD, 18));
            b.setBorder(new EmptyBorder(10, 0, 10, 0));
            b.setPreferredSize(new Dimension(52, 52));
            b.addActionListener(e -> { result[0] = n; d.dispose(); });
            nums.add(b);
        }
        body.add(nums, BorderLayout.CENTER);

        Theme.Btn cancel = new Theme.Btn("Cancel", Theme.MUTED, true);
        cancel.setBorder(new EmptyBorder(8, 22, 8, 22));
        cancel.addActionListener(e -> d.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        south.setOpaque(false);
        south.add(cancel);
        body.add(south, BorderLayout.SOUTH);

        d.setContentPane(body);
        d.pack();
        d.setResizable(false);
        d.setLocationRelativeTo(this);
        d.setVisible(true);
        return result[0];
    }

    private void goBack() {
        if (mode == Mode.CUSTOMER) Theme.go(this, new MovieDetailForm(movie));
        else Theme.go(this, new StaffForm());
    }

    // -------------------------------------------------------------- top bar
    private JPanel topBar(double ratio) {
        Theme.Gradient bar = new Theme.Gradient(Theme.NAVY, new Color(0x4B1D8F));
        bar.setLayout(new BorderLayout());
        bar.setBorder(new EmptyBorder(12, 24, 12, 24));
        Theme.Btn back = new Theme.Btn("\u2190  Back", Color.WHITE, true);
        back.setBorder(new EmptyBorder(8, 18, 8, 18));
        back.addActionListener(e -> goBack());
        bar.add(back, BorderLayout.WEST);

        JPanel t = new JPanel();
        t.setOpaque(false);
        t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        JLabel title = Theme.label(movie.title(), 22, Font.BOLD, Color.WHITE);
        JLabel sub = Theme.label(show.theatre() + ", " + show.location() + "  \u2022  " + show.screen()
                + "  \u2022  " + show.when().format(WHEN), 13, Font.PLAIN, new Color(0xD7D2F0));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        t.add(title);
        t.add(sub);
        bar.add(t, BorderLayout.CENTER);

        String d; Color c;
        if (factor >= 1.25) { d = "Almost full  +25%"; c = Theme.RED; }
        else if (factor >= 1.10) { d = "Filling fast  +10%"; c = Theme.ORANGE; }
        else { d = "Normal demand"; c = Theme.GREEN; }
        JLabel pill = Theme.pill(d + "  (" + Math.round(ratio * 100) + "% sold)", c, Color.WHITE);
        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 6));
        east.setOpaque(false);
        east.add(pill);
        bar.add(east, BorderLayout.EAST);
        return bar;
    }

    // ------------------------------------------------------------ seat area
    private JComponent seatArea() {
        JPanel area = new JPanel();
        area.setBackground(Color.WHITE);
        area.setLayout(new BoxLayout(area, BoxLayout.Y_AXIS));
        area.setBorder(new EmptyBorder(14, 20, 14, 20));

        JComponent screen = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.aa(g);
                int w = getWidth();
                int sw = Math.min(w - 40, 620), x = (w - sw) / 2;
                Path2D p = new Path2D.Float();
                p.moveTo(x, 34);
                p.quadTo(w / 2f, 6, x + sw, 34);
                p.lineTo(x + sw, 44);
                p.quadTo(w / 2f, 16, x, 44);
                p.closePath();
                g2.setPaint(new GradientPaint(x, 0, Theme.PINK, x + sw, 0, Theme.PURPLE));
                g2.fill(p);
                g2.setColor(Theme.MUTED);
                g2.setFont(Theme.font(Font.PLAIN, 11));
                String t = "ALL EYES THIS WAY - SCREEN";
                g2.drawString(t, (w - g2.getFontMetrics().stringWidth(t)) / 2, 78);
                g2.dispose();
            }
        };
        screen.setPreferredSize(new Dimension(700, 88));
        screen.setMaximumSize(new Dimension(Integer.MAX_VALUE, 88));
        screen.setAlignmentX(Component.CENTER_ALIGNMENT);
        area.add(screen);

        Map<String, List<Dao.Seat>> rows = new LinkedHashMap<>();
        for (Dao.Seat s : seats) rows.computeIfAbsent(s.row(), k -> new ArrayList<>()).add(s);

        String lastCat = "";
        for (Map.Entry<String, List<Dao.Seat>> e : rows.entrySet()) {
            Dao.Seat first = e.getValue().get(0);
            if (!first.category().equals(lastCat)) {
                lastCat = first.category();
                Color cc = catColor(lastCat);
                JLabel h = Theme.label(lastCat.toUpperCase() + "   " + Theme.money(Math.round(first.price() * factor)),
                        12, Font.BOLD, cc);
                h.setAlignmentX(Component.CENTER_ALIGNMENT);
                area.add(Box.createVerticalStrut(8));
                area.add(h);
                area.add(Box.createVerticalStrut(4));
            }
            area.add(rowPanel(e.getKey(), e.getValue()));
        }
        if (seats.isEmpty()) {
            area.add(Box.createVerticalStrut(40));
            JLabel none = Theme.label("No seats found for this show. Run the upgrade SQL first.", 16, Font.PLAIN, Theme.MUTED);
            none.setAlignmentX(Component.CENTER_ALIGNMENT);
            area.add(none);
        }

        JScrollPane sp = new JScrollPane(area);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(20);
        return sp;
    }

    private JPanel rowPanel(String letter, List<Dao.Seat> row) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        p.add(Theme.label(letter, 12, Font.BOLD, Theme.MUTED));
        p.add(Box.createHorizontalStrut(12));
        for (int i = 0; i < row.size(); i++) {
            if (row.get(i).gapBefore()) p.add(Box.createHorizontalStrut(34));
            p.add(new SeatBtn(row.get(i)));
            p.add(Box.createHorizontalStrut(5));
        }
        p.add(Box.createHorizontalStrut(7));
        p.add(Theme.label(letter, 12, Font.BOLD, Theme.MUTED));
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 2));
        wrap.setOpaque(false);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        wrap.add(p);
        return wrap;
    }

    private static final Color BLOCKED = new Color(0x3A3A4A);

    private Color catColor(String cat) {
        return switch (cat) {
            case "Premium" -> Theme.PURPLE;
            case "Front" -> Theme.ORANGE;
            default -> Theme.GREEN;
        };
    }

    // ----------------------------------------------------------- bottom bar
    private JPanel bottomBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(0xF5F5FA));
        bar.setBorder(new EmptyBorder(12, 28, 14, 28));

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        legend.setOpaque(false);
        legend.add(legendItem("Front", Theme.ORANGE));
        legend.add(legendItem("Standard", Theme.GREEN));
        legend.add(legendItem("Premium", Theme.PURPLE));
        legend.add(legendItem("Selected", Theme.PINK));
        legend.add(legendItem("Sold", new Color(0xC9C9D3)));
        if (mode == Mode.CONTROL) legend.add(legendItem("Blocked", BLOCKED));

        JPanel mid = new JPanel();
        mid.setOpaque(false);
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        legend.setAlignmentX(Component.LEFT_ALIGNMENT);
        selLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mid.add(legend);
        mid.add(Box.createVerticalStrut(8));
        mid.add(selLabel);
        bar.add(mid, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 4));
        right.setOpaque(false);
        countBtn.setBorder(new EmptyBorder(8, 16, 8, 16));
        countBtn.addActionListener(e -> {
            int n = askCount(want == 0 ? 2 : want);
            if (n != 0 && n != want) Theme.go(this, new SeatForm(movie, show, n, mode));
        });
        if (mode == Mode.CONTROL) {
            blockBtn.setBorder(new EmptyBorder(10, 18, 10, 18));
            unblockBtn.setBorder(new EmptyBorder(10, 18, 10, 18));
            right.add(blockBtn);
            right.add(unblockBtn);
            blockBtn.addActionListener(e -> applyBlock(true));
            unblockBtn.addActionListener(e -> applyBlock(false));
        } else {
            right.add(countBtn);
            right.add(totalLabel);
            pay.setFont(Theme.font(Font.BOLD, 16));
            right.add(pay);
        }
        bar.add(right, BorderLayout.EAST);

        pay.addActionListener(e -> pay());
        return bar;
    }

    private JComponent legendItem(String text, Color c) {
        JLabel l = Theme.label(text, 12, Font.PLAIN, Theme.NAVY);
        l.setIcon(new Icon() {
            public void paintIcon(Component comp, Graphics g, int x, int y) {
                Graphics2D g2 = Theme.aa(g);
                g2.setColor(c);
                g2.fillRoundRect(x, y, 14, 14, 5, 5);
                g2.dispose();
            }
            public int getIconWidth() { return 14; }
            public int getIconHeight() { return 14; }
        });
        l.setIconTextGap(6);
        return l;
    }

    private long priceOf(Dao.Seat s) { return Math.round(s.price() * factor); }

    /** Staff seat control: block the chosen free seats, or release the chosen blocked seats. */
    private void applyBlock(boolean block) {
        List<Integer> ids = new ArrayList<>();
        for (Dao.Seat s : chosen) if (block ? s.available() : s.blocked()) ids.add(s.showSeatId());
        if (ids.isEmpty()) {
            Theme.warn(this, block ? "Select at least one free seat to block." : "Select at least one blocked seat to unblock.");
            return;
        }
        try {
            int n = StaffDao.setBlocked(show.id(), ids, block);
            Theme.nextInfo(n + (block ? " seat(s) blocked." : " seat(s) released."));
        } catch (SQLException ex) {
            Theme.nextError(ex.getMessage());
        }
        Theme.go(this, new SeatForm(movie, show, 0, Mode.CONTROL));
    }

    private void refreshTotals() {
        if (mode == Mode.CONTROL) {
            List<String> n = new ArrayList<>();
            for (Dao.Seat s : chosen) n.add(s.label());
            selLabel.setText(chosen.isEmpty() ? "Click seats to select them (free seats can be blocked, dark seats released)"
                    : chosen.size() + " selected: " + String.join(", ", n));
            blockBtn.setEnabled(!chosen.isEmpty());
            unblockBtn.setEnabled(!chosen.isEmpty());
            return;
        }
        long total = 0;
        List<String> names = new ArrayList<>();
        for (Dao.Seat s : chosen) { total += priceOf(s); names.add(s.label()); }
        countBtn.setText(want == 0 ? "Seats: -" : "Seats: " + want + "  (change)");
        int left = want - chosen.size();
        if (want == 0) {
            selLabel.setText("Choose how many seats you want");
        } else if (chosen.isEmpty()) {
            selLabel.setText("Select " + want + (want == 1 ? " seat" : " seats") + " on the map");
        } else {
            selLabel.setText(chosen.size() + " of " + want + " selected: " + String.join(", ", names)
                    + (left > 0 ? "   (" + left + " more to choose)" : ""));
        }
        totalLabel.setText(Theme.money(total));
        pay.setEnabled(want > 0 && left == 0);
        pay.setText(want == 0 ? "Select seats" : left > 0 ? "Select " + left + " more" : "Pay " + Theme.money(total));
    }

    private void pay() {
        List<String> names = new ArrayList<>();
        long total = 0;
        for (Dao.Seat s : chosen) { names.add(s.label()); total += priceOf(s); }
        int ok = JOptionPane.showConfirmDialog(this,
                (mode == Mode.COUNTER ? "Cash received? Confirm " : "Confirm payment of ") + Theme.money(total)
                        + " for seats " + String.join(", ", names) + "?",
                "Confirm booking", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;
        try {
            List<Integer> ids = new ArrayList<>();
            for (Dao.Seat s : chosen) ids.add(s.showSeatId());
            int bookingId = Dao.book(show.id(), ids);
            Dao.Booking b = Dao.booking(bookingId);
            new TicketDialog(this, b).setVisible(true);
            if (mode == Mode.COUNTER) Theme.go(this, new StaffForm());
            else Theme.go(this, new HomeForm(1));
        } catch (SQLException ex) {
            Theme.nextError(ex.getMessage());
            Theme.go(this, new SeatForm(movie, show, want, mode));
        }
    }

    // ------------------------------------------------------------ seat button
    private class SeatBtn extends JComponent {
        private final Dao.Seat seat;
        private boolean on;

        SeatBtn(Dao.Seat seat) {
            this.seat = seat;
            Dimension d = new Dimension(34, 32);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
            setToolTipText(seat.label() + "  " + seat.category() + "  " + Theme.money(priceOf(seat))
                    + (seat.available() ? "" : seat.blocked() ? "  (blocked)" : "  (sold)"));
            if (seat.available() || (mode == Mode.CONTROL && seat.blocked())) setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { toggle(); }
            });
        }

        private void toggle() {
            if (mode == Mode.CONTROL) {
                if (!seat.available() && !seat.blocked()) return;   // sold seats cannot be changed here
                on = !on;
                if (on) chosen.add(seat); else chosen.remove(seat);
                repaint();
                refreshTotals();
                return;
            }
            if (!seat.available()) return;
            if (want == 0) return;
            if (!on && chosen.size() >= want) {
                Theme.warn(SeatForm.this, "You chose " + want + (want == 1 ? " seat" : " seats")
                        + ". Unselect a seat first, or use the Seats button to change the number.");
                return;
            }
            on = !on;
            if (on) chosen.add(seat); else chosen.remove(seat);
            repaint();
            refreshTotals();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.aa(g);
            Color cat = catColor(seat.category());
            Color fill, line, text;
            if (mode == Mode.CONTROL && seat.blocked()) {
                fill = on ? Theme.PINK : BLOCKED; line = fill; text = Color.WHITE;
            }
            else if (!seat.available()) { fill = new Color(0xDADAE2); line = fill; text = new Color(0xA0A0AC); }
            else if (on) { fill = Theme.PINK; line = Theme.PINK; text = Color.WHITE; }
            else { fill = Color.WHITE; line = cat; text = cat; }
            RoundRectangle2D back = new RoundRectangle2D.Float(5, 2, 24, 18, 9, 9);
            RoundRectangle2D seatBase = new RoundRectangle2D.Float(2, 15, 30, 14, 9, 9);
            g2.setColor(fill);
            g2.fill(back);
            g2.fill(seatBase);
            g2.setColor(line);
            g2.setStroke(new BasicStroke(1.6f));
            g2.draw(back);
            g2.draw(seatBase);
            g2.setColor(fill);
            g2.fill(new RoundRectangle2D.Float(6, 14, 22, 5, 3, 3));
            g2.setColor(text);
            g2.setFont(Theme.font(Font.BOLD, 10));
            String n = String.valueOf(seat.number());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(n, (34 - fm.stringWidth(n)) / 2, 16);
            g2.dispose();
        }
    }
}

