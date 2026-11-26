package MovieBooking;

import java.sql.*;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

/** All database queries in one place. */
final class Dao {
    private Dao() {}

    // ------------------------------------------------------------ session
    static final class Session {
        static int userId, customerId;
        static String username, role;
        // staff only: which employee / theatre, and the walk-in customer of the current counter sale
        static int staffId, theatreId, walkinCustomerId;
        static String theatreName, position;
        static boolean isStaff() { return "STAFF".equalsIgnoreCase(role); }
        static boolean isManager() { return isStaff() && "Manager".equalsIgnoreCase(position); }
        static void clear() {
            userId = 0; customerId = 0; username = null; role = null;
            staffId = 0; theatreId = 0; walkinCustomerId = 0; theatreName = null; position = null;
        }
    }

    // ------------------------------------------------------------- models
    record Movie(int id, String title, String language, int duration, String genre,
                 double rating, String cert, String description, String poster) {}

    record Show(int id, int movieId, String theatre, String location, String screen,
                LocalDate date, LocalTime time, double base) {
        LocalDateTime when() { return LocalDateTime.of(date, time); }
    }

    record Seat(int showSeatId, String row, int number, String category, String status, double price, boolean gapBefore) {
        boolean available() { return "AVAILABLE".equalsIgnoreCase(status); }
        boolean blocked() { return "BLOCKED".equalsIgnoreCase(status); }
        String label() { return row + number; }
    }

    record Booking(int id, String movie, String poster, String theatre, String screen,
                   LocalDate date, LocalTime time, double total, String status,
                   double refund, String seats) {
        LocalDateTime when() { return LocalDateTime.of(date, time); }
    }

    record Policy(int minHours, int percent) {}

    static final class Profile {
        String name, phone, email, gender, city, hobbies, genre, language;
        LocalDate dob;
    }

