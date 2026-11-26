package MovieBooking;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Movie details + date and show-time selection. */
class MovieDetailForm extends JFrame {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    private final Dao.Movie movie;
    private List<Dao.Show> shows = new ArrayList<>();
    private final JPanel times = new JPanel();

    MovieDetailForm(Dao.Movie m) {
        super(m.title() + " - CineBook");
        this.movie = m;
        Theme.frame(this, 1280, 820);
        setLayout(new BorderLayout());

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.NAVY);
        bar.setBorder(new EmptyBorder(12, 24, 12, 24));
        Theme.Btn back = new Theme.Btn("\u2190  Back to movies", Color.WHITE, true);
        back.setBorder(new EmptyBorder(8, 18, 8, 18));
        back.addActionListener(e -> Theme.go(this, new HomeForm()));
        bar.add(back, BorderLayout.WEST);
        bar.add(Theme.label("<html><span style='color:#F84464'>Cine</span>Book</html>", 24, Font.BOLD, Color.WHITE), BorderLayout.EAST);
        add(bar, BorderLayout.NORTH);

        try {
            shows = Dao.shows(m.id());
        } catch (SQLException ex) {
            Theme.error(this, "Database error:\n" + ex.getMessage());
        }

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(Theme.BG);
        content.add(hero(), BorderLayout.NORTH);
        content.add(showSection(), BorderLayout.CENTER);
        JScrollPane sp = new JScrollPane(content);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(24);
        add(sp, BorderLayout.CENTER);
    }

    private JPanel hero() {
        Theme.Gradient hero = new Theme.Gradient(Theme.NAVY, new Color(0x4B1D8F));
        hero.setLayout(new BorderLayout(34, 0));
        hero.setBorder(new EmptyBorder(30, 40, 30, 40));
        hero.add(new JLabel(new ImageIcon(Theme.poster(movie.title(), movie.poster(), 250, 355))), BorderLayout.WEST);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        JLabel title = Theme.label(movie.title(), 42, Font.BOLD, Color.WHITE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(title);
        info.add(Box.createVerticalStrut(12));

        JPanel pills = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        pills.setOpaque(false);
        pills.setAlignmentX(Component.LEFT_ALIGNMENT);
        pills.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        pills.add(Theme.pill(Theme.STAR + " " + String.format("%.1f", movie.rating()) + "/10", Theme.PINK, Color.WHITE));
        pills.add(Theme.pill(movie.cert(), Theme.ORANGE, Color.WHITE));
        pills.add(Theme.pill(movie.genre(), Theme.PURPLE, Color.WHITE));
        pills.add(Theme.pill(movie.language(), Theme.BLUE, Color.WHITE));
        pills.add(Theme.pill(movie.duration() / 60 + "h " + movie.duration() % 60 + "m", Theme.GREEN, Color.WHITE));
        info.add(pills);
        info.add(Box.createVerticalStrut(20));

        JLabel about = Theme.label("About the movie", 16, Font.BOLD, new Color(0xFFD1DA));
        about.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(about);
        info.add(Box.createVerticalStrut(6));
        JLabel desc = Theme.label("<html><body style='width:560px'>" + movie.description() + "</body></html>", 16, Font.PLAIN, new Color(0xE9E4FF));
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(desc);
        info.add(Box.createVerticalGlue());
        hero.add(info, BorderLayout.CENTER);
        return hero;
    }

    private JPanel showSection() {
        JPanel sec = new JPanel();
        sec.setOpaque(false);
        sec.setLayout(new BoxLayout(sec, BoxLayout.Y_AXIS));
        sec.setBorder(new EmptyBorder(26, 40, 30, 40));

        JLabel dl = Theme.label("Select date", 20, Font.BOLD, Theme.NAVY);
        dl.setAlignmentX(Component.LEFT_ALIGNMENT);
        sec.add(dl);
        sec.add(Box.createVerticalStrut(10));

        List<LocalDate> dates = new ArrayList<>();
        for (Dao.Show s : shows) if (!dates.contains(s.date())) dates.add(s.date());

        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        chips.setOpaque(false);
        chips.setAlignmentX(Component.LEFT_ALIGNMENT);
        ButtonGroup bg = new ButtonGroup();
        boolean first = true;
        for (LocalDate d : dates) {
            String txt = "<html><div style='text-align:center'>" + d.getDayOfWeek().toString().substring(0, 3)
                    + "<br><span style='font-size:16px'><b>" + d.getDayOfMonth() + "</b></span> "
                    + d.getMonth().toString().substring(0, 3) + "</div></html>";
            Theme.Chip c = new Theme.Chip(txt);
            bg.add(c);
            chips.add(c);
            c.addActionListener(e -> showTimes(d));
            if (first) { c.setSelected(true); first = false; }
        }
        sec.add(chips);
        sec.add(Box.createVerticalStrut(22));

        JLabel tl = Theme.label("Available shows", 20, Font.BOLD, Theme.NAVY);
        tl.setAlignmentX(Component.LEFT_ALIGNMENT);
        sec.add(tl);
        sec.add(Box.createVerticalStrut(10));

        times.setOpaque(false);
        times.setLayout(new BoxLayout(times, BoxLayout.Y_AXIS));
        times.setAlignmentX(Component.LEFT_ALIGNMENT);
        sec.add(times);

        if (dates.isEmpty()) {
            times.add(Theme.label("No upcoming shows for this movie.", 16, Font.PLAIN, Theme.MUTED));
        } else {
            showTimes(dates.get(0));
        }
        return sec;
    }

    private void showTimes(LocalDate date) {
        times.removeAll();
        Map<String, List<Dao.Show>> groups = new LinkedHashMap<>();
        for (Dao.Show s : shows) {
            if (!s.date().equals(date)) continue;
            groups.computeIfAbsent(s.theatre() + "|" + s.location() + "|" + s.screen(), k -> new ArrayList<>()).add(s);
        }
        for (Map.Entry<String, List<Dao.Show>> e : groups.entrySet()) {
            String[] k = e.getKey().split("\\|");
            Theme.Card row = new Theme.Card();
            row.setLayout(new BorderLayout(20, 0));
            row.setBorder(new EmptyBorder(16, 22, 20, 22));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel name = Theme.label("<html><b style='font-size:15px'>" + k[0] + "</b><br><span style='color:#777788'>"
                    + k[1] + "  \u2022  " + k[2] + "</span></html>", 14, Font.PLAIN, Theme.NAVY);
            name.setPreferredSize(new Dimension(300, 50));
            row.add(name, BorderLayout.WEST);

            JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
            btns.setOpaque(false);
            for (Dao.Show s : e.getValue()) {
                Theme.Btn b = new Theme.Btn(s.time().format(TIME), Theme.GREEN, true);
                b.setBorder(new EmptyBorder(9, 18, 9, 18));
                b.addActionListener(ev -> {
                    try {
                        String problem = Dao.adultError();
                        if (problem != null) { Theme.warn(this, problem); return; }
                    } catch (SQLException ex) {
                        Theme.error(this, "Database error:\n" + ex.getMessage());
                        return;
                    }
                    Theme.go(this, new SeatForm(movie, s));
                });
                btns.add(b);
            }
            row.add(btns, BorderLayout.CENTER);
            times.add(row);
            times.add(Box.createVerticalStrut(12));
        }
        if (groups.isEmpty()) times.add(Theme.label("No shows on this date.", 16, Font.PLAIN, Theme.MUTED));
        times.revalidate();
        times.repaint();
    }
}