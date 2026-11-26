package MovieBooking;

import javax.swing.SwingUtilities;

/** Run this class. */
public class LoginApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new WelcomeForm().setVisible(true));
    }
}
