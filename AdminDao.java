package MovieBooking;

import java.math.BigDecimal;
import java.sql.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Database helpers used only by the Admin panel. */
final class AdminDao {
    private AdminDao() {}

    /** Runs a SELECT and returns every row as an Object[]. */
    static List<Object[]> table(String sql, Object... params) throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        try (Connection con = DB.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                int n = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    Object[] r = new Object[n];
                    for (int i = 0; i < n; i++) {
                        Object v = rs.getObject(i + 1);
                        if (v instanceof BigDecimal bd) v = bd.stripTrailingZeros().toPlainString();
                        r[i] = v;
                    }
                    rows.add(r);
                }
            }
        }
        return rows;
    }

    /** First column of the first row as a number (0 if empty). */
    static double scalar(String sql, Object... params) throws SQLException {
        List<Object[]> r = table(sql, params);
        if (r.isEmpty() || r.get(0)[0] == null) return 0;
        return ((Number) (r.get(0)[0] instanceof String s ? Double.valueOf(s) : r.get(0)[0])).doubleValue();
    }

    /** INSERT / UPDATE / DELETE. Returns the number of rows changed. */
    static int exec(String sql, Object... params) throws SQLException {
        try (Connection con = DB.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
    }

    // ------------------------------------------------------------ screens
    /** Problem with a layout request, or null when it is fine. */
    static String layoutProblem(int rows, int perRow, int front, int premium, int aisleAfter) {
        if (rows < 3 || rows > 20) return "Rows must be between 3 and 20.";
        if (perRow < 4 || perRow > 24) return "Seats per row must be between 4 and 24.";
        if (front < 0 || premium < 0) return "Row counts cannot be negative.";
        if (front + premium > rows) return "Front rows + Premium rows cannot be more than the total rows.";
        if (aisleAfter < 0 || aisleAfter >= perRow) return "The aisle must come after a seat number between 1 and " + (perRow - 1) + " (0 = no aisle).";
        return null;
    }

    /**
     * Creates a screen and its own seat layout: rows A, B, C ...; the first `front` rows are Front, the last `premium`
     * rows are Premium, the rest Standard; an aisle gap is drawn after seat number `aisleAfter` (0 = no aisle).
     */
    static int addScreen(int theatreId, String name, int rows, int perRow, int front, int premium, int aisleAfter) throws SQLException {
        String bad = layoutProblem(rows, perRow, front, premium, aisleAfter);
        if (bad != null) throw new SQLException(bad);
        if (AdminUi.scope() > 0 && theatreId != AdminUi.scope()) throw new SQLException("You can add screens only in your own theatre.");
        try (Connection con = DB.getConnection()) {
            con.setAutoCommit(false);
            try {
                int screenId;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO Screen (theatreid, screenname, capacity) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, theatreId);
                    ps.setString(2, name);
                    ps.setInt(3, rows * perRow);
                    ps.executeUpdate();
                    try (ResultSet k = ps.getGeneratedKeys()) { k.next(); screenId = k.getInt(1); }
                }
                insertSeats(con, screenId, rows, perRow, front, premium, aisleAfter);
                con.commit();
                return screenId;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** Replaces the seat layout of a screen that has no shows yet. */
    static void rebuildLayout(int screenId, int rows, int perRow, int front, int premium, int aisleAfter) throws SQLException {
        String bad = layoutProblem(rows, perRow, front, premium, aisleAfter);
        if (bad != null) throw new SQLException(bad);
        if (AdminUi.scope() > 0 && scalar("SELECT COUNT(*) FROM Screen WHERE screenid = ? AND theatreid = ?", screenId, AdminUi.scope()) == 0)
            throw new SQLException("This screen belongs to another theatre.");
        if (scalar("SELECT COUNT(*) FROM `Show` WHERE screenid = ?", screenId) > 0)
            throw new SQLException("This screen already has shows, so its layout cannot be changed. Add a new screen instead.");
        try (Connection con = DB.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM Seat WHERE screenid = ?")) {
                    ps.setInt(1, screenId);
                    ps.executeUpdate();
                }
                insertSeats(con, screenId, rows, perRow, front, premium, aisleAfter);
                try (PreparedStatement ps = con.prepareStatement("UPDATE Screen SET capacity = ? WHERE screenid = ?")) {
                    ps.setInt(1, rows * perRow);
                    ps.setInt(2, screenId);
                    ps.executeUpdate();
                }
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    private static void insertSeats(Connection con, int screenId, int rows, int perRow, int front, int premium, int aisleAfter)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO Seat (screenid, rowno, seatnumber, category, gapbefore) VALUES (?, ?, ?, ?, ?)")) {
            for (int r = 0; r < rows; r++) {
                String cat = r < front ? "Front" : (r >= rows - premium ? "Premium" : "Standard");
                for (int n = 1; n <= perRow; n++) {
                    ps.setInt(1, screenId);
                    ps.setString(2, String.valueOf((char) ('A' + r)));
                    ps.setInt(3, n);
                    ps.setString(4, cat);
                    ps.setInt(5, aisleAfter > 0 && n == aisleAfter + 1 ? 1 : 0);
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
    }

    // -------------------------------------------------------------- shows
    /** Creates a show and one ShowSeat per seat. Price = base x category x weekend. */
    static void addShow(int movieId, int screenId, LocalDate date, String time, double base) throws SQLException {
        if (AdminUi.scope() > 0 && scalar("SELECT COUNT(*) FROM Screen WHERE screenid = ? AND theatreid = ?", screenId, AdminUi.scope()) == 0)
            throw new SQLException("You can add shows only in your own theatre.");
        try (Connection con = DB.getConnection()) {
            con.setAutoCommit(false);
            try {
                if (java.time.LocalDateTime.of(date, java.time.LocalTime.parse(time)).isBefore(java.time.LocalDateTime.now()))
                    throw new SQLException("That show time has already passed.");
                checkFree(con, movieId, screenId, date, time);
                int showId;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO `Show` (movieid, screenid, showdate, starttime, baseprice, status) VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, movieId);
                    ps.setInt(2, screenId);
                    ps.setDate(3, Date.valueOf(date));
                    ps.setString(4, time);
                    ps.setDouble(5, base);
                    ps.executeUpdate();
                    try (ResultSet k = ps.getGeneratedKeys()) { k.next(); showId = k.getInt(1); }
                }
                boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO ShowSeat (showid, seatid, status, price) "
                                + "SELECT ?, seatid, 'AVAILABLE', "
                                + "ROUND(? * CASE category WHEN 'Front' THEN 0.9 WHEN 'Premium' THEN 1.2 ELSE 1.0 END * ?) "
                                + "FROM Seat WHERE screenid = ?")) {
                    ps.setInt(1, showId);
                    ps.setDouble(2, base);
                    ps.setDouble(3, weekend ? 1.15 : 1.0);
                    ps.setInt(4, screenId);
                    ps.executeUpdate();
                }
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** Minutes of cleaning time kept between two shows on the same screen. */
    private static final int CLEANING = 20;

    private static int minutes(String hhmm) {
        String t = hhmm.trim();
        return Integer.parseInt(t.substring(0, 2)) * 60 + Integer.parseInt(t.substring(3, 5));
    }

    /** Throws when another active show on this screen would overlap (movie length + cleaning time). */
    private static void checkFree(Connection con, int movieId, int screenId, LocalDate date, String time) throws SQLException {
        int newLen = 150;
        try (PreparedStatement ps = con.prepareStatement("SELECT duration FROM Movie WHERE movieid = ?")) {
            ps.setInt(1, movieId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) newLen = rs.getInt(1);
            }
        }
        int ns = minutes(time), ne = ns + newLen;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT sh.starttime, m.title, m.duration FROM `Show` sh JOIN Movie m ON m.movieid = sh.movieid "
                        + "WHERE sh.screenid = ? AND sh.showdate = ? AND sh.status = 'ACTIVE'")) {
            ps.setInt(1, screenId);
            ps.setDate(2, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int es = minutes(rs.getString(1));
                    int ee = es + (rs.getInt(3) > 0 ? rs.getInt(3) : 150);
                    if (ns < ee + CLEANING && es < ne + CLEANING)
                        throw new SQLException("The screen is busy: \"" + rs.getString(2) + "\" runs from "
                                + rs.getString(1).substring(0, 5) + " for " + (ee - es) + " min (plus " + CLEANING + " min cleaning).");
                }
            }
        }
    }

    /** Result of scheduling one movie on every screen: one row per screen. */
    record Placement(String theatre, String screen, int seats, boolean created, String note) {}

    /**
     * Puts one movie on EVERY screen of EVERY theatre at the same date and time (a world-premiere style release).
     * Screens that are busy are skipped with the reason; the rest are created. Each screen is its own transaction.
     */
    static List<Placement> addShowEverywhere(int movieId, LocalDate date, String time, double base) throws SQLException {
        List<Object[]> screens = table("SELECT s.screenid, t.name, s.screenname, "
                + "(SELECT COUNT(*) FROM Seat x WHERE x.screenid = s.screenid) "
                + "FROM Screen s JOIN Theatre t ON t.theatreid = s.theatreid "
                + (AdminUi.scope() > 0 ? "WHERE s.theatreid = " + AdminUi.scope() + " " : "") + "ORDER BY t.name, s.screenname");
        List<Placement> out = new ArrayList<>();
        for (Object[] sc : screens) {
            int seats = ((Number) sc[3]).intValue();
            if (seats == 0) {
                out.add(new Placement(String.valueOf(sc[1]), String.valueOf(sc[2]), 0, false, "No seats in this screen"));
                continue;
            }
            try {
                addShow(movieId, ((Number) sc[0]).intValue(), date, time, base);
                out.add(new Placement(String.valueOf(sc[1]), String.valueOf(sc[2]), seats, true, "Show created"));
            } catch (SQLException ex) {
                out.add(new Placement(String.valueOf(sc[1]), String.valueOf(sc[2]), seats, false, ex.getMessage()));
            }
        }
        return out;
    }

    /** Cancels a show: full refund for every confirmed booking. Returns how many bookings were refunded. */
    static int cancelShow(int showId) throws SQLException {
        try (Connection con = DB.getConnection()) {
            con.setAutoCommit(false);
            try {
                int count;
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE Booking SET status = 'CANCELLED', cancelledat = NOW(), refundamount = totalamount "
                                + "WHERE showid = ? AND status = 'CONFIRMED'")) {
                    ps.setInt(1, showId);
                    count = ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE ShowSeat SET status = 'AVAILABLE' WHERE showid = ?")) {
                    ps.setInt(1, showId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE `Show` SET status = 'CANCELLED' WHERE showid = ?")) {
                    ps.setInt(1, showId);
                    ps.executeUpdate();
                }
                con.commit();
                return count;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }
}

