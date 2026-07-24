package neunix.pagevibe;

import java.util.Calendar;

/**
 * A fixed pool of original, evergreen motivational lines shown one-per-day
 * in Reading Stats. Selection is deterministic — no storage, no random
 * seed to persist, no repeated calls returning different results within
 * the same day. The index is derived from a LOCAL day number (shifted by
 * the device's current UTC offset, including DST), so the quote changes
 * exactly at local midnight rather than at a fixed UTC boundary, which
 * would otherwise appear to change at an odd hour depending on timezone.
 */
public class DailyQuoteProvider {

    private DailyQuoteProvider() {}

    private static final String[] QUOTES = {
        "Every page you turn is a page you didn't skip.",
        "Small reading sessions add up to big understanding.",
        "Consistency beats intensity — read a little, every day.",
        "A book finished slowly is still a book finished.",
        "Curiosity is the best reason to open a new page.",
        "You don't need more time — you need five more minutes today.",
        "Reading is the quietest form of progress.",
        "The page in front of you is the only one that matters right now.",
        "Focus is a habit, not a mood.",
        "Understanding grows one paragraph at a time.",
        "Your streak isn't about pressure — it's about momentum.",
        "The best notes are the ones you actually go back to.",
        "Learning rewards patience more than speed.",
        "A single highlighted line today can save you an hour tomorrow.",
        "Reading with intention beats reading with speed.",
        "The habit matters more than the highlight color.",
        "Today's ten minutes are tomorrow's foundation.",
        "Progress hides inside boring, ordinary study sessions.",
        "You're not behind — you're building.",
        "Every bookmark is a promise to your future self.",
        "Deep focus for ten minutes beats distracted focus for an hour.",
        "The page doesn't care how you feel about starting — just start.",
        "Small daily reading compounds faster than you expect.",
        "You finish books the same way you finish anything: page by page.",
        "A streak is just a series of days you didn't quit.",
        "Reading slowly is still reading forward.",
        "Understanding beats finishing. Take your time.",
        "The best study session is the one that actually happens.",
        "Nobody remembers how fast you read — they remember what you understood.",
        "Open the file. That's the whole first step.",
        "A single page today keeps tomorrow lighter.",
        "Your attention is the most valuable thing you own — spend it here.",
        "Great notes come from patient re-reading, not rushed skimming.",
        "The goal isn't zero distractions — it's returning after every one.",
        "Ten focused minutes will outperform an hour of half-attention.",
        "You don't need motivation to start — you need a habit that doesn't ask.",
        "Reading regularly rewires how you think, not just what you know.",
        "One more page is always within reach.",
        "The hardest part of studying is opening the document. You already did it.",
        "Marking a page as read is a small, real win. Take it.",
        "Consistency turns 'someday' into 'done'.",
        "You are one page closer than you were yesterday.",
        "Great readers aren't fast — they're relentless.",
        "A quiet fifteen minutes can outweigh a noisy hour.",
        "The book doesn't move. You do.",
        "Your streak is proof, not pressure.",
        "Today's reading is tomorrow's shortcut.",
        "Slow comprehension beats fast forgetting.",
        "Every highlight is a note to your future self: this mattered.",
        "You build knowledge the same way you build anything — brick by brick.",
        "The page you're avoiding is usually the one you need most.",
        "Discipline is choosing to read even on the days you don't feel like it.",
        "Study sessions don't need to be long — they need to happen.",
        "A five-minute read is still a read. Don't disqualify small wins.",
        "You're not racing anyone. You're only competing with yesterday's you.",
        "The best time to review your notes was yesterday. The second best is now.",
        "Reading with a pen in hand turns pages into progress.",
        "Understanding one idea deeply beats skimming ten shallowly.",
        "Consistency is unglamorous. It's also unbeatable.",
        "Every session you show up for rewires what 'normal' looks like.",
        "You don't need the perfect mood to read — just the next five minutes.",
        "Bookmarks aren't clutter. They're a map of what mattered.",
        "The document isn't getting shorter by staring at it. Open it.",
        "Reading regularly is one of the quietest superpowers there is.",
        "Momentum is built in ordinary, unremarkable sessions like this one.",
        "You already know how to finish — you did it before. Do it again.",
        "A single clear page is worth more than ten skimmed ones.",
        "Your best study session is rarely your longest — it's your calmest.",
        "Focus isn't the absence of distraction. It's returning anyway.",
        "The page in front of you doesn't know how tired you are. Read it anyway.",
        "Great comprehension starts with a willingness to reread.",
        "You're allowed to go slowly. You're not allowed to stop.",
        "Small, boring, repeated effort is how real understanding gets built.",
        "The best highlight is the one you can still explain a week later.",
        "Today's streak day is tomorrow's evidence that you follow through.",
        "Reading with curiosity turns study into discovery.",
        "You don't need a perfect plan — you need the next open page.",
        "One clear idea today is worth more than ten unclear ones.",
        "Consistency is a compliment you pay to your future self.",
        "The page doesn't judge how long it took you to get here.",
        "Every finished chapter started as a single reluctant page one.",
        "Your notes today are a gift to the version of you who forgets.",
        "There is no wrong pace, only the pace you actually keep.",
        "A short session done is worth more than a long session imagined.",
        "The habit of opening the app is half the battle. You already won it.",
        "Understanding is built quietly, without applause, page after page.",
        "You read for clarity, not for speed. Let that be enough today.",
        "A streak isn't magic — it's just showing up, again.",
        "The best study tool is the one you actually use. You're using it.",
        "Every page read is a small act of trust in your future self.",
        "Progress rarely feels dramatic while it's happening. Keep going.",
        "You don't have to finish today. You just have to continue.",
        "Reading closely once beats reading quickly three times.",
        "The document is patient. Be patient with yourself too.",
        "A calm, focused ten minutes beats a rushed, distracted hour.",
        "Your attention span is a muscle — this page is today's rep.",
        "Small wins, repeated daily, become identity.",
        "You're not starting over — you're picking up exactly where you left off.",
        "The best time to read was this morning. The next best time is now.",
        "One well-understood page beats ten half-read ones.",
        "Keep opening the page. The habit is the whole point."
    };

    public static String getTodayQuote() {
        long dayNumber = localDayNumber();
        int index = (int) Math.floorMod(dayNumber, (long) QUOTES.length);
        return QUOTES[index];
    }

    /**
     * Computes a day number that increments exactly at LOCAL midnight,
     * not UTC midnight — otherwise the quote would appear to change at a
     * fixed, timezone-dependent hour of the day rather than overnight.
     */
    private static long localDayNumber() {
        Calendar cal = Calendar.getInstance();
        long shiftedMillis = cal.getTimeInMillis()
                + cal.get(Calendar.ZONE_OFFSET)
                + cal.get(Calendar.DST_OFFSET);
        return shiftedMillis / 86_400_000L;
    }
}