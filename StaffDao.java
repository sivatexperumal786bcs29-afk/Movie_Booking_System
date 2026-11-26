package MovieBooking;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Database helpers used only by the staff (theatre employee) screens. Everything is limited to the staff member's theatre. */
final class StaffDao {
    private StaffDao() {}

    /** Active shows of the staff member's theatre that have not started yet (next 7 days). */
    static List<Object[]> upcomingShows() throws SQLException {
        return AdminDao.table("SELECT sh.showid, m.title, sc.screenname, sh.showdate, LEFT(sh.starttime, 5), sh.baseprice, "
                + "SUM(CASE WHEN ss.status = 'AVAILABLE' THEN 1 ELSE 0 END), "
                + "SUM(CASE WHEN ss.status = 'BOOKED' THEN 1 ELSE 0 END), "
                + "SUM(CASE WHEN ss.status = 'BLOCKED' THEN 1 ELSE 0 END) "
                + "FROM `Show` sh JOIN Movie m ON m.movieid = sh.movieid "
                + "JOIN Screen sc ON sc.screenid = sh.screenid LEFT JOIN ShowSeat ss ON ss.showid = sh.showid "
                + "WHERE sc.theatreid = ? AND sh.status = 'ACTIVE' "
                + "AND TIMESTAMP(sh.showdate, sh.starttime) > NOW() AND sh.showdate <= DATE_ADD(CURDATE(), INTERVAL 7 DAY) "
                + "GROUP BY sh.showid, m.title, sc.screenname, sh.showdate, sh.starttime, sh.baseprice "
                + "ORDER BY sh.showdate, sh.starttime, sc.screenname", Dao.Session.theatreId);
    }

    /** Today's shows with sales split into online / counter and the number of people already inside. */
    static List<Object[]> todayShows() throws SQLException {
        return AdminDao.table("SELECT sh.showid, m.title, sc.screenname, LEFT(sh.starttime, 5), sh.status, "
                + "(SELECT COUNT(*) FROM ShowSeat x WHERE x.showid = sh.showid AND x.status = 'BOOKED'), "
                + "(SELECT COUNT(*) FROM ShowSeat x WHERE x.showid = sh.showid AND x.status = 'BLOCKED'), "
                + "(SELECT COUNT(*) FROM ShowSeat x WHERE x.showid = sh.showid AND x.status = 'AVAILABLE'), "
                + "(SELECT IFNULL(SUM(b.totalamount), 0) FROM Booking b WHERE b.showid = sh.showid AND b.status = 'CONFIRMED' AND b.channel = 'COUNTER'), "
                + "(SELECT IFNULL(SUM(b.totalamount), 0) FROM Booking b WHERE b.showid = sh.showid AND b.status = 'CONFIRMED' AND b.channel = 'ONLINE'), "
                + "(SELECT COUNT(*) FROM Booking b WHERE b.showid = sh.showid AND b.status = 'CONFIRMED' AND b.checkedin = 1) "
                + "FROM `Show` sh JOIN Movie m ON m.movieid = sh.movieid JOIN Screen sc ON sc.screenid = sh.screenid "
                + "WHERE sc.theatreid = ? AND sh.showdate = CURDATE() ORDER BY sh.starttime, sc.screenname",
                Dao.Session.theatreId);
    }

    /** Confirmed bookings for today's shows of this theatre. */
    static List<Object[]> todayBookings() throws SQLException {
        return AdminDao.table("SELECT b.bookingid, c.name, c.phone, m.title, sc.screenname, LEFT(sh.starttime, 5), "
                + "GROUP_CONCAT(CONCAT(se.rowno, se.seatnumber) ORDER BY se.rowno, se.seatnumber SEPARATOR ', '), "
                + "b.channel, IF(b.checkedin = 1, 'ENTERED', 'WAITING') "
                + "FROM Booking b JOIN Customer c ON c.customerid = b.customerid "
                + "JOIN `Show` sh ON sh.showid = b.showid JOIN Movie m ON m.movieid = sh.movieid "
                + "JOIN Screen sc ON sc.screenid = sh.screenid "
                + "LEFT JOIN BookingSeat bs ON bs.bookingid = b.bookingid "
                + "LEFT JOIN ShowSeat ss ON ss.showseatid = bs.showseatid LEFT JOIN Seat se ON se.seatid = ss.seatid "
                + "WHERE sc.theatreid = ? AND sh.showdate = CURDATE() AND b.status = 'CONFIRMED' "
                + "GROUP BY b.bookingid, c.name, c.phone, m.title, sc.screenname, sh.starttime, b.channel, b.checkedin "
                + "ORDER BY sh.starttime, b.bookingid", Dao.Session.theatreId);
    }

