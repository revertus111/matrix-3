package game.console;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CommandCatalog {

    public static final String CATEGORY_ALL = "All";
    public static final String CATEGORY_PUNISHMENT = "Punishment";
    public static final String CATEGORY_ADMIN = "Admin";
    public static final String CATEGORY_MODERATOR = "Moderator";
    public static final String CATEGORY_SUPPORT = "Support";
    public static final String CATEGORY_SPAWN = "Spawn";
    public static final String CATEGORY_PLAYER = "Player";

    private static final String[] PUNISHMENT = {
        "teletome", "colour", "unban", "unmute", "punish", "forcekick"
    };

    private static final String[] ADMIN = {
        "emusic", "it", "wave", "hint", "praybook", "qbd", "fade", "task", "pmsg", "ptest", "girl", "randomevent",
        "book", "corruptxp", "anon", "tvtcrt", "lmscrt", "lmsjoin", "evearena", "costumecolor", "comp", "blatest",
        "setprice", "decantt", "floorf", "leak", "startlms", "starttvt", "startevent", "reloadshops", "shop",
        "stopevent", "resethouse", "pestpoints", "hide", "maxdung", "empty", "sprite", "prjdebugmisc",
        "prjdebugheight", "prjdebugdelay", "nextclue", "forceitem", "prjdebugemote", "startprjdebug",
        "resetbarrows", "stopprjdebug", "enablebxp", "disablebxp", "stopsupertroll", "checkpin", "resetotheracc",
        "scshop", "clipflag", "walkto", "givespinsall", "loyalty", "getspins", "ugd", "ss2", "sendscriptblank",
        "script", "script1", "script2", "ss", "testresetsof", "sendsofempty", "sendsofitems", "senditems",
        "forcewep", "clearst", "ectest", "ecotestcutscene", "istest", "st", "addpoints", "testdeath", "myindex",
        "gw", "getspawned", "removeobjects", "clearspot", "switchyell", "switchbadboy", "clearall", "reward",
        "getclipflag", "scbariertest", "startscblue", "startscred", "hugemap", "normmap", "testmap", "test",
        "testscarea", "sgar", "scg", "gesearch", "ge", "ge2", "ge3", "configsize", "npcmask", "runespan", "house",
        "killingfields", "isprite", "pptest", "sd", "debugobjects", "telesupport", "telemods", "telestaff",
        "teleallfree", "pickuppet", "canceltask", "messagetest", "restartfp", "modelid", "pos", "agilitytest",
        "scare", "partyroom", "objectname", "bork", "killnpc", "sound", "music", "testdialogue", "removenpcs",
        "resetkdr", "newtut", "removecontroler", "nomads", "item", "copy", "god", "prayertest", "karamja",
        "clanwars", "watereast", "dungsmall", "dung", "dungtest", "objects", "checkdisplay", "coords", "ccoords",
        "hash", "itemoni", "items", "trade", "setlevel", "npc", "loadwalls", "cwbase", "object", "ltab", "otab",
        "tab", "killme", "hidec", "string", "istringl", "istring", "iconfig", "nisvar", "var", "forcemovement",
        "ab", "varbit", "hit", "menu", "iloop", "hloop", "varloop", "varloop2", "varbitloop", "objectanim",
        "gitem", "loopoanim", "bconfigloop", "reset", "build", "givexp", "pintest", "givetokens", "master",
        "addxp", "window", "bconfig", "tonpc", "inter", "pane", "overlay", "resetprices", "recalcprices",
        "interh2", "interh", "inters", "kill", "killall", "bank", "shutdown", "emote", "remote", "quake",
        "getrender", "spec", "setlook", "color", "tryinter", "tryanim", "animcount", "trygfx", "gfx", "gfxp",
        "sync", "mess", "staffmeeting", "fightkiln", "cutscene", "dzs", "noescape", "dungcoords", "tloop"
    };

    private static final String[] MODERATOR = {
        "teleto", "sendhome"
    };

    private static final String[] SUPPORT = {
        "sz", "staffzone", "dz", "donatorzone", "realnames", "sy", "staffyell", "ticket", "endticket"
    };

    private static final String[] SPAWN = {
        "sets", "veng", "barrage", "dharok", "home", "itemn", "restore", "blueskin", "greenskin", "pray", "curses",
        "regular", "spellbook", "modern", "ancient", "lunar", "pots", "food", "wolp", "multi", "clw", "zerk",
        "50ports", "gdz", "mb", "magebank", "easts", "wests", "wild", "wilderness", "obelisk"
    };

    private static final String[] PLAYER = {
        "mode", "score", "kdr", "players", "checkvote", "claim", "claimvote", "help", "wiki", "vote", "hs",
        "highscores", "donate", "itemdb", "commands", "itemlist", "website", "livechat", "reportplayer",
        "reportbug", "recoverpass", "appealoffence", "checkreport", "checkappeal", "rules", "guides", "thread",
        "yell", "switchitemslook", "sil"
    };

    private static final List<Entry> ENTRIES;
    private static final Map<String, String> USAGE = new HashMap<String, String>();
    private static final Set<String> DANGEROUS = new HashSet<String>();

    static {
        USAGE.put("item", "<id> [amount]");
        USAGE.put("itemn", "<item name> [+amount]");
        USAGE.put("npc", "<id>");
        USAGE.put("object", "<id> [type] [rotation]");
        USAGE.put("tele", "<x> <y> [plane]");
        USAGE.put("teleto", "<player>");
        USAGE.put("teletome", "<player>");
        USAGE.put("sendhome", "<player>");
        USAGE.put("emote", "<id>");
        USAGE.put("remote", "<id>");
        USAGE.put("gfx", "<id> [delay] [height]");
        USAGE.put("gfxp", "<id>");
        USAGE.put("sync", "<animId> <gfxId> [height]");
        USAGE.put("animcount", "");
        USAGE.put("sound", "<soundId>");
        USAGE.put("music", "<musicId>");
        USAGE.put("emusic", "<effectId>");
        USAGE.put("setlevel", "<skillId> <level>");
        USAGE.put("master", "[skillId]");
        USAGE.put("inter", "<interfaceId>");
        USAGE.put("pane", "<interfaceId>");
        USAGE.put("overlay", "<interfaceId> [child]");
        USAGE.put("shop", "<shopId>");
        USAGE.put("modelid", "<itemId>");
        USAGE.put("killnpc", "<npcId>");
        USAGE.put("removenpcs", "<npcId>");
        USAGE.put("forcekick", "<player>");
        USAGE.put("unban", "<player>");
        USAGE.put("unmute", "<player>");
        USAGE.put("punish", "<player>");
        USAGE.put("kill", "<player>");
        USAGE.put("thread", "<threadId>");
        USAGE.put("yell", "<message>");
        USAGE.put("staffyell", "<message>");
        USAGE.put("sy", "<message>");
        USAGE.put("colour", "<a> <b> <c> <d> <e>");
        USAGE.put("forceitem", "<player> <itemId> <amount>");
        USAGE.put("walkto", "<x> <y> [checked]");
        USAGE.put("givespinsall", "<type> <amount>");
        USAGE.put("getspins", "<type> [amount]");
        USAGE.put("script", "<scriptId>");
        USAGE.put("script1", "<scriptId> <arg1>");
        USAGE.put("script2", "<scriptId> <arg1> <arg2>");
        USAGE.put("sendscriptblank", "<scriptId>");
        USAGE.put("forcewep", "<itemId>");
        USAGE.put("canceltask", "<player>");
        USAGE.put("isprite", "<interface> <component> <sprite>");
        USAGE.put("testdialogue", "<id>");
        USAGE.put("trade", "<player>");
        USAGE.put("ltab", "<tabId>");
        USAGE.put("otab", "<tabId>");
        USAGE.put("tab", "<slot> <interfaceId>");
        USAGE.put("hidec", "<interfaceId> <componentId> <hidden>");
        USAGE.put("string", "<interfaceId> <maxComponent>");
        USAGE.put("istring", "<id> <value>");
        USAGE.put("istringl", "<id> <value>");
        USAGE.put("iconfig", "<id> <value>");
        USAGE.put("nisvar", "<id> <value>");
        USAGE.put("var", "<id> <value>");
        USAGE.put("varbit", "<id> <value>");
        USAGE.put("hit", "<mark>");
        USAGE.put("menu", "<id> <value>");
        USAGE.put("bconfig", "<id> <value>");
        USAGE.put("tonpc", "<id | -1>");
        USAGE.put("givexp", "<skillId> <xp> <player>");
        USAGE.put("givetokens", "<amount> <player>");
        USAGE.put("shutdown", "[delaySeconds]");
        USAGE.put("quake", "<a> <b> <c> <d> <e>");
        USAGE.put("setlook", "<setId>");
        USAGE.put("mess", "<type>");
        USAGE.put("pmsg", "<text> <type>");
        USAGE.put("ptest", "<amount>");
        USAGE.put("book", "<bookId>");
        USAGE.put("corruptxp", "<skillId> <username>");
        USAGE.put("setprice", "<itemId> <price>");
        USAGE.put("prjdebugmisc", "<slope> <angle>");
        USAGE.put("prjdebugheight", "<start> <end>");
        USAGE.put("prjdebugdelay", "<delay> <speed>");
        USAGE.put("prjdebugemote", "<startAnim> <startGfx> <projectileGfx> <destAnim> <destGfx>");
        USAGE.put("startprjdebug", "<targetIndex> <intervalMs>");
        USAGE.put("npcmask", "<barId> <value> <max> <bool>");
        USAGE.put("objectanim", "<x> <y> [type] <anim>");
        USAGE.put("ab", "<value>");
        USAGE.put("cutscene", "<cutsceneId>");
        USAGE.put("tloop", "<startSlot> <endSlot> <interfaceId>");
        USAGE.put("dungcoords", "");
        USAGE.put("dzs", "");
        USAGE.put("noescape", "");
        DANGEROUS.add("clearall");
        DANGEROUS.add("clearspot");
        DANGEROUS.add("clearst");
        DANGEROUS.add("empty");
        DANGEROUS.add("forcekick");
        DANGEROUS.add("kill");
        DANGEROUS.add("killall");
        DANGEROUS.add("killme");
        DANGEROUS.add("punish");
        DANGEROUS.add("recalcprices");
        DANGEROUS.add("removenpcs");
        DANGEROUS.add("removeobjects");
        DANGEROUS.add("reset");
        DANGEROUS.add("resethouse");
        DANGEROUS.add("resetotheracc");
        DANGEROUS.add("resetprices");
        DANGEROUS.add("shutdown");
        DANGEROUS.add("stopevent");

        List<Entry> entries = new ArrayList<Entry>();
        add(entries, CATEGORY_PUNISHMENT, PUNISHMENT);
        add(entries, CATEGORY_ADMIN, ADMIN);
        add(entries, CATEGORY_MODERATOR, MODERATOR);
        add(entries, CATEGORY_SUPPORT, SUPPORT);
        add(entries, CATEGORY_SPAWN, SPAWN);
        add(entries, CATEGORY_PLAYER, PLAYER);
        Collections.sort(entries, new Comparator<Entry>() {
            @Override
            public int compare(Entry left, Entry right) {
                return left.name.compareTo(right.name);
            }
        });
        ENTRIES = Collections.unmodifiableList(entries);
    }

    private CommandCatalog() {
    }

    private static void add(List<Entry> entries, String category, String[] names) {
        for (String name : names) {
            String arguments = USAGE.get(name);
            entries.add(new Entry(name, category, arguments == null ? "[arguments]" : arguments,
                    DANGEROUS.contains(name)));
        }
    }

    public static List<Entry> getEntries() {
        return ENTRIES;
    }

    public static String[] getCategories() {
        return new String[] {
                CATEGORY_ALL,
                CATEGORY_ADMIN,
                CATEGORY_PUNISHMENT,
                CATEGORY_MODERATOR,
                CATEGORY_SUPPORT,
                CATEGORY_SPAWN,
                CATEGORY_PLAYER
        };
    }

    public static final class Entry {
        private final String name;
        private final String category;
        private final String arguments;
        private final boolean dangerous;

        private Entry(String name, String category, String arguments, boolean dangerous) {
            this.name = name;
            this.category = category;
            this.arguments = arguments;
            this.dangerous = dangerous;
        }

        public String getName() {
            return name;
        }

        public String getCategory() {
            return category;
        }

        public String getArguments() {
            return arguments;
        }

        public boolean isDangerous() {
            return dangerous;
        }

        public String getUsage() {
            if (arguments.length() == 0) {
                return "::" + name;
            }
            return "::" + name + " " + arguments;
        }
    }
}
