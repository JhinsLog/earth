package com.earth.incident;

import com.earth.config.SeoulIncidentProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 서울시 실시간 돌발정보를 받는다.
 *
 * <p><b>목록 전체를 다 받았는지</b>를 함께 돌려준다. 동기화는 "이번 목록에 없는 별은 끝났다"고 판단하는데, 페이지 하나가
 * 실패한 채 비교하면 진행 중인 돌발이 무더기로 꺼진다. 응답의 총 건수와 실제로 읽은 건수가 같을 때만 완전한 목록이다.
 *
 * <p><b>인증키가 주소 경로에 들어간다</b>(이 API의 형식). RestClient 예외 메시지에는 요청 주소가 그대로 실리므로, 예외를
 * 그대로 던지거나 로그로 남기지 않고 키를 지운 메시지로 바꿔 던진다.
 */
@Component
public class SeoulIncidentClient {

    // 평소 수십~수백 건(운영 1,000건씩). 응답이 이상하게 커서 메모리를 채우지 않게 막는다.
    private static final int MAX_PAGES = 10;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    /** 응답 한 번의 결과. {@code complete}가 false면 목록 일부만 받은 것이다. */
    public record Snapshot(List<SeoulIncident> incidents, boolean complete) {
    }

    private final SeoulIncidentProperties properties;
    private final RestClient rest;

    public SeoulIncidentClient(SeoulIncidentProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    public Snapshot fetch() {
        List<SeoulIncident> all = new ArrayList<>();
        int total = -1;
        int received = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            int start = page * properties.pageSize() + 1;
            Page result = parse(get(start, start + properties.pageSize() - 1));
            total = result.total();
            received += result.rows();
            all.addAll(result.incidents());
            if (received >= total || result.rows() == 0) {
                break;
            }
        }
        return new Snapshot(List.copyOf(all), total >= 0 && received == total);
    }

    private byte[] get(int start, int end) {
        URI uri = URI.create("%s/%s/xml/AccInfo/%d/%d/".formatted(properties.baseUrl(), properties.apiKey(), start, end));
        try {
            byte[] body = rest.get().uri(uri).retrieve().body(byte[].class);
            return body == null ? new byte[0] : body;
        } catch (RuntimeException e) {
            throw new IllegalStateException("서울시 돌발정보 요청 실패: " + e.getClass().getSimpleName() + " "
                    + redact(e.getMessage()));
        }
    }

    private String redact(String message) {
        if (message == null) return "";
        String key = properties.apiKey();
        return key == null || key.isBlank() ? message : message.replace(key, "***");
    }

    /** @param rows 받은 행 수. 좌표가 깨져 건너뛴 행도 센다 — 완전한 목록인지는 받은 행 수로 판단한다. */
    record Page(int total, int rows, List<SeoulIncident> incidents) {
    }

    /**
     * 응답 XML을 읽는다. 정상은 {@code INFO-000}, 진행 중인 돌발이 하나도 없으면 {@code INFO-200}(빈 목록)이고,
     * 그 밖의 코드(키 오류 등)는 예외다.
     */
    static Page parse(byte[] xml) {
        Document doc = readXml(xml);
        Element root = doc.getDocumentElement();
        String code = text(root, "CODE");
        if ("INFO-200".equals(code)) {
            return new Page(0, 0, List.of());
        }
        if (!"INFO-000".equals(code)) {
            throw new IllegalStateException("서울시 돌발정보 응답 오류: " + code + " " + text(root, "MESSAGE"));
        }
        int total = Integer.parseInt(text(root, "list_total_count").trim());
        NodeList rows = root.getElementsByTagName("row");
        List<SeoulIncident> incidents = new ArrayList<>(rows.getLength());
        for (int i = 0; i < rows.getLength(); i++) {
            Element row = (Element) rows.item(i);
            try {
                incidents.add(new SeoulIncident(
                        text(row, "acc_id").trim(),
                        text(row, "acc_type").trim(),
                        text(row, "acc_dtype").trim(),
                        dateTime(text(row, "occr_date"), text(row, "occr_time")),
                        dateTime(text(row, "exp_clr_date"), text(row, "exp_clr_time")),
                        Double.parseDouble(text(row, "grs80tm_x").trim()),
                        Double.parseDouble(text(row, "grs80tm_y").trim()),
                        text(row, "acc_info")));
            } catch (NumberFormatException e) {
                // 좌표가 없는 행은 지도에 찍을 수 없어 이 행만 건너뛴다.
            }
        }
        return new Page(total, rows.getLength(), incidents);
    }

    /**
     * 날짜 8자리 + 시각. 시각이 4자리(HHmm)인 행과 6자리(HHmmss)인 행이 섞여 온다(2026-11-07 실제 응답:
     * "1000"과 "130000"). 둘 다 받고, 그 밖의 형식은 null로 둔다.
     */
    static Instant dateTime(String date, String time) {
        try {
            LocalDate d = LocalDate.parse(date.trim(), DATE);
            String t = time.trim();
            LocalTime lt = switch (t.length()) {
                case 4 -> LocalTime.of(Integer.parseInt(t.substring(0, 2)), Integer.parseInt(t.substring(2, 4)));
                case 6 -> LocalTime.of(Integer.parseInt(t.substring(0, 2)), Integer.parseInt(t.substring(2, 4)),
                        Integer.parseInt(t.substring(4, 6)));
                default -> null;
            };
            return lt == null ? null : d.atTime(lt).atZone(SEOUL).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 외부 XML이라 DTD와 외부 엔티티를 막는다(XXE). 기본 설정의 DocumentBuilder는 DOCTYPE의 외부 엔티티를 따라가
     * 서버 파일을 읽거나 내부망으로 요청을 보낼 수 있다. OWASP XXE 방지 지침의 JAXP 설정이다.
     */
    private static Document readXml(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new IllegalStateException("서울시 돌발정보 응답을 읽지 못했습니다: " + e.getMessage(), e);
        }
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
    }
}