    /** Everything the counter needs to verify one ticket by its booking number. */
    record Ticket(int id, String customer, String phone, String movie, String theatre, String screen, String date,
                  String time, String seats, String channel, double total, String verdict, boolean ok) {}

    /**
     * Looks up a booking number and says whether the person may enter: wrong theatre, cancelled, not today's show,
     * already used, or VALID. Nothing is changed in the database (use checkIn to allow the entry).
     */
    static Ticket verify(int bookingId) throws SQLException {
        List<Object[]> r = AdminDao.table("SELECT b.bookingid, c.name, IFNULL(c.phone, '-'), m.title, t.name, sc.screenname, sh.showdate, "
                + "LEFT(sh.starttime, 5), "
                + "(SELECT GROUP_CONCAT(CONCAT(se.rowno, se.seatnumber) ORDER BY se.rowno, se.seatnumber SEPARATOR ', ') "
                + "   FROM BookingSeat bs JOIN ShowSeat ss ON ss.showseatid = bs.showseatid "
                + "   JOIN Seat se ON se.seatid = ss.seatid WHERE bs.bookingid = b.bookingid), "
                + "b.channel, b.totalamount, b.status, b.checkedin, IFNULL(DATE_FORMAT(b.checkedinat, '%H:%i'), ''), "
                + "sc.theatreid, sh.showdate = CURDATE() "
                + "FROM Booking b JOIN Customer c ON c.customerid = b.customerid "
                + "JOIN `Show` sh ON sh.showid = b.showid JOIN Movie m ON m.movieid = sh.movieid "
                + "JOIN Screen sc ON sc.screenid = sh.screenid JOIN Theatre t ON t.theatreid = sc.theatreid "
                + "WHERE b.bookingid = ?", bookingId);
        if (r.isEmpty()) throw new SQLException("No booking with number " + bookingId + ". Check the number on the ticket.");
        Object[] x = r.get(0);
        String verdict;
        boolean ok = false;
        if (Integer.parseInt(String.valueOf(x[14])) != Dao.Session.theatreId) verdict = "WRONG THEATRE - this ticket is for " + x[4];
        else if (!"CONFIRMED".equals(String.valueOf(x[11]))) verdict = "NOT VALID - booking is " + x[11];
        else if (Integer.parseInt(String.valueOf(x[15])) != 1) verdict = "NOT TODAY - the show is on " + x[6];
        else if (Integer.parseInt(String.valueOf(x[12])) == 1) verdict = "ALREADY USED - entered at " + x[13];
        else { verdict = "VALID - allow entry"; ok = true; }
        return new Ticket(Integer.parseInt(String.valueOf(x[0])), String.valueOf(x[1]), String.valueOf(x[2]), String.valueOf(x[3]),
                String.valueOf(x[4]), String.valueOf(x[5]), String.valueOf(x[6]), String.valueOf(x[7]), String.valueOf(x[8]),
                String.valueOf(x[9]), Double.parseDouble(String.valueOf(x[10])), verdict, ok);
    }

