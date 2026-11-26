package MovieBooking;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Profile fields (name, DOB, hobbies ...) used by the Register screen and the My Profile tab. */
class ProfilePanel extends JPanel {
    static final String[] HOBBIES = {"Movies", "Music", "Sports", "Travel", "Reading", "Gaming", "Cooking", "Dance"};
    static final String[] GENRES = {"Action", "Comedy", "Drama", "Thriller", "Romance", "Historical", "Family", "Sci-Fi"};
    static final String[] LANGS = {"Tamil", "English", "Hindi", "Telugu", "Malayalam", "Kannada"};
    static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December"};

    final JTextField name = Theme.field(20);
    final JTextField phone = Theme.field(20);
    final JTextField email = Theme.field(20);
    final JTextField city = Theme.field(20);
    final DatePicker dob = new DatePicker(LocalDate.of(1930, 1, 1), LocalDate.now().minusDays(1),
            LocalDate.now().minusYears(25));
    final JComboBox<String> genre = new JComboBox<>(GENRES);
    final JComboBox<String> lang = new JComboBox<>(LANGS);
    final JRadioButton male = new JRadioButton("Male");
    final JRadioButton female = new JRadioButton("Female");
    final JRadioButton other = new JRadioButton("Other");
    final JCheckBox[] hobbyBoxes = new JCheckBox[HOBBIES.length];
    private int row = 0;

    ProfilePanel() {
        setOpaque(false);
        setLayout(new GridBagLayout());

        for (JComboBox<?> c : new JComboBox<?>[]{genre, lang}) Theme.style(c);

        ButtonGroup bg = new ButtonGroup();
        JPanel g = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        g.setOpaque(false);
        for (JRadioButton r : new JRadioButton[]{male, female, other}) {
            r.setOpaque(false);
            r.setFont(Theme.font(Font.PLAIN, 14));
            r.setForeground(Theme.NAVY);
            bg.add(r);
            g.add(r);
        }

        JPanel hobbies = new JPanel(new GridLayout(2, 4, 10, 4));
        hobbies.setOpaque(false);
        for (int i = 0; i < HOBBIES.length; i++) {
            hobbyBoxes[i] = new JCheckBox(HOBBIES[i]);
            hobbyBoxes[i].setOpaque(false);
            hobbyBoxes[i].setFont(Theme.font(Font.PLAIN, 14));
            hobbyBoxes[i].setForeground(Theme.NAVY);
            hobbies.add(hobbyBoxes[i]);
        }

        JPanel dobRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        dobRow.setOpaque(false);
        dobRow.add(dob);
        dobRow.add(Theme.label("Tickets can be booked only at age " + Validate.MIN_AGE + "+", 12, Font.PLAIN, Theme.MUTED));

        addRow("Full name", name);
        addRow("Phone number", phone);
        addRow("Email", email);
        addRow("Date of birth", dobRow);
        addRow("Gender", g);
        addRow("City", city);
        addRow("Hobbies", hobbies);
        addRow("Favourite genre", genre);
        addRow("Preferred language", lang);
    }

    private void addRow(String label, JComponent c) {
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = row;
        gc.anchor = GridBagConstraints.WEST;
        gc.insets = new Insets(7, 0, 7, 18);
        add(Theme.label(label, 13, Font.BOLD, Theme.NAVY), gc);
        gc = new GridBagConstraints();
        gc.gridx = 1;
        gc.gridy = row;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(7, 0, 7, 0);
        add(c, gc);
        row++;
    }

    /** Validates the form. Returns null (after showing a message) when something is wrong. */
    Dao.Profile read() {
        String n = name.getText().trim(), ph = phone.getText().trim(), em = email.getText().trim();
        if (n.isEmpty() || ph.isEmpty() || em.isEmpty() || city.getText().trim().isEmpty()) {
            Theme.warn(this, "Please fill name, phone, email and city.");
            return null;
        }
        if (!ph.matches("\\d{10}")) { Theme.warn(this, "Phone must be 10 digits."); return null; }
        if (!em.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) { Theme.warn(this, "Enter a valid email."); return null; }
        LocalDate birth = dob.getDate();
        if (birth == null) {
            Theme.warn(this, "Select your date of birth from the calendar.");
            return null;
        }
        if (!male.isSelected() && !female.isSelected() && !other.isSelected()) {
            Theme.warn(this, "Select your gender.");
            return null;
        }
        List<String> picked = new ArrayList<>();
        for (JCheckBox b : hobbyBoxes) if (b.isSelected()) picked.add(b.getText());
        if (picked.isEmpty()) { Theme.warn(this, "Pick at least one hobby."); return null; }

        Dao.Profile p = new Dao.Profile();
        p.name = n;
        p.phone = ph;
        p.email = em;
        p.dob = birth;
        p.gender = male.isSelected() ? "Male" : female.isSelected() ? "Female" : "Other";
        p.city = city.getText().trim();
        p.hobbies = String.join(", ", picked);
        p.genre = (String) genre.getSelectedItem();
        p.language = (String) lang.getSelectedItem();
        return p;
    }

    void load(Dao.Profile p) {
        name.setText(p.name);
        phone.setText(p.phone);
        email.setText(p.email);
        city.setText(p.city);
        if (p.dob != null) {
            dob.setDate(p.dob);
        }
        male.setSelected("Male".equals(p.gender));
        female.setSelected("Female".equals(p.gender));
        other.setSelected("Other".equals(p.gender));
        for (JCheckBox b : hobbyBoxes) b.setSelected(p.hobbies != null && p.hobbies.contains(b.getText()));
        if (!p.genre.isEmpty()) genre.setSelectedItem(p.genre);
        if (!p.language.isEmpty()) lang.setSelectedItem(p.language);
    }
}

