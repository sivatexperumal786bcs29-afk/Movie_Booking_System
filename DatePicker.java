package MovieBooking;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** A date field that opens a calendar (month and year selectors plus a day grid). */
class DatePicker extends JPanel {
    private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December"};
    private static final String[] DAYS = {"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"};

    private final LocalDate min, max, startView;
    private LocalDate value;
    private final JLabel text = Theme.label("Select date", 14, Font.PLAIN, Theme.MUTED);

    /** Dates outside min..max cannot be picked. startView is the date the calendar opens on. */
    DatePicker(LocalDate min, LocalDate max, LocalDate startView) {
        this.min = min;
        this.max = max;
        this.startView = startView;
        setBackground(Color.WHITE);
        setLayout(new BorderLayout(10, 0));
        setBorder(new CompoundBorder(new Theme.RoundBorder(Theme.LINE, 12), new EmptyBorder(8, 12, 8, 12)));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(230, 42));

        JComponent icon = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.aa(g);
                g2.setColor(Theme.PINK);
                g2.fillRoundRect(0, 3, 18, 16, 5, 5);
                g2.setColor(Color.WHITE);
                g2.fillRect(2, 8, 14, 9);
                g2.setColor(Theme.PINK);
                for (int r = 0; r < 2; r++) for (int c = 0; c < 3; c++) g2.fillRect(3 + c * 5, 9 + r * 4, 3, 2);
                g2.fillRect(4, 0, 2, 5);
                g2.fillRect(12, 0, 2, 5);
                g2.dispose();
            }
        };
        icon.setPreferredSize(new Dimension(20, 20));
        add(text, BorderLayout.CENTER);
        add(icon, BorderLayout.EAST);

        MouseAdapter click = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { open(); }
        };
        addMouseListener(click);
        text.addMouseListener(click);
        icon.addMouseListener(click);
    }

    LocalDate getDate() { return value; }

    void setDate(LocalDate d) {
        value = d;
        if (d == null) {
            text.setText("Select date");
            text.setForeground(Theme.MUTED);
        } else {
            text.setText(d.format(SHOWN));
            text.setForeground(Theme.NAVY);
        }
    }

    // ------------------------------------------------------------- popup
    private void open() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog d = new JDialog(owner, "Select date", Dialog.ModalityType.APPLICATION_MODAL);
        YearMonth[] shown = {YearMonth.from(value != null ? value : startView)};
        boolean[] busy = {false};

        JComboBox<String> mc = new JComboBox<>(MONTHS);
        JComboBox<Integer> yc = new JComboBox<>();
        for (int y = max.getYear(); y >= min.getYear(); y--) yc.addItem(y);
        Theme.style(mc);
        Theme.style(yc);
        JPanel grid = new JPanel(new GridLayout(7, 7, 4, 4));
        grid.setOpaque(false);

        Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            YearMonth ym = shown[0];
            busy[0] = true;
            mc.setSelectedIndex(ym.getMonthValue() - 1);
            yc.setSelectedItem(ym.getYear());
            busy[0] = false;
            grid.removeAll();
            for (String s : DAYS) {
                JLabel h = Theme.label(s, 12, Font.BOLD, Theme.MUTED);
                h.setHorizontalAlignment(SwingConstants.CENTER);
                grid.add(h);
            }
            int lead = ym.atDay(1).getDayOfWeek().getValue() % 7;
            int cells = 0;
            for (int i = 0; i < lead; i++, cells++) grid.add(new JLabel());
            LocalDate today = LocalDate.now();
            for (int day = 1; day <= ym.lengthOfMonth(); day++, cells++) {
                LocalDate dt = ym.atDay(day);
                boolean allowed = !dt.isBefore(min) && !dt.isAfter(max);
                DayBtn b = new DayBtn(day, dt.equals(value), dt.equals(today), allowed);
                b.addActionListener(e -> { setDate(dt); d.dispose(); });
                grid.add(b);
            }
            for (; cells < 42; cells++) grid.add(new JLabel());
            grid.revalidate();
            grid.repaint();
        };

        Runnable fromCombos = () -> {
            if (busy[0] || yc.getSelectedItem() == null) return;
            shown[0] = clamp(YearMonth.of((Integer) yc.getSelectedItem(), mc.getSelectedIndex() + 1));
            fill[0].run();
        };
        mc.addActionListener(e -> fromCombos.run());
        yc.addActionListener(e -> fromCombos.run());

        Theme.Btn prev = new Theme.Btn("<", Theme.PURPLE, true);
        Theme.Btn next = new Theme.Btn(">", Theme.PURPLE, true);
        prev.setBorder(new EmptyBorder(6, 14, 6, 14));
        next.setBorder(new EmptyBorder(6, 14, 6, 14));
        prev.addActionListener(e -> { shown[0] = clamp(shown[0].minusMonths(1)); fill[0].run(); });
        next.addActionListener(e -> { shown[0] = clamp(shown[0].plusMonths(1)); fill[0].run(); });

        JPanel mid = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        mid.setOpaque(false);
        mid.add(mc);
        mid.add(yc);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(prev, BorderLayout.WEST);
        top.add(mid, BorderLayout.CENTER);
        top.add(next, BorderLayout.EAST);

        Theme.Btn cancel = new Theme.Btn("Cancel", Theme.MUTED, true);
        cancel.setBorder(new EmptyBorder(6, 18, 6, 18));
        cancel.addActionListener(e -> d.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        south.setOpaque(false);
        south.add(cancel);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setBackground(Color.WHITE);
        body.setBorder(new EmptyBorder(16, 18, 16, 18));
        body.add(top, BorderLayout.NORTH);
        body.add(grid, BorderLayout.CENTER);
        body.add(south, BorderLayout.SOUTH);
        d.setContentPane(body);
        fill[0].run();
        d.pack();
        d.setResizable(false);
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    private YearMonth clamp(YearMonth ym) {
        YearMonth lo = YearMonth.from(min), hi = YearMonth.from(max);
        if (ym.isBefore(lo)) return lo;
        if (ym.isAfter(hi)) return hi;
        return ym;
    }

    private static class DayBtn extends JButton {
        private final boolean selected, today;

        DayBtn(int day, boolean selected, boolean today, boolean allowed) {
            super(String.valueOf(day));
            this.selected = selected;
            this.today = today;
            setEnabled(allowed);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setPreferredSize(new Dimension(40, 34));
            if (allowed) setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.aa(g);
            int s = Math.min(getWidth(), getHeight()) - 2;
            int x = (getWidth() - s) / 2, y = (getHeight() - s) / 2;
            Color fg = isEnabled() ? Theme.NAVY : new Color(0xBBBBC8);
            if (selected) {
                g2.setColor(Theme.PINK);
                g2.fillOval(x, y, s, s);
                fg = Color.WHITE;
            } else if (today) {
                g2.setColor(Theme.PURPLE);
                g2.setStroke(new BasicStroke(1.6f));
                g2.drawOval(x, y, s, s);
            }
            g2.setColor(fg);
            g2.setFont(Theme.font(selected ? Font.BOLD : Font.PLAIN, 13));
            FontMetrics fm = g2.getFontMetrics();
            String t = getText();
            g2.drawString(t, (getWidth() - fm.stringWidth(t)) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }
}

