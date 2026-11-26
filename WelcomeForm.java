package MovieBooking;

import javax.swing.*;
import java.awt.*;

/** Front page: theatre hall backdrop, poster strip, Login / Create account buttons. */
class WelcomeForm extends JFrame {
    private static final String[][] SAMPLE = {
        {"Vikram", "vikram.jpg"}, {"Leo", "leo.jpg"}, {"Jailer", "jailer.jpg"},
        {"Kaithi", "kaithi.jpg"}, {"Master", "master.jpg"}
    };

    WelcomeForm() {
        super("CineBook - Welcome");
        Theme.frame(this, 1280, 800);

        Theme.Hall hall = new Theme.Hall();
        hall.setLayout(new GridBagLayout());

        JLabel logo = Theme.label("<html><span style='color:#F84464'>Cine</span>Book</html>", 64, Font.BOLD, Color.WHITE);
        JLabel tag = Theme.label("Movies. Seats. Tickets. Book your show in a few clicks.", 18, Font.PLAIN, new Color(0xE9E4FF));
        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        tag.setAlignmentX(Component.CENTER_ALIGNMENT);
        head.add(logo);
        head.add(tag);

        JPanel posters = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 0));
        posters.setOpaque(false);
        for (int i = 0; i < SAMPLE.length; i++) {
            int w = i == 2 ? 190 : 160, h = i == 2 ? 270 : 230;
            JLabel p = new JLabel(new ImageIcon(Theme.poster(SAMPLE[i][0], SAMPLE[i][1], w, h)));
            p.setVerticalAlignment(SwingConstants.CENTER);
            posters.add(p);
        }

        Theme.Btn login = new Theme.Btn("Login", Theme.PINK);
        Theme.Btn register = new Theme.Btn("Create account", Color.WHITE, true);
        login.setFont(Theme.font(Font.BOLD, 17));
        register.setFont(Theme.font(Font.BOLD, 17));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        buttons.setOpaque(false);
        buttons.add(login);
        buttons.add(register);

        JLabel foot = Theme.label("Dynamic pricing  \u2022  Live seat map  \u2022  Easy cancellation", 14, Font.PLAIN, new Color(0xBDB5E0));

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 0; gc.weighty = 0.05; hall.add(new JLabel(), gc);
        gc.gridy = 1; gc.weighty = 0; hall.add(head, gc);
        gc.gridy = 2; gc.weighty = 0.07; hall.add(new JLabel(), gc);
        gc.gridy = 3; gc.weighty = 0; hall.add(posters, gc);
        gc.gridy = 4; gc.weighty = 0.06; hall.add(new JLabel(), gc);
        gc.gridy = 5; gc.weighty = 0; hall.add(buttons, gc);
        gc.gridy = 6; gc.weighty = 0.03; hall.add(new JLabel(), gc);
        gc.gridy = 7; gc.weighty = 0; hall.add(foot, gc);
        gc.gridy = 8; gc.weighty = 0.12; hall.add(new JLabel(), gc);

        setContentPane(hall);
        login.addActionListener(e -> Theme.go(this, new LoginForm()));
        register.addActionListener(e -> Theme.go(this, new RegisterForm()));
    }
}