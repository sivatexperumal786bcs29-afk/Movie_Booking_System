package MovieBooking;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Random;

/** Booking confirmed - printable-style ticket. */
class TicketDialog extends JDialog {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    TicketDialog(JFrame owner, Dao.Booking b) {
        super(owner, "Booking confirmed", true);
        setSize(480, 660);
        setLocationRelativeTo(owner);
        setResizable(false);

        Theme.Gradient bg = new Theme.Gradient(Theme.PINK, Theme.PURPLE);
        bg.setLayout(new BorderLayout(0, 14));
        bg.setBorder(new EmptyBorder(18, 22, 18, 22));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel ok = Theme.label("Booking confirmed!", 26, Font.BOLD, Color.WHITE);
        JLabel sub = Theme.label("Show this ticket at the entrance", 13, Font.PLAIN, new Color(0xFFE6EC));
        ok.setAlignmentX(Component.CENTER_ALIGNMENT);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        head.add(ok);
        head.add(sub);
        bg.add(head, BorderLayout.NORTH);

        Theme.Card ticket = new Theme.Card();
        ticket.setLayout(new BoxLayout(ticket, BoxLayout.Y_AXIS));
        ticket.setBorder(new EmptyBorder(18, 20, 24, 20));

        JPanel top = new JPanel(new BorderLayout(16, 0));
        top.setOpaque(false);
        top.add(new JLabel(new ImageIcon(Theme.poster(b.movie(), b.poster(), 100, 140))), BorderLayout.WEST);
        JPanel mt = new JPanel();
        mt.setOpaque(false);
        mt.setLayout(new BoxLayout(mt, BoxLayout.Y_AXIS));
        mt.add(Theme.label(b.movie(), 22, Font.BOLD, Theme.NAVY));
        mt.add(Box.createVerticalStrut(6));
        mt.add(Theme.label(b.theatre(), 14, Font.PLAIN, Theme.MUTED));
        mt.add(Theme.label(b.screen(), 14, Font.PLAIN, Theme.MUTED));
        mt.add(Box.createVerticalStrut(8));
        mt.add(Theme.pill("CONFIRMED", new Color(0xDDF5E3), new Color(0x1B8A37)));
        top.add(mt, BorderLayout.CENTER);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        ticket.add(top);
        ticket.add(Box.createVerticalStrut(14));

        JComponent dash = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.aa(g);
                g2.setColor(Theme.LINE);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{8f, 6f}, 0f));
                g2.drawLine(0, 6, getWidth(), 6);
                g2.dispose();
            }
        };
        dash.setPreferredSize(new Dimension(100, 12));
        dash.setMaximumSize(new Dimension(Integer.MAX_VALUE, 12));
        dash.setAlignmentX(Component.LEFT_ALIGNMENT);
        ticket.add(dash);
        ticket.add(Box.createVerticalStrut(8));

        addRow(ticket, "Date", b.date().format(DATE));
        addRow(ticket, "Time", b.time().format(TIME));
        addRow(ticket, "Seats", b.seats());
        addRow(ticket, "Booking ID", "#" + b.id());
        addRow(ticket, "Total paid", Theme.money(b.total()));
        ticket.add(Box.createVerticalStrut(14));

        final long seed = b.id();
        JComponent bar = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.aa(g);
                Random r = new Random(seed * 7919);
                g2.setColor(Theme.NAVY);
                int x = 0;
                while (x < getWidth()) {
                    int w = 1 + r.nextInt(4);
                    if (r.nextBoolean()) g2.fillRect(x, 0, w, getHeight());
                    x += w + 1 + r.nextInt(3);
                }
                g2.dispose();
            }
        };
        bar.setPreferredSize(new Dimension(100, 54));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        ticket.add(bar);
        bg.add(ticket, BorderLayout.CENTER);

        Theme.Btn done = new Theme.Btn("View my bookings", Color.WHITE, true);
        done.addActionListener(e -> dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER));
        south.setOpaque(false);
        south.add(done);
        bg.add(south, BorderLayout.SOUTH);
        setContentPane(bg);
    }

    private void addRow(JPanel p, String k, String v) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(Theme.label(k, 14, Font.PLAIN, Theme.MUTED), BorderLayout.WEST);
        row.add(Theme.label(v, 15, Font.BOLD, k.equals("Total paid") ? Theme.PINK : Theme.NAVY), BorderLayout.EAST);
        p.add(row);
    }
}

