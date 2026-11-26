package MovieBooking;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.SQLException;

class RegisterForm extends JFrame {
    private final JTextField userField = Theme.field(20);
    private final JPasswordField passField = Theme.password(20);
    private final JPasswordField confirmField = Theme.password(20);
    private final ProfilePanel profile = new ProfilePanel();

    RegisterForm() {
        super("CineBook - Create account");
        Theme.frame(this, 1280, 860);
        setLayout(new BorderLayout());

        JPanel left = Theme.hallSide("Join CineBook", "Create your account and tell us a little about yourself.");
        left.setPreferredSize(new Dimension(380, 100));
        add(left, BorderLayout.WEST);

        JPanel form = new JPanel();
        form.setBackground(Color.WHITE);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(26, 44, 26, 44));

        form.add(left(Theme.label("Create your account", 30, Font.BOLD, Theme.NAVY)));
        form.add(Box.createVerticalStrut(18));
        form.add(left(section("1  Login details", Theme.PINK)));
        form.add(Box.createVerticalStrut(6));
        form.add(left(loginRows()));
        form.add(Box.createVerticalStrut(20));
        form.add(left(section("2  Your profile", Theme.PURPLE)));
        form.add(Box.createVerticalStrut(6));
        form.add(left(profile));
        form.add(Box.createVerticalStrut(22));

        Theme.Btn registerBtn = new Theme.Btn("Create account", Theme.PINK);
        Theme.Btn backBtn = new Theme.Btn("Back to login", Theme.PURPLE, true);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        btns.setOpaque(false);
        btns.add(registerBtn);
        btns.add(backBtn);
        form.add(left(btns));

        JScrollPane sp = new JScrollPane(form);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        add(sp, BorderLayout.CENTER);

        registerBtn.addActionListener(e -> register());
        backBtn.addActionListener(e -> Theme.go(this, new LoginForm()));
    }

    private JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private JLabel section(String text, Color c) {
        return Theme.label(text, 16, Font.BOLD, c);
    }

    private JPanel loginRows() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        String[] labels = {"Username", "Password", "Confirm password"};
        JComponent[] comps = {userField, passField, confirmField};
        for (int i = 0; i < labels.length; i++) {
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridx = 0; gc.gridy = i; gc.anchor = GridBagConstraints.WEST; gc.insets = new Insets(7, 0, 7, 18);
            JLabel l = Theme.label(labels[i], 13, Font.BOLD, Theme.NAVY);
            l.setPreferredSize(new Dimension(130, 20));
            p.add(l, gc);
            gc = new GridBagConstraints();
            gc.gridx = 1; gc.gridy = i; gc.weightx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.insets = new Insets(7, 0, 7, 0);
            p.add(comps[i], gc);
        }
        GridBagConstraints hc = new GridBagConstraints();
        hc.gridx = 1; hc.gridy = labels.length; hc.anchor = GridBagConstraints.WEST; hc.insets = new Insets(0, 0, 4, 0);
        p.add(Theme.label("<html><body style='width:420px'>" + Validate.PASSWORD_RULE + "</body></html>",
                12, Font.PLAIN, Theme.MUTED), hc);
        return p;
    }

    private void register() {
        String username = userField.getText().trim();
        String password = new String(passField.getPassword());
        String confirm = new String(confirmField.getPassword());
        if (username.length() < 3) { Theme.warn(this, "Username must be at least 3 characters."); return; }
        String problem = Validate.password(password);
        if (problem != null) { Theme.warn(this, problem); return; }
        if (!password.equals(confirm)) { Theme.warn(this, "Passwords do not match."); return; }
        Dao.Profile p = profile.read();
        if (p == null) return;
        if (!Validate.isAdult(p.dob)) {
            Theme.warn(this, "Sorry, you must be " + Validate.MIN_AGE + " years or older to create an account.");
            return;
        }
        try {
            if (Dao.register(p, username, password)) {
                Theme.nextInfo("Registration successful! Please login.");
                Theme.go(this, new LoginForm());
            } else {
                Theme.warn(this, "Username already taken. Choose another.");
            }
        } catch (SQLException ex) {
            Theme.error(this, "Database error:\n" + ex.getMessage());
        }
    }
}