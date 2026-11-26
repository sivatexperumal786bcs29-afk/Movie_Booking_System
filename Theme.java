package MovieBooking;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Colours, fonts and custom-painted components shared by all screens. */
final class Theme {
    private Theme() {}

    static final Color PINK = new Color(0xF84464);
    static final Color PURPLE = new Color(0x7B2FF7);
    static final Color NAVY = new Color(0x1A1A2E);
    static final Color GREEN = new Color(0x2DB84D);
    static final Color ORANGE = new Color(0xF5A623);
    static final Color BLUE = new Color(0x2F80ED);
    static final Color RED = new Color(0xE53935);
    static final Color BG = new Color(0xF5F5FA);
    static final Color LINE = new Color(0xDDDDE6);
    static final Color MUTED = new Color(0x777788);
    static final String RUPEE = "\u20B9";
    static final String STAR = "\u2605";

    static Font font(int style, int size) { return new Font("SansSerif", style, size); }
    static String money(double v) { return RUPEE + Math.round(v); }

    static Graphics2D aa(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g2;
    }

    // ------------------------------------------------------------ helpers
    static void frame(JFrame f, int w, int h) {
        Dimension s = Toolkit.getDefaultToolkit().getScreenSize();
        f.setSize(Math.min(w, s.width - 30), Math.min(h, s.height - 60));
        f.setMinimumSize(new Dimension(900, 600));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().setBackground(BG);
        if (pendingMsg != null) { String m = pendingMsg; Color col = pendingColor; pendingMsg = null; SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(f, m, col.equals(new Color(0xD92D20)) ? "Error" : "CineBook", col.equals(new Color(0xD92D20)) ? JOptionPane.ERROR_MESSAGE : JOptionPane.INFORMATION_MESSAGE)); }
    }

    static void go(JFrame current, JFrame next) {
        next.setVisible(true);
        current.dispose();
    }

    // ------------------------------------------------------------ messages (normal dialogs; only Admin > Add movie uses inline errors)
    private static String pendingMsg;
    private static Color pendingColor;

    private static void dialog(Component c, String msg, String title, int type) {
        JOptionPane.showMessageDialog(c, msg, title, type);
    }

    static void warn(Component c, String msg) { dialog(c, msg, "Warning", JOptionPane.WARNING_MESSAGE); }

    static void info(Component c, String msg) { dialog(c, msg, "CineBook", JOptionPane.INFORMATION_MESSAGE); }

    static void error(Component c, String msg) { dialog(c, msg, "Error", JOptionPane.ERROR_MESSAGE); }

    /** Message that should appear on the NEXT window (use just before Theme.go). */
    static void nextInfo(String msg) { pendingMsg = msg; pendingColor = new Color(0x1B8A37); }

    static void nextError(String msg) { pendingMsg = msg; pendingColor = new Color(0xD92D20); }

    /**
     * Shows the message as a coloured banner at the top of the window for a few seconds (click to hide).
     * It is drawn inside the window, so no dialog box opens and nothing is hidden behind a pop-up.
     */
    static void toast(Component c, String msg, Color bg) {
        JRootPane root = c == null ? null : SwingUtilities.getRootPane(c);
        if (root == null) { JOptionPane.showMessageDialog(c, msg, "CineBook", JOptionPane.PLAIN_MESSAGE); return; }
        Window win = SwingUtilities.getWindowAncestor(root);
        int total = Math.max(win == null ? 0 : win.getWidth(), root.getWidth());
        int width = Math.max(320, Math.min(760, (total > 0 ? total : 900) - 60));
        JLayeredPane layer = root.getLayeredPane();
        for (Component old : layer.getComponentsInLayer(JLayeredPane.POPUP_LAYER)) {
            if (old instanceof JComponent jc && Boolean.TRUE.equals(jc.getClientProperty("cinebook.toast"))) layer.remove(old);
        }
        JLabel text = new JLabel("<html><body style='width:" + (width - 70) + "px'>"
                + msg.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>") + "</body></html>");
        text.setFont(font(Font.BOLD, 14));
        text.setForeground(Color.WHITE);
        JPanel box = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = aa(g);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
            }
        };
        box.setOpaque(false);
        box.putClientProperty("cinebook.toast", Boolean.TRUE);
        box.setBorder(new javax.swing.border.EmptyBorder(11, 18, 11, 18));
        box.add(text, BorderLayout.CENTER);
        box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Dimension d = box.getPreferredSize();
        box.setBounds(Math.max(10, (total - width) / 2), 12, width, d.height);
        layer.add(box, JLayeredPane.POPUP_LAYER);
        layer.repaint();
        javax.swing.Timer t = new javax.swing.Timer(6000, e -> { layer.remove(box); layer.repaint(); });
        t.setRepeats(false);
        t.start();
        box.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { t.stop(); layer.remove(box); layer.repaint(); }
        });
    }

    static JLabel label(String text, int size, int style, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(style, size));
        l.setForeground(color);
        return l;
    }

    static JTextField field(int cols) {
        JTextField f = new JTextField(cols);
        style(f);
        return f;
    }

    static JPasswordField password(int cols) {
        JPasswordField f = new JPasswordField(cols);
        style(f);
        return f;
    }

    static void style(JTextComponent f) {
        f.setFont(font(Font.PLAIN, 14));
        f.setBackground(Color.WHITE);
        f.setForeground(NAVY);
        f.setCaretColor(PINK);
        f.setBorder(new CompoundBorder(new RoundBorder(LINE, 12), new EmptyBorder(8, 12, 8, 12)));
    }

    static void style(JComboBox<?> c) {
        c.setFont(font(Font.PLAIN, 14));
        c.setBackground(Color.WHITE);
        c.setForeground(NAVY);
    }

    static JLabel pill(String text, Color bg, Color fg) {
        JLabel l = new JLabel(text, SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = aa(g);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        l.setOpaque(false);
        l.setBackground(bg);
        l.setForeground(fg);
        l.setFont(font(Font.BOLD, 12));
        l.setBorder(new EmptyBorder(4, 12, 4, 12));
        return l;
    }

    static JComponent avatar(String text, int d) {
        JComponent c = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = aa(g);
                g2.setPaint(new GradientPaint(0, 0, PINK, d, d, PURPLE));
                g2.fillOval(0, 0, d - 1, d - 1);
                g2.setColor(Color.WHITE);
                g2.setFont(font(Font.BOLD, d / 3));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(text, (d - fm.stringWidth(text)) / 2, (d - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        };
        c.setPreferredSize(new Dimension(d, d));
        c.setMinimumSize(new Dimension(d, d));
        c.setMaximumSize(new Dimension(d, d));
        return c;
    }

    static String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] p = name.trim().split("\\s+");
        String s = p[0].substring(0, 1);
        if (p.length > 1) s += p[1].substring(0, 1);
        return s.toUpperCase();
    }

    // ---------------------------------------------------- borders / panels
    static class RoundBorder implements Border {
        private final Color color;
        private final int arc;
        RoundBorder(Color c, int arc) { this.color = c; this.arc = arc; }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = aa(g);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(x + 1, y + 1, w - 3, h - 3, arc, arc);
            g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c) { return new Insets(2, 2, 2, 2); }
        @Override public boolean isBorderOpaque() { return false; }
    }

    /** Diagonal two-colour gradient panel. */
    static class Gradient extends JPanel {
        private final Color a, b;
        Gradient(Color a, Color b) { this.a = a; this.b = b; }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            g2.setPaint(new GradientPaint(0, 0, a, getWidth(), getHeight(), b));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }

    /** White rounded card with a soft shadow. */
    static class Card extends JPanel {
        Card() { setOpaque(false); setBackground(Color.WHITE); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            g2.setColor(new Color(0, 0, 0, 20));
            g2.fillRoundRect(2, 4, getWidth() - 4, getHeight() - 5, 22, 22);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth() - 3, getHeight() - 4, 20, 20);
            g2.dispose();
        }
    }

    // ------------------------------------------------------------ buttons
    static class Btn extends JButton {
        private Color base;
        private boolean outline;
        private boolean hover;

        Btn(String text, Color c) { this(text, c, false); }

        Btn(String text, Color c, boolean outline) {
            super(text);
            this.base = c;
            this.outline = outline;
            setFont(font(Font.BOLD, 14));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setBorder(new EmptyBorder(11, 24, 11, 24));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        void style(Color c, boolean outline) { this.base = c; this.outline = outline; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int w = getWidth(), h = getHeight(), arc = 16;
            Color c = isEnabled() ? base : new Color(0xBDBDC8);
            if (hover && isEnabled()) {
                c = new Color(Math.min(255, c.getRed() + 22), Math.min(255, c.getGreen() + 22), Math.min(255, c.getBlue() + 22));
            }
            if (outline) {
                if (hover && isEnabled()) {
                    g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 45));
                    g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
                }
                g2.setColor(c);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);
            } else {
                g2.setColor(c);
                g2.fillRoundRect(0, 0, w, h, arc, arc);
            }
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            String t = getText();
            g2.setColor(outline ? c : Color.WHITE);
            g2.drawString(t, (w - fm.stringWidth(t)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** Toggle chip used for date selection. */
    static class Chip extends JToggleButton {
        Chip(String html) {
            super(html);
            setFont(font(Font.BOLD, 13));
            setForeground(NAVY);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setBorder(new EmptyBorder(8, 18, 8, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addItemListener(e -> setForeground(isSelected() ? Color.WHITE : NAVY));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            if (isSelected()) {
                g2.setPaint(new GradientPaint(0, 0, PINK, getWidth(), getHeight(), PURPLE));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            } else {
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.setColor(LINE);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 16, 16);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ------------------------------------------------------------ posters
    private static final Map<String, BufferedImage> CACHE = new HashMap<>();
    private static final Color[][] PALETTES = {
        {new Color(0xFF512F), new Color(0x8E0E00)}, {new Color(0x11998E), new Color(0x0B3D91)},
        {new Color(0x7B2FF7), new Color(0xF107A3)}, {new Color(0xF7971E), new Color(0xB4141E)},
        {new Color(0x2193B0), new Color(0x6DD5ED)}, {new Color(0x4E54C8), new Color(0x1A1A2E)},
        {new Color(0xDD2476), new Color(0xFF512F)}, {new Color(0x134E5E), new Color(0x71B280)},
    };

    /** Movie poster. Uses posters/<file> if it exists, otherwise draws a coloured poster. */
    static BufferedImage poster(String title, String file, int w, int h) {
        String key = title + "|" + file + "|" + w + "x" + h;
        BufferedImage cached = CACHE.get(key);
        if (cached != null) return cached;

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = aa(out.getGraphics());
        g.setClip(new RoundRectangle2D.Float(0, 0, w, h, 20, 20));
        BufferedImage real = loadPosterFile(file);
        if (real != null) {
            double s = Math.max((double) w / real.getWidth(), (double) h / real.getHeight());
            int dw = (int) (real.getWidth() * s), dh = (int) (real.getHeight() * s);
            g.drawImage(real, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
        } else {
            Color[] p = PALETTES[Math.abs(title.hashCode()) % PALETTES.length];
            g.setPaint(new GradientPaint(0, 0, p[0], w, h, p[1]));
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(255, 255, 255, 32));
            g.fillOval(-w / 3, h / 3, w, w);
            g.fillOval(w / 2, -h / 6, w, w);
            g.setColor(new Color(255, 255, 255, 60));
            g.setStroke(new BasicStroke(2f));
            g.drawOval(w / 5, h / 8, w * 3 / 5, w * 3 / 5);
            g.setPaint(new GradientPaint(0, h * 0.40f, new Color(0, 0, 0, 0), 0, h, new Color(0, 0, 0, 190)));
            g.fillRect(0, 0, w, h);
            drawWrapped(g, title.toUpperCase(), w, h);
        }
        g.dispose();
        CACHE.put(key, out);
        return out;
    }

    private static void drawWrapped(Graphics2D g, String title, int w, int h) {
        int size = Math.max(14, w / 9);
        g.setFont(font(Font.BOLD, size));
        FontMetrics fm = g.getFontMetrics();
        java.util.List<String> lines = new java.util.ArrayList<>();
        String cur = "";
        for (String word : title.split(" ")) {
            String t = cur.isEmpty() ? word : cur + " " + word;
            if (fm.stringWidth(t) > w - 24 && !cur.isEmpty()) { lines.add(cur); cur = word; } else cur = t;
        }
        lines.add(cur);
        int y = h - 28 - (lines.size() - 1) * (fm.getHeight() + 2);
        g.setColor(Color.WHITE);
        for (String l : lines) {
            g.drawString(l, (w - fm.stringWidth(l)) / 2, y);
            y += fm.getHeight() + 2;
        }
    }

    private static BufferedImage loadPosterFile(String file) {
        if (file == null || file.isBlank()) return null;
        try {
            File f = new File("posters", file);
            return f.exists() ? ImageIO.read(f) : null;
        } catch (Exception e) {
            return null;
        }
    }

    // --------------------------------------------------- theatre backdrop
    /** Painted cinema hall: curtains, curved screen and rows of seats. */
    static class Hall extends JPanel {
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int w = getWidth(), h = getHeight();
            g2.setPaint(new GradientPaint(0, 0, new Color(0x0F0A2A), 0, h, new Color(0x3B0F40)));
            g2.fillRect(0, 0, w, h);
            g2.setPaint(new RadialGradientPaint(new Point2D.Float(w / 2f, h * 0.17f), Math.max(w, h) * 0.55f,
                    new float[]{0f, 1f}, new Color[]{new Color(248, 68, 100, 125), new Color(248, 68, 100, 0)}));
            g2.fillRect(0, 0, w, h);

            // curtains
            curtain(g2, 0, w * 0.10f, h);
            curtain(g2, w - w * 0.10f, w * 0.10f, h);

            // curved screen
            float sw = w * 0.62f, sx = (w - sw) / 2f, y1 = h * 0.07f, th = h * 0.15f, bulge = h * 0.035f;
            Path2D scr = new Path2D.Float();
            scr.moveTo(sx, y1 + bulge);
            scr.quadTo(w / 2f, y1 - bulge, sx + sw, y1 + bulge);
            scr.lineTo(sx + sw, y1 + bulge + th);
            scr.quadTo(w / 2f, y1 - bulge + th, sx, y1 + bulge + th);
            scr.closePath();
            g2.setPaint(new GradientPaint(sx, y1, new Color(0x5B3FD6), sx + sw, y1 + th, new Color(0xB03FC8)));
            g2.fill(scr);

            // fade to dark in the lower half so content stays readable
            g2.setPaint(new GradientPaint(0, h * 0.45f, new Color(15, 10, 42, 0), 0, h, new Color(15, 10, 42, 190)));
            g2.fillRect(0, 0, w, h);

            // seat rows
            int rows = 6;
            for (int r = 0; r < rows; r++) {
                float sz = w * 0.026f * (1 + r * 0.30f);
                float step = sz * 1.32f;
                int count = (int) ((w * 0.78f) / step);
                float x0 = (w - count * step) / 2f;
                float y = h * 0.62f + r * (h * 0.082f);
                Random rnd = new Random(r * 977L);
                for (int c = 0; c < count; c++) {
                    float x = x0 + c * step;
                    if (Math.abs(c - count / 2f + 0.5f) < 0.6f) continue; // aisle
                    boolean taken = r >= 3 && rnd.nextFloat() < 0.16f;
                    Color body = taken ? new Color(0xF84464) : new Color(0x2E1F55);
                    g2.setColor(body);
                    g2.fill(new RoundRectangle2D.Float(x + sz * 0.1f, y, sz * 0.8f, sz * 0.78f, sz * 0.3f, sz * 0.3f));
                    g2.fill(new RoundRectangle2D.Float(x, y + sz * 0.5f, sz, sz * 0.45f, sz * 0.3f, sz * 0.3f));
                    g2.setColor(new Color(255, 255, 255, taken ? 70 : 28));
                    g2.fill(new RoundRectangle2D.Float(x + sz * 0.22f, y + sz * 0.08f, sz * 0.56f, sz * 0.14f, 4, 4));
                }
            }
            g2.dispose();
        }

        private static void curtain(Graphics2D g2, float x, float cw, int h) {
            int n = 8;
            float sw = cw / n;
            for (int i = 0; i < n; i++) {
                float xx = x + i * sw;
                Color a = i % 2 == 0 ? new Color(0x7A0F2B) : new Color(0xB3173A);
                Color b = i % 2 == 0 ? new Color(0xB3173A) : new Color(0x7A0F2B);
                g2.setPaint(new GradientPaint(xx, 0, a, xx + sw, 0, b));
                g2.fill(new Rectangle2D.Float(xx, 0, sw + 1, h));
            }
            g2.setPaint(new GradientPaint(0, 0, new Color(0x5A0B20), 0, h * 0.08f, new Color(0x5A0B20, true)));
            g2.fill(new Rectangle2D.Float(x, 0, cw, h * 0.06f));
        }
    }

    /** Left-hand banner used on login/register: hall painting with a title in the middle. */
    static JPanel hallSide(String title, String sub) {
        Hall hall = new Hall();
        hall.setLayout(new GridBagLayout());
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel logo = label("<html><span style='color:#F84464'>Cine</span>Book</html>", 22, Font.BOLD, Color.WHITE);
        JLabel t = label(title, 34, Font.BOLD, Color.WHITE);
        JLabel s = label("<html><body style='width:300px; text-align:center'>" + sub + "</body></html>", 15, Font.PLAIN, new Color(0xD7D2F0));
        for (JLabel l : new JLabel[]{logo, t, s}) {
            l.setHorizontalAlignment(SwingConstants.CENTER);
            l.setAlignmentX(Component.CENTER_ALIGNMENT);
            l.setMaximumSize(new Dimension(Integer.MAX_VALUE, l.getPreferredSize().height));
        }
        text.add(logo);
        text.add(Box.createVerticalStrut(14));
        text.add(t);
        text.add(Box.createVerticalStrut(8));
        text.add(s);
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = 0; gc.weighty = 0.34;
        hall.add(new JLabel(), gc);
        gc.gridy = 1; gc.weighty = 0;
        hall.add(text, gc);
        gc.gridy = 2; gc.weighty = 0.66;
        hall.add(new JLabel(), gc);
        return hall;
    }
}