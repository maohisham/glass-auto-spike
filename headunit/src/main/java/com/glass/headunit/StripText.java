package com.glass.headunit;

/** The prayer line of the top strip: it may wrap in one place only, so the countdown moves whole to the next line when space is short. Pure. */
final class StripText {

    private StripText() { }

    /** "Isyak 8:27 PM" and "in 1h 06m" joined by the only breaking space. */
    static String build(String name, String time12, String countdown) {
        return (name + " " + time12).replace(' ', '\u00a0') + " " + ("in " + countdown).replace(' ', '\u00a0');
    }
}
