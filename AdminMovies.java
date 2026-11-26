package MovieBooking;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.List;

/** Admin: add, edit and delete movies (with poster image). */
class AdminMovies extends JPanel {
    private final AdminUi.DataTable table =
            new AdminUi.DataTable("ID", "Title", "Language", "Minutes", "Genre", "Rating", "Cert", "Poster file");

    AdminMovies() {
        setLayout(new BorderLayout());
        setOpaque(false);
        add(AdminUi.header("Movies", "Add new movies, change details, upload posters", Theme.PINK, Theme.PURPLE), BorderLayout.NORTH);

        Theme.Btn add = AdminUi.btn("+ Add movie", Theme.GREEN);
        Theme.Btn edit = AdminUi.btn("Edit selected", Theme.BLUE);
        Theme.Btn del = AdminUi.btn("Delete selected", Theme.RED);
        add.addActionListener(e -> addMovie());
        edit.addActionListener(e -> editMovie());
        del.addActionListener(e -> deleteMovie());
        table.width(0, 40);
        table.width(1, 220);
        add(AdminUi.body(AdminUi.scope() > 0 ? AdminUi.buttons(add, edit) : AdminUi.buttons(add, edit, del), table), BorderLayout.CENTER);
    }

    void refresh() {
        try {
            table.load(AdminDao.table("SELECT movieid, title, language, duration, genre, rating, certificate, posterfile "
                    + "FROM Movie ORDER BY movieid"));
        } catch (SQLException ex) {
            AdminUi.error(this, ex);
        }
    }

    private void addMovie() {
        if (movieForm("Add movie", new String[]{"", "Tamil", "", "Action", "", "UA", "", ""}, null)) refresh();
    }

    private void editMovie() {
        String id = table.sel(0);
        if (id == null) { Theme.warn(this, "Select a movie first."); return; }
        try {
            List<Object[]> r = AdminDao.table("SELECT title, language, duration, genre, rating, certificate, description, posterfile "
                    + "FROM Movie WHERE movieid = ?", Integer.parseInt(id));
            if (r.isEmpty()) return;
            String[] init = new String[8];
            for (int i = 0; i < 8; i++) init[i] = r.get(0)[i] == null ? "" : String.valueOf(r.get(0)[i]);
            if (movieForm("Edit movie #" + id, init, Integer.parseInt(id))) refresh();
        } catch (SQLException ex) {
            AdminUi.error(this, ex);
        }
    }

    private void deleteMovie() {
        String id = table.sel(0);
        if (id == null) { Theme.warn(this, "Select a movie first."); return; }
        if (!AdminUi.confirm(this, "Delete movie \"" + table.sel(1) + "\"?\nA movie that already has shows cannot be deleted.")) return;
        try {
            AdminDao.exec("DELETE FROM Movie WHERE movieid = ?", Integer.parseInt(id));
            refresh();
        } catch (SQLException ex) {
            AdminUi.error(this, ex);
        }
    }

    /**
     * One dialog for add and edit. init = title, language, minutes, genre, rating, cert, description, poster.
     * movieId null = insert, otherwise update. The dialog stays open (with the values kept) until the movie is
     * saved or the user presses Cancel. Returns true when the movie was saved.
     */
    private boolean movieForm(String title, String[] init, Integer movieId) {
        JTextField name = AdminUi.tf(init[0], 24);
        JComboBox<String> lang = new JComboBox<>(ProfilePanel.LANGS);
        lang.setSelectedItem(init[1]);
        JTextField mins = AdminUi.tf(init[2], 24);
        JComboBox<String> genre = new JComboBox<>(ProfilePanel.GENRES);
        genre.setSelectedItem(init[3]);
        JTextField rating = AdminUi.tf(init[4], 24);
        JComboBox<String> cert = new JComboBox<>(new String[]{"U", "UA", "A"});
        cert.setSelectedItem(init[5]);
        JTextArea desc = new JTextArea(init[6], 4, 24);
        desc.setLineWrap(true);
        desc.setWrapStyleWord(true);
        desc.setFont(Theme.font(Font.PLAIN, 14));
        JTextField poster = AdminUi.tf(init[7], 18);
        Theme.Btn choose = AdminUi.btn("Choose image", Theme.PURPLE);
        File[] picked = new File[1];
        choose.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Select poster image");
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                picked[0] = fc.getSelectedFile();
                poster.setText(picked[0].getName());
            }
        });
        JPanel pp = new JPanel(new BorderLayout(8, 0));
        pp.add(poster, BorderLayout.CENTER);
        pp.add(choose, BorderLayout.EAST);

        return AdminUi.formInline(this, title,
                new String[]{"Title", "Language", "Duration (min)", "Genre", "Rating (0-10)", "Certificate", "Description", "Poster"},
                new JComponent[]{name, lang, mins, genre, rating, cert, new JScrollPane(desc), pp},
                () -> {
                    if (name.getText().trim().isEmpty()) return "Title is required.";
                    int m;
                    double r;
                    try {
                        m = Integer.parseInt(mins.getText().trim());
                    } catch (NumberFormatException ex) {
                        return "Duration must be a whole number of minutes, for example 150.";
                    }
                    if (m <= 0) return "Duration must be more than 0 minutes.";
                    try {
                        r = Double.parseDouble(rating.getText().trim());
                    } catch (NumberFormatException ex) {
                        return "Rating must be a number between 0 and 10, for example 8.2.";
                    }
                    if (r < 0 || r > 10) return "Rating must be between 0 and 10.";

                    String file = poster.getText().trim();
                    if (picked[0] != null) {
                        try {
                            String n = picked[0].getName();
                            String ext = n.contains(".") ? n.substring(n.lastIndexOf('.')) : ".jpg";
                            file = name.getText().trim().toLowerCase().replaceAll("[^a-z0-9]", "") + ext.toLowerCase();
                            File dir = new File("posters");
                            dir.mkdirs();
                            Files.copy(picked[0].toPath(), new File(dir, file).toPath(), StandardCopyOption.REPLACE_EXISTING);
                            poster.setText(file);
                            picked[0] = null;
                        } catch (Exception ex) {
                            return "Could not copy the poster image: " + ex.getMessage();
                        }
                    }
                    try {
                        if (movieId == null) {
                            AdminDao.exec("INSERT INTO Movie (title, language, duration, genre, rating, certificate, description, posterfile) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", name.getText().trim(), lang.getSelectedItem(), m,
                                    genre.getSelectedItem(), r, cert.getSelectedItem(), desc.getText().trim(), file);
                        } else {
                            AdminDao.exec("UPDATE Movie SET title = ?, language = ?, duration = ?, genre = ?, rating = ?, "
                                    + "certificate = ?, description = ?, posterfile = ? WHERE movieid = ?", name.getText().trim(),
                                    lang.getSelectedItem(), m, genre.getSelectedItem(), r, cert.getSelectedItem(),
                                    desc.getText().trim(), file, movieId);
                        }
                    } catch (SQLException ex) {
                        return "Could not save the movie: " + AdminUi.message(ex);
                    }
                    return null;
                });
    }
}