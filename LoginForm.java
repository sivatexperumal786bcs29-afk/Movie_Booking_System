package MovieBooking;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.SQLException;

class LoginForm extends JFrame {
    private final JTextField userField = Theme.field(18);
    private final JPasswordField passField = Theme.password(18);

    LoginForm() {
        super("CineBook - Login");
        Theme.frame(this, 1200, 720);
        setLayout(new GridLayout(1, 2));
        add(Theme.hallSide("Welcome back", "Login to pick your movie, choose your seats and get your ticket."));

        JPanel right = new JPanel(new GridBagLayout());
        right.setBackground(Color.WHITE);
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setPreferredSize(new Dimension(380, 470));

        JLabel title = Theme.label("Login", 32, Font.BOLD, Theme.NAVY);
        JLabel sub = Theme.label("Enter your account details", 14, Font.PLAIN, Theme.MUTED);
        Theme.Btn loginBtn = new Theme.Btn("Login", Theme.PINK);
        Theme.Btn registerBtn = new Theme.Btn("Create new account", Theme.PURPLE, true);
        Theme.Btn backBtn = new Theme.Btn("\u2190 Back to home", Theme.MUTED, true);

        for (JComponent c : new JComponent[]{title, sub, userField, passField, loginBtn, registerBtn, backBtn}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        userField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        passField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        loginBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        registerBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        backBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        form.add(title);
        form.add(Box.createVerticalStrut(4));
        form.add(sub);
        form.add(Box.createVerticalStrut(26));
        form.add(Theme.label("Username", 13, Font.BOLD, Theme.NAVY));
        form.add(Box.createVerticalStrut(6));
        form.add(userField);
        form.add(Box.createVerticalStrut(16));
        form.add(Theme.label("Password", 13, Font.BOLD, Theme.NAVY));
        form.add(Box.createVerticalStrut(6));
        form.add(passField);
        form.add(Box.createVerticalStrut(8));
        JLabel forgot = Theme.label("<html><u>Forgot password?</u></html>", 13, Font.BOLD, Theme.PURPLE);
        forgot.setAlignmentX(Component.LEFT_ALIGNMENT);
        forgot.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        forgot.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { Theme.go(LoginForm.this, new ResetPasswordForm()); }
        });
        form.add(forgot);
        form.add(Box.createVerticalStrut(20));
        form.add(loginBtn);
        form.add(Box.createVerticalStrut(12));
        form.add(registerBtn);
        form.add(Box.createVerticalStrut(12));
        form.add(backBtn);
        right.add(form);
        right.setBorder(new EmptyBorder(20, 20, 20, 20));
        add(right);

        loginBtn.addActionListener(e -> login());
        registerBtn.addActionListener(e -> Theme.go(this, new RegisterForm()));
        backBtn.addActionListener(e -> Theme.go(this, new WelcomeForm()));
        getRootPane().setDefaultButton(null);
        passField.addActionListener(e -> login());
        userField.addActionListener(e -> passField.requestFocus());
    }

    private void login() {
        String username = userField.getText().trim();
        String password = new String(passField.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            Theme.warn(this, "Enter username and password.");
            return;
        }
        try {
            if (Dao.login(username, password)) {
                if ("ADMIN".equalsIgnoreCase(Dao.Session.role)) Theme.go(this, new AdminForm());
                else if (Dao.Session.isStaff()) Theme.go(this, new StaffForm());
                else Theme.go(this, new HomeForm());
            } else {
                Theme.error(this, "Invalid username or password.");
            }
        } catch (SQLException ex) {
            Theme.error(this, "Database error:\n" + ex.getMessage());
        }
    }
}