    /** Blocks free seats or releases blocked seats of one show. Returns how many seats changed. */
    static int setBlocked(int showId, List<Integer> showSeatIds, boolean block) throws SQLException {
        try (Connection con = DB.getConnection()) {
            con.setAutoCommit(false);
            try {
                checkOwnShow(con, showId);
                int changed = 0;
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE ShowSeat SET status = ? WHERE showseatid = ? AND showid = ? AND status = ?")) {
                    for (int id : showSeatIds) {
                        ps.setString(1, block ? "BLOCKED" : "AVAILABLE");
                        ps.setInt(2, id);
                        ps.setInt(3, showId);
                        ps.setString(4, block ? "AVAILABLE" : "BLOCKED");
                        changed += ps.executeUpdate();
                    }
                }
                con.commit();
                return changed;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    private static void checkOwnShow(Connection con, int showId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT sc.theatreid FROM `Show` sh JOIN Screen sc ON sc.screenid = sh.screenid WHERE sh.showid = ?")) {
            ps.setInt(1, showId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) != Dao.Session.theatreId)
                    throw new SQLException("This show belongs to another theatre.");
            }
        }
    }

    /**
     * Marks a booking as entered. Returns a short message for the staff member.
     * Fails (SQLException with a clear message) when the booking is for another theatre, not for today,
     * cancelled, or already used.
     */
    static String checkIn(int bookingId) throws SQLException {
        try (Connection con = DB.getConnection()) {
            con.setAutoCommit(false);
            try {
                String movie, seats;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT sc.theatreid, sh.showdate = CURDATE(), b.status, b.checkedin, m.title, "
                                + "(SELECT GROUP_CONCAT(CONCAT(se.rowno, se.seatnumber) ORDER BY se.rowno, se.seatnumber SEPARATOR ', ') "
                                + "   FROM BookingSeat bs JOIN ShowSeat ss ON ss.showseatid = bs.showseatid "
                                + "   JOIN Seat se ON se.seatid = ss.seatid WHERE bs.bookingid = b.bookingid) "
                                + "FROM Booking b JOIN `Show` sh ON sh.showid = b.showid "
                                + "JOIN Screen sc ON sc.screenid = sh.screenid JOIN Movie m ON m.movieid = sh.movieid "
                                + "WHERE b.bookingid = ?")) {
                    ps.setInt(1, bookingId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("No booking with ID " + bookingId + ".");
                        if (rs.getInt(1) != Dao.Session.theatreId) throw new SQLException("This booking is for another theatre.");
                        if (!"CONFIRMED".equals(rs.getString(3))) throw new SQLException("This booking is " + rs.getString(3) + ". Entry not allowed.");
                        if (rs.getInt(2) != 1) throw new SQLException("This booking is not for a show today.");
                        if (rs.getInt(4) == 1) throw new SQLException("Already entered. This ticket was used earlier.");
                        movie = rs.getString(5);
                        seats = rs.getString(6);
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE Booking SET checkedin = 1, checkedinat = NOW() WHERE bookingid = ? AND checkedin = 0")) {
                    ps.setInt(1, bookingId);
                    if (ps.executeUpdate() != 1) throw new SQLException("Already entered. This ticket was used earlier.");
                }
                con.commit();
                return "Entry OK  -  booking #" + bookingId + "  -  " + movie + "  -  seats " + seats;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    // ------------------------------------------------------- admin: staff accounts
    static List<Object[]> staffList() throws SQLException {
        return AdminDao.table("SELECT s.staffid, s.name, s.phone, s.position, t.name, u.username, "
                + "IF(s.active = 1, 'ACTIVE', 'INACTIVE'), "
                + "(SELECT COUNT(*) FROM Booking b WHERE b.staffid = s.staffid) "
                + "FROM Staff s JOIN Theatre t ON t.theatreid = s.theatreid "
                + "LEFT JOIN AppUser u ON u.staffid = s.staffid ORDER BY s.staffid");
    }

    /** Creates the employee and the login in one transaction. Returns false when the username is taken. */
    static boolean addStaff(String name, String phone, String position, int theatreId,
                            String username, String password) throws SQLException {
        try (Connection con = DB.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM AppUser WHERE username = ?")) {
                    ps.setString(1, username);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) > 0) { con.rollback(); return false; }
                    }
                }
                int staffId;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO Staff (name, phone, position, theatreid, active) VALUES (?, ?, ?, ?, 1)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name);
                    ps.setString(2, phone);
                    ps.setString(3, position);
                    ps.setInt(4, theatreId);
                    ps.executeUpdate();
                    try (ResultSet k = ps.getGeneratedKeys()) { k.next(); staffId = k.getInt(1); }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO AppUser (username, password, role, staffid) VALUES (?, ?, 'STAFF', ?)")) {
                    ps.setString(1, username);
                    ps.setString(2, password);
                    ps.setInt(3, staffId);
                    ps.executeUpdate();
                }
                con.commit();
                return true;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    static void setActive(int staffId, boolean active) throws SQLException {
        AdminDao.exec("UPDATE Staff SET active = ? WHERE staffid = ?", active ? 1 : 0, staffId);
    }

    /** Deletes an employee who has made no counter sales. Otherwise deactivate instead. */
    static void deleteStaff(int staffId) throws SQLException {
        if (AdminDao.scalar("SELECT COUNT(*) FROM Booking WHERE staffid = ?", staffId) > 0)
            throw new SQLException("This employee has sold tickets, so the record is kept. Deactivate the account instead.");
        AdminDao.exec("DELETE FROM AppUser WHERE staffid = ?", staffId);
        AdminDao.exec("DELETE FROM Staff WHERE staffid = ?", staffId);
    }

    /** Theatres for the add-staff form: {id, name}. */
    static List<Object[]> theatres() throws SQLException {
        return new ArrayList<>(AdminDao.table("SELECT theatreid, name FROM Theatre ORDER BY name"));
    }
}

