package MovieBooking;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;

/** Admin: every booking (with admin cancel) and every customer with profile details. */
class AdminBookings extends JPanel {
    private final AdminUi.DataTable bookings = new AdminUi.DataTable(
            "ID", "Customer", "Movie", "Theatre", "Date", "Time", "Seats", "Total", "Status", "Refund", "Channel", "Entry");
    private final AdminUi.DataTable customers = new AdminUi.DataTable(
            "ID", "Name", "Phone", "Email", "DOB", "Gender", "City", "Hobbies", "Fav genre", "Language", "Bookings");

    AdminBookings() {
        setLayout(new BorderLayout());
        setOpaque(false);
        add(AdminUi.header("Bookings and Customers", "See every ticket sold and every registered customer", Theme.GREEN, Theme.BLUE), BorderLayout.NORTH);

        Theme.Btn cancel = AdminUi.btn("Cancel selected booking (full refund)", Theme.RED);
        cancel.addActionListener(e -> cancelBooking());
        bookings.width(0, 40);
        bookings.width(6, 120);
        customers.width(0, 40);
        customers.width(3, 160);
        customers.width(7, 160);

        UIManager.put("TabbedPane.contentAreaColor", Theme.BG);
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(Theme.BG);
        tabs.setOpaque(false);
        tabs.setFont(Theme.font(Font.BOLD, 14));
        tabs.addTab("Bookings", AdminUi.body(AdminUi.buttons(cancel), bookings));
        tabs.addTab("Customers", AdminUi.body(null, customers));
        add(tabs, BorderLayout.CENTER);
    }

    void refresh() {
        try {
            bookings.load(AdminDao.table("SELECT b.bookingid, c.name, m.title, t.name, sh.showdate, sh.starttime, "
                    + "GROUP_CONCAT(CONCAT(se.rowno, se.seatnumber) ORDER BY se.rowno, se.seatnumber SEPARATOR ', '), "
                    + "b.totalamount, b.status, IFNULL(b.refundamount, 0), b.channel, IF(b.checkedin = 1, 'ENTERED', '-') "
                    + "FROM Booking b JOIN Customer c ON c.customerid = b.customerid "
                    + "JOIN `Show` sh ON sh.showid = b.showid JOIN Movie m ON m.movieid = sh.movieid "
                    + "JOIN Screen sc ON sc.screenid = sh.screenid JOIN Theatre t ON t.theatreid = sc.theatreid "
                    + "LEFT JOIN BookingSeat bs ON bs.bookingid = b.bookingid "
                    + "LEFT JOIN ShowSeat ss ON ss.showseatid = bs.showseatid LEFT JOIN Seat se ON se.seatid = ss.seatid "
                    + "GROUP BY b.bookingid, c.name, m.title, t.name, sh.showdate, sh.starttime, b.totalamount, b.status, b.refundamount, b.channel, b.checkedin "
                    + "ORDER BY b.bookingid DESC"));
            customers.load(AdminDao.table("SELECT c.customerid, c.name, c.phone, c.email, u.dob, u.gender, u.city, u.hobbies, "
                    + "u.favgenre, u.favlanguage, (SELECT COUNT(*) FROM Booking b WHERE b.customerid = c.customerid) "
                    + "FROM Customer c LEFT JOIN UserProfile u ON u.customerid = c.customerid ORDER BY c.customerid"));
        } catch (SQLException ex) {
            AdminUi.error(this, ex);
        }
    }

    private void cancelBooking() {
        String id = bookings.sel(0);
        if (id == null) { Theme.warn(this, "Select a booking first."); return; }
        if (!"CONFIRMED".equals(bookings.sel(8))) { Theme.warn(this, "Only confirmed bookings can be cancelled."); return; }
        if (!AdminUi.confirm(this, "Cancel booking #" + id + " (" + bookings.sel(2) + ") and refund "
                + Theme.money(Double.parseDouble(bookings.sel(7))) + "?")) return;
        try {
            Dao.cancel(Dao.booking(Integer.parseInt(id)), 100);
            refresh();
        } catch (SQLException ex) {
            AdminUi.error(this, ex);
        }
    }
}

