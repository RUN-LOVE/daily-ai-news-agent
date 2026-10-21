package com.newsagent.news;

import com.newsagent.source.NewsSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RssParser {

    private static final int MAX_ITEMS_PER_FEED = 20;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_SUMMARY_LENGTH = 500;
    private static final int MAX_DATE_LENGTH = 60;

    private RssParser() {
    }

    public static List<NewsItem> parse(byte[] body, NewsSource source, int limit) {
        Document document = readDocument(body);
        NodeList nodes = document.getElementsByTagName("item");
        if (nodes.getLength() == 0) {
            nodes = document.getElementsByTagName("entry");
        }

        int maxItems = Math.min(Math.max(limit, 1), MAX_ITEMS_PER_FEED);
        List<NewsItem> items = new ArrayList<>();
        for (int index = 0; index < nodes.getLength() && items.size() < maxItems; index++) {
            Node node = nodes.item(index);
            if (!(node instanceof Element element)) {
                continue;
            }

            String title = cleanText(childText(element, "title"), MAX_TITLE_LENGTH);
            if (title.isEmpty()) {
                continue;
            }
            String link = safeLink(linkOf(element));
            String publishedAt = normalizeDate(childText(element,
                    "pubDate", "published", "updated", "dc:date"));
            String summary = cleanText(childText(element,
                    "description", "summary", "content:encoded", "content"), MAX_SUMMARY_LENGTH);

            items.add(new NewsItem(
                    source.id(),
                    source.name(),
                    title,
                    link,
                    publishedAt,
                    summary
            ));
        }
        return items;
    }

    private static Document readDocument(byte[] body) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(body));
        } catch (ParserConfigurationException | SAXException | IOException exception) {
            throw new NewsFetchException("RSS 解析失败: " + exception.getMessage(), exception);
        }
    }

    private static String linkOf(Element item) {
        String rssLink = childText(item, "link").trim();
        if (!rssLink.isEmpty()) {
            return rssLink;
        }

        for (Element child : childElements(item)) {
            if (!"link".equalsIgnoreCase(child.getTagName())) {
                continue;
            }
            String href = child.getAttribute("href").trim();
            String rel = child.getAttribute("rel");
            if (!href.isEmpty() && (rel.isEmpty() || "alternate".equalsIgnoreCase(rel))) {
                return href;
            }
        }
        return "";
    }

    private static String childText(Element parent, String... names) {
        for (Element child : childElements(parent)) {
            for (String name : names) {
                if (name.equalsIgnoreCase(child.getTagName())) {
                    String text = child.getTextContent();
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                }
            }
        }
        return "";
    }

    private static List<Element> childElements(Element parent) {
        List<Element> elements = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }

    private static String normalizeDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String value = raw.trim();
        try {
            return OffsetDateTime.parse(value).toString();
        } catch (RuntimeException ignored) {
            // Try the other common RSS/Atom date formats.
        }
        try {
            return ZonedDateTime.parse(value).toString();
        } catch (RuntimeException ignored) {
            // Try the other common RSS/Atom date formats.
        }
        try {
            return Instant.parse(value).toString();
        } catch (RuntimeException ignored) {
            // Try the other common RSS/Atom date formats.
        }
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toString();
        } catch (RuntimeException ignored) {
            return truncate(value, MAX_DATE_LENGTH);
        }
    }

    private static String safeLink(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            URI uri = new URI(raw.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return scheme.equals("http") || scheme.equals("https") ? uri.toString() : "";
        } catch (Exception exception) {
            return "";
        }
    }

    private static String cleanText(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = raw
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replaceAll("\\s+", " ")
                .trim();
        return truncate(text, maxLength);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "…";
    }
}
