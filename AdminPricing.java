package MovieBooking;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.SQLException;

/** Admin: edit dynamic-pricing slabs and the cancellation refund policy. */
class AdminPricing extends JPanel {
    private final AdminUi.DataTable demand = new AdminUi.DataTable("ID", "When seats sold are at least (%)", "Price multiplier");
    private final AdminUi.DataTable policy = new AdminUi.DataTable("ID", "Hours before show (at least)", "Refund (%)");

    AdminPricing() {
        setLayout(new BorderLayout());
        setOpaque(false);
        add(AdminUi.header("Pricing and Policy", "Control dynamic pricing and the cancellation refund rules", Theme.BLUE, Theme.PURPLE), BorderLayout.NORTH);

        Theme.Btn editD = AdminUi.btn("Edit selected slab", Theme.BLUE);
        Theme.Btn addD = AdminUi.btn("+ Add slab", Theme.GREEN);
        Theme.Btn delD = AdminUi.btn("Delete slab", Theme.RED);
        editD.addActionListener(e -> editDemand());
        addD.addActionListener(e -> addDemand());
        delD.addActionListener(e -> delete(demand, "DemandRule", "ruleid"));
        Theme.Btn editP = AdminUi.btn("Edit selected rule", Theme.BLUE);
        editP.addActionListener(e -> editPolicy());
        demand.width(0, 40);
        policy.width(0, 40);

        JLabel info = Theme.label("<html>Seat price = base price x seat type (Front 0.9, Standard 1.0, Premium 1.2) x weekend (1.15), "
                + "set when a show is created. At booking time the demand multiplier below is applied on top.</html>",
                13, Font.PLAIN, Theme.MUTED);

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        top.add(Theme.label("Dynamic pricing slabs", 18, Font.BOLD, Theme.NAVY), BorderLayout.NORTH);
        top.add(demand, BorderLayout.CENTER);
        top.add(AdminUi.buttons(editD, addD, delD), BorderLayout.SOUTH);

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setOpaque(false);
        bottom.add(Theme.label("Cancellation policy (customers cannot cancel below the smallest hours)", 18, Font.BOLD, Theme.NAVY), BorderLayout.NORTH);
        bottom.add(policy, BorderLayout.CENTER);
        bottom.add(AdminUi.buttons(editP), BorderLayout.SOUTH);

        JPanel grid = new JPanel(new GridLayout(2, 1, 0, 18));
        grid.setOpaque(false);
        grid.add(top);
        grid.add(bottom);
        JPanel wrap = new JPanel(new BorderLayout(0, 10));
        wrap.setOpaque(false);
        wrap.add(info, BorderLayout.NORTH);
        wrap.add(grid, BorderLayout.CENTER);
        add(AdminUi.body(null, wrap), BorderLayout.CENTER);
    }

    void refresh() {
        try {
            demand.load(AdminDao.table("SELECT ruleid, minpercent, multiplier FROM DemandRule ORDER BY minpercent"));
            policy.load(AdminDao.table("SELECT policyid, minhoursbefore, refundpercent FROM CancellationPolicy ORDER BY minhoursbefore DESC"));
        } catch (SQLException ex) {
            AdminUi.error(this, ex);
        }
    }

    private void addDemand() {
        JTextField pct = AdminUi.tf("", 10), mult = AdminUi.tf("", 10);
        boolean saved = AdminUi.form(this, "Add slab", new String[]{"Seats sold at least (%)", "Multiplier (e.g. 1.15)"},
                new JComponent[]{pct, mult}, () -> saveDemand(null, pct, mult));
        if (saved) refresh();
    }

    private void editDemand() {
        String id = demand.sel(0);
        if (id == null) { Theme.warn(this, "Select a slab first."); return; }
        JTextField pct = AdminUi.tf(demand.sel(1), 10), mult = AdminUi.tf(demand.sel(2), 10);
        boolean saved = AdminUi.form(this, "Edit slab", new String[]{"Seats sold at least (%)", "Multiplier"},
                new JComponent[]{pct, mult}, () -> saveDemand(Integer.parseInt(id), pct, mult));
        if (saved) refresh();
    }

    /** Validates and saves a slab. id null = insert. Returns an error message or null. */
    private String saveDemand(Integer id, JTextField pct, JTextField mult) {
        int p;
        double m;
        try {
            p = Integer.parseInt(pct.getText().trim());
        } catch (NumberFormatException ex) {
            return "Percent must be a whole number from 0 to 100.";
        }
        try {
            m = Double.parseDouble(mult.getText().trim());
        } catch (NumberFormatException ex) {
            return "Multiplier must be a number such as 1.15.";
        }
        if (p < 0 || p > 100) return "Percent must be from 0 to 100.";
        if (m < 0.5 || m > 3) return "Multiplier must be between 0.5 and 3.";
        try {
            if (id == null) AdminDao.exec("INSERT INTO DemandRule (minpercent, multiplier) VALUES (?, ?)", p, m);
            else AdminDao.exec("UPDATE DemandRule SET minpercent = ?, multiplier = ? WHERE ruleid = ?", p, m, id);
        } catch (SQLException ex) {
            return "Could not save the slab: " + AdminUi.message(ex);
        }
        return null;
    }

    private void editPolicy() {
        String id = policy.sel(0);
        if (id == null) { Theme.warn(this, "Select a rule first."); return; }
        JTextField hrs = AdminUi.tf(policy.sel(1), 10), pct = AdminUi.tf(policy.sel(2), 10);
        boolean saved = AdminUi.form(this, "Edit cancellation rule", new String[]{"Hours before show (at least)", "Refund (%)"},
                new JComponent[]{hrs, pct}, () -> {
                    int h, p;
                    try {
                        h = Integer.parseInt(hrs.getText().trim());
                        p = Integer.parseInt(pct.getText().trim());
                    } catch (NumberFormatException ex) {
                        return "Hours and refund must be whole numbers.";
                    }
                    if (h < 0) return "Hours must be 0 or more.";
                    if (p < 0 || p > 100) return "Refund must be from 0 to 100.";
                    try {
                        AdminDao.exec("UPDATE CancellationPolicy SET minhoursbefore = ?, refundpercent = ? WHERE policyid = ?", h, p, Integer.parseInt(id));
                    } catch (SQLException ex) {
                        return "Could not save the rule: " + AdminUi.message(ex);
                    }
                    return null;
                });
        if (saved) refresh();
    }

    private void delete(AdminUi.DataTable t, String table, String idCol) {
        String id = t.sel(0);
        if (id == null) { Theme.warn(this, "Select a row first."); return; }
        if (!AdminUi.confirm(this, "Delete this row?")) return;
        try {
            AdminDao.exec("DELETE FROM " + table + " WHERE " + idCol + " = ?", Integer.parseInt(id));
            refresh();
        } catch (SQLException ex) {
            AdminUi.error(this, ex);
        }
    }
}