package MovieBooking;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.SQLException;

/** Forgot password: the customer proves the account with username + registered email, then sets a new password. */
class ResetPasswordForm extends JFrame {
    private final JTextField userField = Theme.field(20);
    private final JTextField emailField = Theme.field(20);
    private final JPasswordField passField = Theme.password(20);
    private final JPasswordField confirmField = Theme.password(20);

    ResetPasswordForm() {
        super("CineBook - Reset password");
        Theme.frame(this, 1200, 760);
        setLayout(new GridLayout(1, 2));
        add(Theme.hallSide("Reset password", "Enter your username and the email you registered with, then choose a new password."));

        JPanel right = new JPanel(new GridBagLayout());
        right.setBackground(Color.WHITE);
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setPreferredSize(new Dimension(400, 560));

        JLabel title = Theme.label("Reset password", 30, Font.BOLD, Theme.NAVY);
        JLabel hint = Theme.label("<html><body style='width:380px'>" + Validate.PASSWORD_RULE + "</body></html>",
                12, Font.PLAIN, Theme.MUTED);
        Theme.Btn resetBtn = new Theme.Btn("Reset password", Theme.PINK);
        Theme.Btn backBtn = new Theme.Btn("Back to login", Theme.PURPLE, true);

        form.add(left(title));
        form.add(Box.createVerticalStrut(22));
        addField(form, "Username", userField);
        addField(form, "Registered email", emailField);
        addField(form, "New password", passField);
        addField(form, "Confirm new password", confirmField);
        form.add(left(hint));
        form.add(Box.createVerticalStrut(22));
        for (JButton b : new JButton[]{resetBtn, backBtn}) {
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        }
        form.add(resetBtn);
        form.add(Box.createVerticalStrut(12));
        form.add(backBtn);
        right.add(form);
        right.setBorder(new EmptyBorder(20, 20, 20, 20));
        add(right);

        resetBtn.addActionListener(e -> reset());
        backBtn.addActionListener(e -> Theme.go(this, new LoginForm()));
    }

    private JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private void addField(JPanel form, String label, JComponent field) {
        form.add(left(Theme.label(label, 13, Font.BOLD, Theme.NAVY)));
        form.add(Box.createVerticalStrut(6));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        form.add(left(field));
        form.add(Box.createVerticalStrut(14));
    }

    private void reset() {
        String user = userField.getText().trim();
        String email = emailField.getText().trim();
        String pass = new String(passField.getPassword());
        String confirm = new String(confirmField.getPassword());
        if (user.isEmpty() || email.isEmpty()) { Theme.warn(this, "Enter your username and registered email."); return; }
        String problem = Validate.password(pass);
        if (problem != null) { Theme.warn(this, problem); return; }
        if (!pass.equals(confirm)) { Theme.warn(this, "Passwords do not match."); return; }
        try {
            if (Dao.resetPassword(user, email, pass)) {
                Theme.nextInfo("Password changed. Please login with your new password.");
                Theme.go(this, new LoginForm());
            } else {
                Theme.error(this, "No customer account matches that username and email.");
            }
        } catch (SQLException ex) {
            Theme.error(this, "Database error:\n" + ex.getMessage());
        }
    }
}