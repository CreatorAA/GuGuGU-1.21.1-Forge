package online.pigeonshouse.gugugu.chat.processors.map;

import lombok.Builder;
import lombok.Data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Builder
@Data
public class LevelWaypoint {
    private static final Pattern XAERO_WAYPOINT_PATTERN = Pattern.compile(
            "xaero-waypoint:" +
                    "(?<name>[^:]+):" +
                    "(?<abbr>[^:]*):" +
                    "(?<x>-?\\d+):" +
                    "(?<y>-?\\d+):" +
                    "(?<z>-?\\d+):" +
                    "\\d+:" +
                    "(?<state>true|false):" +
                    "\\d+:" +
                    "(?:" +
                    "Internal-dim%(?<ns>[^\\$]+)\\$(?<w1>[^-]+)" +
                    "|" +
                    "Internal-(?<w2>[^-]+)" +
                    ")" +
                    "-waypoints.*"
    );

    private static final Pattern JOURNEY_MAP_PATTERN = Pattern.compile(
            ".*\\[\\s*name:\"(?<name>.*?)\"\\s*," +
                    "\\s*x:(?<x>-?\\d+)\\s*," +
                    "\\s*y:(?<y>-?\\d+)\\s*," +
                    "\\s*z:(?<z>-?\\d+)\\s*," +
                    "\\s*dim:(?<namespace>[^:]+):(?<world>[^\\]]+)\\s*].*"
    );

    private final String name;
    private final String abbreviation;
    private final double x, y, z;
    private final boolean state;
    private final String namespace;
    private final String worldName;

    public static LevelWaypoint xaeroParse(String input) {
        Matcher m = XAERO_WAYPOINT_PATTERN.matcher(input);
        if (!m.matches()) {
            throw new IllegalArgumentException("输入不符合 Xaero waypoint 格式: " + input);
        }

        String name = m.group("name");
        String abbr = m.group("abbr");
        int x = Integer.parseInt(m.group("x"));
        int y = Integer.parseInt(m.group("y"));
        int z = Integer.parseInt(m.group("z"));
        boolean state = Boolean.parseBoolean(m.group("state"));

        String namespace;
        String world;
        if (m.group("ns") != null) {
            namespace = m.group("ns");
            world = m.group("w1");
        } else {
            namespace = "minecraft";
            world = m.group("w2");
        }

        return LevelWaypoint.builder()
                .name(name)
                .abbreviation(abbr.substring(0, 1))
                .x(x).y(y).z(z)
                .state(state)
                .namespace(namespace)
                .worldName(world)
                .build();
    }


    public static LevelWaypoint journeyMapParse(String input) {
        Matcher m = JOURNEY_MAP_PATTERN.matcher(input);
        if (!m.matches()) {
            throw new IllegalArgumentException("输入不符合 JourneyMap waypoint 格式: " + input);
        }
        String name = m.group("name");
        int x = Integer.parseInt(m.group("x"));
        int y = Integer.parseInt(m.group("y"));
        int z = Integer.parseInt(m.group("z"));
        String namespace = m.group("namespace");
        String world = m.group("world");

        return LevelWaypoint.builder()
                .name(name)
                .abbreviation(name.substring(0, 1))
                .x(x).y(y).z(z)
                .state(true)
                .namespace(namespace)
                .worldName(world)
                .build();
    }

    public String getXaeroWaypointString() {
        return String.format("xaero-waypoint:%s:%s:%d:%d:%d:0:%b:0:Internal-dim%%%s$%s-waypoints",
                name, abbreviation, (int) x, (int) y, (int) z, state, namespace, worldName);

    }

    public String getJourneyMapWaypointString() {
        String dim = namespace + ":" + worldName;
        return String.format("[name:\"%s\", x:%d, y:%d, z:%d, dim:\"%s\"]",
                name, (int) x, (int) y, (int) z, dim);
    }
}
