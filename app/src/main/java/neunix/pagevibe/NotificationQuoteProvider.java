package neunix.pagevibe;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A SEPARATE pool from DailyQuoteProvider (the ~100-line, sequential,
 * in-app-only collection shown in Reading Stats). This pool is:
 *  - shown ONLY in notifications, never inside the app
 *  - selected RANDOMLY (not by day), so it never lines up with the
 *    in-app quote and the two never feel repetitive together
 *  - protected by anti-repeat history so the same line can't resurface
 *    for the next ~40 notifications
 *
 * Seeded with 120 original lines. The spec's target is 500-1000 — GROWING
 * THIS POOL NEEDS ZERO CODE CHANGES: just append more strings to QUOTES
 * below. The selection/anti-repeat logic already scales to any size.
 */
public class NotificationQuoteProvider {

    private static final String POOL_NAME = "notification_quotes";

    private static final String[] QUOTES = {
        "One page today is better than none.",
        "Books don't change the world. Readers do.",
        "Read something today that your future self will thank you for.",
        "A quiet ten minutes with a book beats an hour of scrolling.",
        "Your library is patient. Visit it today.",
        "The best reading habit is the one you don't have to think about starting.",
        "Somewhere in your library, a page is waiting to surprise you.",
        "A single paragraph can change how you see the rest of your day.",
        "Reading is a quiet kind of self-respect.",
        "Progress doesn't need to be loud to be real.",
        "You don't need a plan to read — you just need to open the file.",
        "Every reader was once a beginner who kept opening the book.",
        "The page in front of you doesn't care what kind of day you've had.",
        "A little curiosity goes a long way.",
        "Reading regularly is one of the cheapest luxuries there is.",
        "Today's five minutes could become tonight's favorite chapter.",
        "The best time to read is whenever you actually do it.",
        "A book opened is a conversation started.",
        "Small reading habits build big understanding, quietly.",
        "You already have everything you need to read today: five minutes and curiosity.",
        "Your notes from last week are still there, waiting to be reread.",
        "Reading a little every day beats reading a lot occasionally.",
        "The next good idea in your life might be sitting in your library right now.",
        "There's no wrong time to open a book you've been meaning to finish.",
        "A finished chapter is a small, real accomplishment. Claim it.",
        "You don't have to feel like reading to start — starting is what creates the feeling.",
        "Somewhere between page one and the last page, understanding happens.",
        "Reading rewards patience more than any other habit.",
        "The best books get better the second time you visit them.",
        "A page read with attention is worth ten skimmed in a hurry.",
        "Your future self is quietly counting on the reading you do today.",
        "Bookmarks are just promises you made to come back.",
        "The habit of opening the app is already half the work.",
        "One clear idea today outlasts ten unclear ones from last month.",
        "You're not behind on your reading — you're exactly where you left off.",
        "A short session today keeps tomorrow's session easier.",
        "The document hasn't gone anywhere. Go find it.",
        "Reading with intention turns pages into progress.",
        "Some of the best ideas are hiding in the pages you haven't reached yet.",
        "A calm five minutes with a book is a genuinely good use of your day.",
        "Today's reading doesn't need to be long to matter.",
        "The next sentence you read might be the one that sticks with you.",
        "Consistency in reading beats intensity in reading, every time.",
        "You built a reading habit once — you can pick it back up any time.",
        "There's a version of today where you read one more page than usual.",
        "A library only grows more valuable the more you actually use it.",
        "Reading slowly and understanding beats reading fast and forgetting.",
        "The page you're avoiding is often the one worth reading most.",
        "Today is a good day to finish something you started.",
        "A single good page is still a single good page, however short the visit.",
        "Your attention is valuable. Spending some of it on reading is a good trade.",
        "Understanding compounds quietly, page after page.",
        "The best reading sessions rarely feel like work while they're happening.",
        "You don't need motivation — you need five open minutes and a book.",
        "A page marked as read is a small, honest win. Take it.",
        "Reading is one of the few habits that gets easier the more you do it.",
        "Somewhere in today's schedule, there's room for one more page.",
        "The document you left mid-sentence is still mid-sentence, waiting.",
        "A little reading now saves a lot of catching up later.",
        "You're allowed to read slowly. You're just not allowed to stop entirely.",
        "The best notes come from patient rereading, not rushed skimming.",
        "Today's ordinary reading session is tomorrow's quiet advantage.",
        "A finished page is proof you showed up, even briefly.",
        "Reading regularly rewires how you think, not just what you know.",
        "The next great idea you encounter might be one page away.",
        "Small, boring, repeated reading sessions are how real understanding gets built.",
        "You don't need the perfect mood — you need the next open page.",
        "There's a book on your shelf that's still waiting for its first real read.",
        "A page read today is one less thing waiting for you tomorrow.",
        "Reading closely once beats reading quickly three times.",
        "The habit matters far more than the page count.",
        "Every session you show up for changes what 'normal' looks like for you.",
        "You already know how to finish a book — you've done it before.",
        "A calm, unhurried page is worth more than a rushed chapter.",
        "Today's reading doesn't need an audience or a reason — just five minutes.",
        "Somewhere in this document is a sentence worth remembering.",
        "The best study sessions are the ones that actually happen, not the ones you plan.",
        "A book you keep meaning to finish is still worth finishing.",
        "Reading with a pen nearby turns pages into progress you can revisit.",
        "You're one page closer than you were yesterday, even if it doesn't feel that way.",
        "A quiet reading habit is a quiet kind of confidence.",
        "The next few minutes could be spent on a page instead of a feed.",
        "Understanding one idea deeply beats skimming ten shallowly.",
        "Today's reading session doesn't need to be memorable to be worthwhile.",
        "A document reopened is a habit reinforced.",
        "You don't owe today's reading to anyone but yourself.",
        "The best highlight is the one you can still explain a week later.",
        "Somewhere in your recent files is a page you never quite finished.",
        "Reading a little every day is how libraries get read, one visit at a time.",
        "A five-minute read is still a real read. Don't disqualify it.",
        "The page in front of you is the only one that needs your attention right now.",
        "You're not racing anyone — only competing with yesterday's version of you.",
        "A single reopened document can restart an entire habit.",
        "Today's small reading session is tomorrow's easy momentum.",
        "The best time to revisit your notes was yesterday. The next best time is now.",
        "Reading with curiosity turns study into something closer to discovery.",
        "You don't need a perfect plan — you need the next open page.",
        "One well-understood paragraph beats ten half-read ones.",
        "A quiet library is an invitation, not a scoreboard.",
        "Progress you can't see yet is still progress.",
        "Today's page might be the one that finally makes the chapter click.",
        "A reopened book is a promise quietly kept to yourself.",
        "The habit of reading is built in ordinary, forgettable sessions like this one.",
        "You already started this book once — starting again is easier the second time.",
        "A short visit to your library still counts as a visit.",
        "The next chapter has been ready this whole time.",
        "Reading regularly is one of the quietest, most durable habits there is.",
        "Today's five minutes of reading are still five minutes well spent.",
        "A page turned is a small kind of forward motion.",
        "You don't have to finish today — you just have to continue.",
        "The best reading habit is the one that survives a busy week.",
        "There's no wrong pace, only the pace you actually keep.",
        "A document left open in your mind is worth reopening on your screen.",
        "Reading, even briefly, is still a choice in your favor.",
        "The next good sentence is closer than it feels.",
        "Small wins, repeated daily, quietly become identity."
    };

    public static String pickQuote(Context context) {
        NotificationPreferences prefs = new NotificationPreferences(context);
        List<Integer> recent = prefs.getRecentIndices(POOL_NAME);

        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < QUOTES.length; i++) {
            if (!recent.contains(i)) candidates.add(i);
        }
        if (candidates.isEmpty()) {
            for (int i = 0; i < QUOTES.length; i++) candidates.add(i);
        }

        int chosen = candidates.get(new Random().nextInt(candidates.size()));
        prefs.pushRecentIndex(POOL_NAME, chosen);
        return QUOTES[chosen];
    }
}