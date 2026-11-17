package com.earth.volcano;

import com.earth.config.VolcanoProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 주간 화산 활동 보고 RSS를 받아 분화 중인 화산만 고른다. 인증키가 필요 없다.
 *
 * <p>항목 제목이 "Krakatau (Indonesia) - Report for 8 October-14 October 2026 - New Eruptive Activity" 꼴이고, 좌표는
 * {@code georss:point}("위도 경도")에 있다. 제목 끝이 분화(New/Continuing Eruptive Activity)가 아닌 항목(불안정 징후 등)은
 * 버린다 — 분화가 아닌 것을 분화로 띄우지 않는다.
 */
@Component
public class VolcanoReportClient {

    private static final Pattern TITLE = Pattern.compile(
            "^(.+?) \\(([^)]+)\\) - Report for .+ - (New|Continuing) Eruptive Activity$");
    private static final Pattern VOLCANO_NUMBER = Pattern.compile("vn_(\\d+)");

    private final VolcanoProperties properties;
    private final RestClient rest;

    public VolcanoReportClient(VolcanoProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NORMAL).build());
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    public List<VolcanoReport> fetch() {
        byte[] body;
        try {
            body = rest.get().uri(properties.feedUrl()).retrieve().body(byte[].class);
        } catch (RuntimeException e) {
            throw new IllegalStateException("화산 보고 요청 실패: " + e.getClass().getSimpleName() + " " + e.getMessage(), e);
        }
        return parse(body == null ? new byte[0] : body);
    }

    static List<VolcanoReport> parse(byte[] body) {
        Document doc = read(body);
        NodeList items = doc.getElementsByTagName("item");
        List<VolcanoReport> reports = new ArrayList<>();
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            Matcher title = TITLE.matcher(text(item, "title").strip());
            Matcher number = VOLCANO_NUMBER.matcher(text(item, "guid"));
            String[] point = text(item, "georss:point").strip().split("\\s+");
            Instant published = published(text(item, "pubDate").strip());
            if (!title.matches() || !number.find() || point.length != 2 || published == null) continue;
            try {
                double lat = Double.parseDouble(point[0]);
                double lon = Double.parseDouble(point[1]);
                if (Math.abs(lat) > 90 || Math.abs(lon) > 180) continue;
                reports.add(new VolcanoReport(number.group(1), title.group(1).strip(), title.group(2).strip(),
                        title.group(3).equals("New"), lat, lon, published));
            } catch (NumberFormatException ignored) {
                // 좌표를 읽을 수 없는 항목은 건너뛴다.
            }
        }
        return reports;
    }

    /**
     * 외부 XML이라 DOCTYPE을 아예 받지 않는다(XXE — 외부 엔티티로 서버 파일을 읽거나 내부망을 찌르는 공격 — 를 막는 OWASP 권장 설정).
     * RSS에는 DOCTYPE이 필요 없다.
     */
    private static Document read(byte[] body) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(body));
        } catch (Exception e) {
            throw new IllegalStateException("화산 보고 RSS를 읽을 수 없습니다: " + e.getMessage(), e);
        }
    }

    private static String text(Element item, String tag) {
        NodeList nodes = item.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
    }

    private static Instant published(String raw) {
        try {
            return ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