    // -------------------------------------------------------------- login
    static boolean login(String user, String pass) throws SQLException {
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT userid, role, customerid, staffid FROM AppUser WHERE username = ? AND password = ?")) {
            ps.setString(1, user);
            ps.setString(2, pass);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                Session.userId = rs.getInt(1);
                Session.role = rs.getString(2);
                Session.username = user;
                int cid = rs.getInt(3);
                boolean cidNull = rs.wasNull();
                int staffId = rs.getInt(4);
                boolean admin = "ADMIN".equalsIgnoreCase(Session.role);
                if (Session.isStaff()) {
                    if (!loadStaff(con, staffId)) { Session.clear(); return false; }   // unknown or deactivated staff
                    Session.customerId = 0;
                    return true;
                }
                if (cidNull) cid = admin ? 0 : createCustomerFor(con, Session.userId, user);
                Session.customerId = cid;
                return true;
            }
        }
    }

    /** Loads the employee's theatre into the session. False when the staff row is missing or deactivated. */
    private static boolean loadStaff(Connection con, int staffId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT s.theatreid, t.name, s.position FROM Staff s JOIN Theatre t ON t.theatreid = s.theatreid "
                        + "WHERE s.staffid = ? AND s.active = 1")) {
            ps.setInt(1, staffId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                Session.staffId = staffId;
                Session.theatreId = rs.getInt(1);
                Session.theatreName = rs.getString(2);
                Session.position = rs.getString(3);
                return true;
            }
        }
    }

    /** Old accounts (created before the upgrade) have no customer row yet. */
    private static int createCustomerFor(Connection con, int userId, String name) throws SQLException {
        int cid;
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO Customer (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) { k.next(); cid = k.getInt(1); }
        }
        try (PreparedStatement ps = con.prepareStatement("UPDATE AppUser SET customerid = ? WHERE userid = ?")) {
            ps.setInt(1, cid);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
        return cid;
    }

    // ----------------------------------------------------------- register
    /** @return false when the username is already taken. */
    static boolean register(Profile p, String username, String password) throws SQLException {
        if (!Validate.isAdult(p.dob)) throw new SQLException("You must be " + Validate.MIN_AGE + " or older to register.");
        try (Connection con = DB.getConnection()) {
            con.setAutoCommit(false);
            try {
                int cid;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO Customer (name, phone, email) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, p.name);
                    ps.setString(2, p.phone);
                    ps.setString(3, p.email);
                    ps.executeUpdate();
                    try (ResultSet k = ps.getGeneratedKeys()) { k.next(); cid = k.getInt(1); }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO AppUser (username, password, role, customerid) VALUES (?, ?, 'CUSTOMER', ?)")) {
                    ps.setString(1, username);
                    ps.setString(2, password);
                    ps.setInt(3, cid);
                    ps.executeUpdate();
                }
                saveProfileRow(con, cid, p);
                con.commit();
                return true;
            } catch (SQLIntegrityConstraintViolationException e) {
                con.rollback();
                return false;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    // ----------------------------------------------------- reset password
    /** Sets a new password when the username and registered email belong to the same customer account. */
    static boolean resetPassword(String username, String email, String newPassword) throws SQLException {
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE AppUser u JOIN Customer c ON c.customerid = u.customerid "
                             + "SET u.password = ? WHERE u.username = ? AND u.role = 'CUSTOMER' "
                             + "AND LOWER(c.email) = LOWER(?)")) {
            ps.setString(1, newPassword);
            ps.setString(2, username);
            ps.setString(3, email);
            return ps.executeUpdate() == 1;
        }
    }

    // ----------------------------------------------------------- age rule
    /** @return null when the logged-in customer is 18 or older, otherwise the reason booking is blocked. */
    static String adultError() throws SQLException {
        if ("ADMIN".equalsIgnoreCase(Session.role) || Session.isStaff()) return null;   // staff check ID at the counter
        LocalDate dob = null;
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT dob FROM UserProfile WHERE customerid = ?")) {
            ps.setInt(1, Session.customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getDate(1) != null) dob = rs.getDate(1).toLocalDate();
            }
        }
        if (dob == null) return "Add your date of birth in My Profile first. Tickets can be booked only by users aged "
                + Validate.MIN_AGE + " or above.";
        if (!Validate.isAdult(dob)) return "Tickets can be booked only by users aged " + Validate.MIN_AGE + " or above.";
        return null;
    }

    // ------------------------------------------------------------ profile
    static Profile loadProfile() throws SQLException {
        Profile p = new Profile();
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT c.name, c.phone, c.email, u.dob, u.gender, u.city, u.hobbies, u.favgenre, u.favlanguage "
                             + "FROM Customer c LEFT JOIN UserProfile u ON u.customerid = c.customerid "
                             + "WHERE c.customerid = ?")) {
            ps.setInt(1, Session.customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    p.name = nz(rs.getString(1));
                    p.phone = nz(rs.getString(2));
                    p.email = nz(rs.getString(3));
                    Date d = rs.getDate(4);
                    p.dob = d == null ? null : d.toLocalDate();
                    p.gender = nz(rs.getString(5));
                    p.city = nz(rs.getString(6));
                    p.hobbies = nz(rs.getString(7));
                    p.genre = nz(rs.getString(8));
                    p.language = nz(rs.getString(9));
                }
            }
        }
        return p;
    }

    static void saveProfile(Profile p) throws SQLException {
        try (Connection con = DB.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE Customer SET name = ?, phone = ?, email = ? WHERE customerid = ?")) {
                    ps.setString(1, p.name);
                    ps.setString(2, p.phone);
                    ps.setString(3, p.email);
                    ps.setInt(4, Session.customerId);
                    ps.executeUpdate();
                }
                saveProfileRow(con, Session.customerId, p);
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    private static void saveProfileRow(Connection con, int cid, Profile p) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO UserProfile (customerid, dob, gender, city, hobbies, favgenre, favlanguage) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE dob = VALUES(dob), gender = VALUES(gender), city = VALUES(city), "
                        + "hobbies = VALUES(hobbies), favgenre = VALUES(favgenre), favlanguage = VALUES(favlanguage)")) {
            ps.setInt(1, cid);
            if (p.dob == null) ps.setNull(2, Types.DATE); else ps.setDate(2, Date.valueOf(p.dob));
            ps.setString(3, p.gender);
            ps.setString(4, p.city);
            ps.setString(5, p.hobbies);
            ps.setString(6, p.genre);
            ps.setString(7, p.language);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------- movies
    static List<Movie> movies() throws SQLException {
        List<Movie> list = new ArrayList<>();
        try (Connection con = DB.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT movieid, title, language, duration, genre, rating, certificate, description, posterfile "
                             + "FROM Movie ORDER BY movieid")) {
            while (rs.next()) {
                list.add(new Movie(rs.getInt(1), nz(rs.getString(2)), nz(rs.getString(3)), rs.getInt(4),
                        nz(rs.getString(5)), rs.getDouble(6), nz(rs.getString(7)).isEmpty() ? "UA" : rs.getString(7),
                        nz(rs.getString(8)), nz(rs.getString(9))));
            }
        }
        return list;
    }

    /** Upcoming active shows of a movie (past shows are filtered out). */
    static List<Show> shows(int movieId) throws SQLException {
        List<Show> list = new ArrayList<>();
        String sql = "SELECT sh.showid, sh.movieid, t.name, t.location, sc.screenname, sh.showdate, sh.starttime, sh.baseprice "
                + "FROM `Show` sh JOIN Screen sc ON sc.screenid = sh.screenid "
                + "JOIN Theatre t ON t.theatreid = sc.theatreid "
                + "WHERE sh.movieid = ? AND sh.status = 'ACTIVE' AND sh.showdate >= CURDATE() "
                + "ORDER BY sh.showdate, sh.starttime, t.name";
        try (Connection con = DB.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, movieId);
            try (ResultSet rs = ps.executeQuery()) {
                LocalDateTime now = LocalDateTime.now();
                while (rs.next()) {
                    Show s = new Show(rs.getInt(1), rs.getInt(2), nz(rs.getString(3)), nz(rs.getString(4)),
                            nz(rs.getString(5)), rs.getDate(6).toLocalDate(), parseTime(rs.getString(7)), rs.getDouble(8));
                    if (s.when().isAfter(now)) list.add(s);
                }
            }
        }
        return list;
    }

    /** One show by id (used by the staff screens). */
    static Show show(int showId) throws SQLException {
        String sql = "SELECT sh.showid, sh.movieid, t.name, t.location, sc.screenname, sh.showdate, sh.starttime, sh.baseprice "
                + "FROM `Show` sh JOIN Screen sc ON sc.screenid = sh.screenid "
                + "JOIN Theatre t ON t.theatreid = sc.theatreid WHERE sh.showid = ?";
        try (Connection con = DB.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, showId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Show not found.");
                return new Show(rs.getInt(1), rs.getInt(2), nz(rs.getString(3)), nz(rs.getString(4)),
                        nz(rs.getString(5)), rs.getDate(6).toLocalDate(), parseTime(rs.getString(7)), rs.getDouble(8));
            }
        }
    }

    /** Counter sale: stores the walk-in customer (name + phone) and remembers the id in the session. */
    static void newWalkin(String name, String phone) throws SQLException {
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO Customer (name, phone) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, phone);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) { k.next(); Session.walkinCustomerId = k.getInt(1); }
        }
    }

    // -------------------------------------------------------------- seats
    static List<Seat> seats(int showId) throws SQLException {
        List<Seat> list = new ArrayList<>();
        String sql = "SELECT ss.showseatid, se.rowno, se.seatnumber, se.category, ss.status, ss.price, se.gapbefore "
                + "FROM ShowSeat ss JOIN Seat se ON se.seatid = ss.seatid "
                + "WHERE ss.showid = ? ORDER BY se.rowno, se.seatnumber";
        try (Connection con = DB.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, showId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Seat(rs.getInt(1), rs.getString(2), rs.getInt(3), nz(rs.getString(4)),
                            nz(rs.getString(5)), rs.getDouble(6), rs.getInt(7) == 1));
                }
            }
        }
        return list;
    }

    /** Dynamic pricing: the fuller the show, the higher the price. */
    static double demandFactor(double bookedRatio) {
        // slabs are editable by the admin (DemandRule table); fall back to defaults if the table is missing
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT multiplier FROM DemandRule WHERE minpercent <= ? ORDER BY minpercent DESC LIMIT 1")) {
            ps.setDouble(1, bookedRatio * 100);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        } catch (SQLException e) {
            // use defaults below
        }
        if (bookedRatio > 0.80) return 1.25;
        if (bookedRatio >= 0.50) return 1.10;
        return 1.00;
    }

    // ------------------------------------------------------------ booking
    /** Books the given seats in one transaction. Returns the new booking id. */
    static int book(int showId, List<Integer> showSeatIds) throws SQLException {
        String ageProblem = adultError();
        if (ageProblem != null) throw new SQLException(ageProblem);
        if (showSeatIds.isEmpty() || showSeatIds.size() > 8) throw new SQLException("Choose between 1 and 8 seats.");
        try (Connection con = DB.getConnection()) {
            con.setAutoCommit(false);
            try {
                double ratio = 0;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT COUNT(*), SUM(CASE WHEN status = 'BOOKED' THEN 1 ELSE 0 END) "
                                + "FROM ShowSeat WHERE showid = ? AND status <> 'BLOCKED'")) {
                    ps.setInt(1, showId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) ratio = rs.getDouble(2) / rs.getInt(1);
                    }
                }
                if (Session.isStaff()) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT sc.theatreid FROM `Show` sh JOIN Screen sc ON sc.screenid = sh.screenid WHERE sh.showid = ?")) {
                        ps.setInt(1, showId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next() || rs.getInt(1) != Session.theatreId)
                                throw new SQLException("You can sell tickets only for your own theatre.");
                        }
                    }
                    if (Session.walkinCustomerId <= 0) throw new SQLException("Enter the walk-in customer's name and phone first.");
                }
                double factor = demandFactor(ratio);

                List<Long> prices = new ArrayList<>();
                double total = 0;
                for (int id : showSeatIds) {
                    double base;
                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT price FROM ShowSeat WHERE showseatid = ? AND showid = ?")) {
                        ps.setInt(1, id);
                        ps.setInt(2, showId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) throw new SQLException("Seat not found for this show.");
                            base = rs.getDouble(1);
                        }
                    }
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE ShowSeat SET status = 'BOOKED' WHERE showseatid = ? AND status = 'AVAILABLE'")) {
                        ps.setInt(1, id);
                        if (ps.executeUpdate() != 1) {
                            throw new SQLException("Sorry, one of the seats was just booked by someone else. Please choose again.");
                        }
                    }
                    long price = Math.round(base * factor);
                    prices.add(price);
                    total += price;
                }

                int bookingId;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO Booking (customerid, showid, bookingdate, totalamount, status, channel, staffid) "
                                + "VALUES (?, ?, CURDATE(), ?, 'CONFIRMED', ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, Session.isStaff() ? Session.walkinCustomerId : Session.customerId);
                    ps.setInt(2, showId);
                    ps.setDouble(3, total);
                    ps.setString(4, Session.isStaff() ? "COUNTER" : "ONLINE");
                    if (Session.isStaff()) ps.setInt(5, Session.staffId); else ps.setNull(5, Types.INTEGER);
                    ps.executeUpdate();
                    try (ResultSet k = ps.getGeneratedKeys()) { k.next(); bookingId = k.getInt(1); }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO BookingSeat (bookingid, showseatid, price) VALUES (?, ?, ?)")) {
                    for (int i = 0; i < showSeatIds.size(); i++) {
                        ps.setInt(1, bookingId);
                        ps.setInt(2, showSeatIds.get(i));
                        ps.setDouble(3, prices.get(i));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                con.commit();
                return bookingId;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    static List<Booking> myBookings() throws SQLException {
        return queryBookings("b.customerid = ?", Session.customerId);
    }

    static Booking booking(int id) throws SQLException {
        List<Booking> l = queryBookings("b.bookingid = ?", id);
        if (l.isEmpty()) throw new SQLException("Booking not found.");
        return l.get(0);
    }

    private static List<Booking> queryBookings(String where, int param) throws SQLException {
        String sql = "SELECT b.bookingid, m.title, m.posterfile, t.name, sc.screenname, sh.showdate, sh.starttime, "
                + "b.totalamount, b.status, b.refundamount, "
                + "GROUP_CONCAT(CONCAT(se.rowno, se.seatnumber) ORDER BY se.rowno, se.seatnumber SEPARATOR ', ') "
                + "FROM Booking b JOIN `Show` sh ON sh.showid = b.showid "
                + "JOIN Movie m ON m.movieid = sh.movieid "
                + "JOIN Screen sc ON sc.screenid = sh.screenid "
                + "JOIN Theatre t ON t.theatreid = sc.theatreid "
                + "LEFT JOIN BookingSeat bs ON bs.bookingid = b.bookingid "
                + "LEFT JOIN ShowSeat ss ON ss.showseatid = bs.showseatid "
                + "LEFT JOIN Seat se ON se.seatid = ss.seatid "
                + "WHERE " + where + " "
                + "GROUP BY b.bookingid, m.title, m.posterfile, t.name, sc.screenname, sh.showdate, sh.starttime, "
                + "b.totalamount, b.status, b.refundamount "
                + "ORDER BY b.bookingid DESC";
        List<Booking> list = new ArrayList<>();
        try (Connection con = DB.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Booking(rs.getInt(1), nz(rs.getString(2)), nz(rs.getString(3)), nz(rs.getString(4)),
                            nz(rs.getString(5)), rs.getDate(6).toLocalDate(), parseTime(rs.getString(7)),
                            rs.getDouble(8), nz(rs.getString(9)), rs.getDouble(10), nz(rs.getString(11))));
                }
            }
        }
        return list;
    }

    // ------------------------------------------------------- cancellation
    static List<Policy> policies() throws SQLException {
        List<Policy> list = new ArrayList<>();
        try (Connection con = DB.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT minhoursbefore, refundpercent FROM CancellationPolicy ORDER BY minhoursbefore DESC")) {
            while (rs.next()) list.add(new Policy(rs.getInt(1), rs.getInt(2)));
        }
        return list;
    }

    /** Refund percent for cancelling now, or -1 if cancellation is no longer allowed. */
    static int refundPercent(LocalDateTime showTime, List<Policy> policies) {
        double hours = Duration.between(LocalDateTime.now(), showTime).toMinutes() / 60.0;
        for (Policy p : policies) {
            if (hours >= p.minHours()) return p.percent();
        }
        return -1;
    }

    static void cancel(Booking b, int percent) throws SQLException {
        double refund = Math.round(b.total() * percent / 100.0);
        try (Connection con = DB.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE Booking SET status = 'CANCELLED', cancelledat = NOW(), refundamount = ? "
                                + "WHERE bookingid = ? AND status = 'CONFIRMED'")) {
                    ps.setDouble(1, refund);
                    ps.setInt(2, b.id());
                    if (ps.executeUpdate() != 1) throw new SQLException("This booking is already cancelled.");
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE ShowSeat SET status = 'AVAILABLE' "
                                + "WHERE showseatid IN (SELECT showseatid FROM BookingSeat WHERE bookingid = ?)")) {
                    ps.setInt(1, b.id());
                    ps.executeUpdate();
                }
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    // ------------------------------------------------------------ helpers
    private static String nz(String s) { return s == null ? "" : s; }

    private static LocalTime parseTime(String s) {
        try {
            String t = s == null ? "" : s.trim();
            return LocalTime.parse(t.length() >= 5 ? t.substring(0, 5) : t);
        } catch (Exception e) {
            return LocalTime.MIDNIGHT;
        }
    }
}

