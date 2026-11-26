package MovieBooking;

import java.time.LocalDate;
import java.time.Period;

/** Input rules shared by the screens: password strength and the 18+ age check. */
final class Validate {
    private Validate() {}

    static final String PASSWORD_RULE =
            "Password: at least 8 characters with an uppercase letter, a lowercase letter, a digit and a special character.";
    static final int MIN_AGE = 18;

    /** @return an error message, or null when the password meets every rule. */
    static String password(String p) {
        if (p == null || p.length() < 8) return "Password must be at least 8 characters.";
        boolean upper = false, lower = false, digit = false, special = false;
        for (char c : p.toCharArray()) {
            if (Character.isUpperCase(c)) upper = true;
            else if (Character.isLowerCase(c)) lower = true;
            else if (Character.isDigit(c)) digit = true;
            else if (!Character.isWhitespace(c)) special = true;
        }
        if (!upper) return "Password needs at least one uppercase letter.";
        if (!lower) return "Password needs at least one lowercase letter.";
        if (!digit) return "Password needs at least one digit.";
        if (!special) return "Password needs at least one special character (for example @ # $ % !).";
        if (p.chars().anyMatch(Character::isWhitespace)) return "Password must not contain spaces.";
        return null;
    }

    static int age(LocalDate dob) {
        return Period.between(dob, LocalDate.now()).getYears();
    }

    static boolean isAdult(LocalDate dob) {
        return dob != null && age(dob) >= MIN_AGE;
    }
}
