package com.fooddelivery.aimcpserver.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fooddelivery.aimcpserver.dto.Card;
import com.fooddelivery.aimcpserver.dto.Link;
import com.fooddelivery.aimcpserver.support.ToolText;

/** Builders for the three card kinds; they enforce the item cap and clean every text that came from data. */
public final class Cards {

    public static final String LEFT = "left";
    public static final String RIGHT = "right";

    private static final int TEXT = 80;

    private Cards() {
    }

    public static Card.Stat stat(String label, Object value, String hint) {
        return new Card.Stat(label, String.valueOf(value), hint);
    }

    public static Card.Stats stats(String title, List<Card.Stat> stats) {
        return new Card.Stats(title, stats);
    }

    public static Card.Column column(String key, String label, String align) {
        return new Card.Column(key, label, align);
    }

    public static Card.Table table(String title, List<Card.Column> columns, List<Map<String, Object>> rows) {
        List<Map<String, Object>> capped = rows.size() > Card.MAX_ITEMS ? rows.subList(0, Card.MAX_ITEMS) : rows;
        return new Card.Table(title, columns, List.copyOf(capped));
    }

    public static Card.Entities entities(String entityType, String title, List<Card.Item> items) {
        List<Card.Item> capped = items.size() > Card.MAX_ITEMS ? items.subList(0, Card.MAX_ITEMS) : items;
        return new Card.Entities(entityType, title, List.copyOf(capped));
    }

    /** meta: label/value pairs in order; pairs whose value is empty are left out. */
    public static Card.Item item(Object id, String title, String subtitle, List<String> badges, List<Card.Meta> meta,
                                 String imageUrl, Link link) {
        List<Card.Meta> shown = new ArrayList<>();
        for (Card.Meta entry : meta) {
            if (entry.value() != null && !entry.value().isBlank()) {
                shown.add(new Card.Meta(entry.label(), ToolText.clean(entry.value(), TEXT)));
            }
        }
        List<String> cleanBadges = badges.stream().filter(b -> b != null && !b.isBlank())
                .map(b -> ToolText.clean(b, 30)).toList();
        // Only https images are shown: no data: or javascript: URLs from data
        String image = imageUrl != null && imageUrl.startsWith("https://") ? imageUrl : null;
        return new Card.Item(id, ToolText.clean(title, TEXT),
                subtitle == null || subtitle.isBlank() ? null : ToolText.clean(subtitle, 120),
                cleanBadges, List.copyOf(shown), image, link);
    }

    public static Card.Meta meta(String label, String value) {
        return new Card.Meta(label, value);
    }
}
