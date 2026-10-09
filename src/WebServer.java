import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class WebServer {

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/clubs", new GetClubsHandler());
        server.createContext("/api/add-club", new AddClubHandler());
        server.createContext("/api/events", new GetEventsHandler());
        server.createContext("/api/event-detail", new GetEventDetailHandler());
        server.createContext("/api/add-event", new AddEventHandler());
        server.createContext("/api/update-event", new UpdateEventHandler());
        server.createContext("/api/delete-event", new DeleteEventHandler());
        server.createContext("/api/register", new RegisterEventHandler());
        server.createContext("/api/attendees", new GetAttendeesHandler());

        server.setExecutor(null);
        System.out.println("==================================================");
        System.out.println("🚀 Rajalakshmi Engineering College (REC) Hub");
        System.out.println("🚀 Server running on: http://localhost:8080");
        System.out.println("==================================================");
        server.start();
    }

    // Static Asset Delivery
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") || path.isEmpty()) path = "/index.html";

            File file = new File("web" + path);
            if (!file.exists() || file.isDirectory()) {
                String error = "404 Not Found: " + path;
                exchange.sendResponseHeaders(404, error.length());
                OutputStream os = exchange.getResponseBody();
                os.write(error.getBytes());
                os.close();
                return;
            }

            String contentType = "text/html; charset=UTF-8";
            if (path.endsWith(".png")) contentType = "image/png";
            else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) contentType = "image/jpeg";
            else if (path.endsWith(".svg")) contentType = "image/svg+xml";
            else if (path.endsWith(".css")) contentType = "text/css";
            else if (path.endsWith(".js")) contentType = "application/javascript";
            else if (path.endsWith(".ico")) contentType = "image/x-icon";

            byte[] bytes = Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    // GET /api/clubs
    static class GetClubsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder json = new StringBuilder("[");
            String sql = "SELECT * FROM clubs ORDER BY club_id ASC";
            try (Connection conn = DBConnection.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    json.append(String.format("{\"id\":%d,\"name\":\"%s\",\"tagline\":\"%s\",\"icon\":\"%s\"}",
                        rs.getInt("club_id"),
                        escapeJson(rs.getString("name")),
                        escapeJson(rs.getString("tagline")),
                        rs.getString("icon")
                    ));
                    first = false;
                }
            } catch (SQLException e) { e.printStackTrace(); }
            json.append("]");
            sendResponse(exchange, 200, "application/json; charset=UTF-8", json.toString());
        }
    }

    // POST /api/add-club
    static class AddClubHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> p = parseFormData(exchange.getRequestBody());
                String sql = "INSERT INTO clubs (name, tagline, icon) VALUES (?, ?, ?)";
                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, p.get("name"));
                    pstmt.setString(2, p.get("tagline"));
                    pstmt.setString(3, p.getOrDefault("icon", "ph-star"));
                    pstmt.executeUpdate();
                    sendResponse(exchange, 200, "text/plain; charset=UTF-8", "OK");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "text/plain; charset=UTF-8", "ERROR: " + e.getMessage());
                }
            }
        }
    }

    // GET /api/events
    static class GetEventsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            int clubId = 0;
            if (query != null && query.contains("clubId=")) {
                try { clubId = Integer.parseInt(query.split("clubId=")[1].split("&")[0]); } catch (Exception ignored) {}
            }

            StringBuilder json = new StringBuilder("[");
            String sql = "SELECT e.*, c.name AS club_name FROM events e JOIN clubs c ON e.club_id = c.club_id ";
            if (clubId > 0) sql += "WHERE e.club_id = " + clubId + " ";
            sql += "ORDER BY e.event_date ASC";

            try (Connection conn = DBConnection.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    json.append(String.format("{\"id\":%d,\"clubId\":%d,\"clubName\":\"%s\",\"title\":\"%s\",\"category\":\"%s\",\"date\":\"%s\",\"startTime\":\"%s\",\"endTime\":\"%s\",\"venue\":\"%s\",\"total\":%d,\"filled\":%d}",
                        rs.getInt("event_id"),
                        rs.getInt("club_id"),
                        escapeJson(rs.getString("club_name")),
                        escapeJson(rs.getString("title")),
                        rs.getString("category"),
                        rs.getString("event_date"),
                        rs.getString("start_time"),
                        rs.getString("end_time"),
                        escapeJson(rs.getString("venue")),
                        rs.getInt("seats_total"),
                        rs.getInt("seats_filled")
                    ));
                    first = false;
                }
            } catch (SQLException e) { e.printStackTrace(); }
            json.append("]");
            sendResponse(exchange, 200, "application/json; charset=UTF-8", json.toString());
        }
    }

    // GET /api/event-detail?id=X
    static class GetEventDetailHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            int id = 0;
            if (query != null && query.contains("id=")) {
                id = Integer.parseInt(query.split("id=")[1].split("&")[0]);
            }
            String sql = "SELECT e.*, c.name AS club_name FROM events e JOIN clubs c ON e.club_id = c.club_id WHERE e.event_id = ?";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, id);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    String json = String.format("{\"id\":%d,\"clubId\":%d,\"clubName\":\"%s\",\"title\":\"%s\",\"category\":\"%s\",\"date\":\"%s\",\"startTime\":\"%s\",\"endTime\":\"%s\",\"venue\":\"%s\",\"total\":%d,\"filled\":%d,\"ps\":\"%s\",\"rules\":\"%s\"}",
                        rs.getInt("event_id"),
                        rs.getInt("club_id"),
                        escapeJson(rs.getString("club_name")),
                        escapeJson(rs.getString("title")),
                        rs.getString("category"),
                        rs.getString("event_date"),
                        rs.getString("start_time"),
                        rs.getString("end_time"),
                        escapeJson(rs.getString("venue")),
                        rs.getInt("seats_total"),
                        rs.getInt("seats_filled"),
                        escapeJson(rs.getString("problem_statements") == null ? "" : rs.getString("problem_statements")),
                        escapeJson(rs.getString("rules") == null ? "" : rs.getString("rules"))
                    );
                    sendResponse(exchange, 200, "application/json; charset=UTF-8", json);
                    return;
                }
            } catch (SQLException e) { e.printStackTrace(); }
            sendResponse(exchange, 404, "text/plain; charset=UTF-8", "Event not found");
        }
    }

    // POST /api/add-event
    static class AddEventHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> p = parseFormData(exchange.getRequestBody());
                String sql = "INSERT INTO events (club_id, title, category, event_date, start_time, end_time, venue, seats_total, seats_filled, problem_statements, rules) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)";
                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, Integer.parseInt(p.get("clubId")));
                    pstmt.setString(2, p.get("title"));
                    pstmt.setString(3, p.get("category"));
                    pstmt.setString(4, p.get("date"));
                    pstmt.setString(5, p.get("startTime"));
                    pstmt.setString(6, p.get("endTime"));
                    pstmt.setString(7, p.get("venue"));
                    pstmt.setInt(8, Integer.parseInt(p.get("seats")));
                    pstmt.setString(9, p.get("problemStatements"));
                    pstmt.setString(10, p.get("rules"));
                    pstmt.executeUpdate();
                    sendResponse(exchange, 200, "text/plain; charset=UTF-8", "OK");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "text/plain; charset=UTF-8", "ERROR: " + e.getMessage());
                }
            }
        }
    }

    // POST /api/update-event (Admin edits date, time, venue, seats, problem statements & rules)
    static class UpdateEventHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> p = parseFormData(exchange.getRequestBody());
                String sql = "UPDATE events SET club_id=?, title=?, category=?, event_date=?, start_time=?, end_time=?, venue=?, seats_total=?, problem_statements=?, rules=? WHERE event_id=?";
                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, Integer.parseInt(p.get("clubId")));
                    pstmt.setString(2, p.get("title"));
                    pstmt.setString(3, p.get("category"));
                    pstmt.setString(4, p.get("date"));
                    pstmt.setString(5, p.get("startTime"));
                    pstmt.setString(6, p.get("endTime"));
                    pstmt.setString(7, p.get("venue"));
                    pstmt.setInt(8, Integer.parseInt(p.get("seats")));
                    pstmt.setString(9, p.get("problemStatements"));
                    pstmt.setString(10, p.get("rules"));
                    pstmt.setInt(11, Integer.parseInt(p.get("eventId")));
                    pstmt.executeUpdate();
                    sendResponse(exchange, 200, "text/plain; charset=UTF-8", "OK");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "text/plain; charset=UTF-8", "ERROR: " + e.getMessage());
                }
            }
        }
    }

    // POST /api/delete-event
    static class DeleteEventHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> p = parseFormData(exchange.getRequestBody());
                String sql = "DELETE FROM events WHERE event_id = ?";
                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, Integer.parseInt(p.get("id")));
                    pstmt.executeUpdate();
                    sendResponse(exchange, 200, "text/plain; charset=UTF-8", "DELETED");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "text/plain; charset=UTF-8", "ERROR: " + e.getMessage());
                }
            }
        }
    }

    // POST /api/register
    static class RegisterEventHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> p = parseFormData(exchange.getRequestBody());
                int eventId = Integer.parseInt(p.get("eventId"));
                String name = p.get("name").trim();
                String regNo = p.get("regNo").trim();
                String email = p.get("email").trim().toLowerCase();
                String dept = p.get("dept").trim();

                // Strict REC Email Domain Validation
                if (!email.endsWith("@rajalakshmi.edu.in")) {
                    sendResponse(exchange, 400, "text/plain; charset=UTF-8", 
                        "INVALID_EMAIL: Only official college email IDs (@rajalakshmi.edu.in) are allowed.");
                    return;
                }

                try (Connection conn = DBConnection.getConnection()) {
                    // Check duplicate registration
                    String checkSql = "SELECT reg_id FROM registrations WHERE event_id = ? AND (reg_number = ? OR student_email = ?)";
                    try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                        checkStmt.setInt(1, eventId);
                        checkStmt.setString(2, regNo);
                        checkStmt.setString(3, email);
                        ResultSet rs = checkStmt.executeQuery();
                        if (rs.next()) {
                            sendResponse(exchange, 409, "text/plain; charset=UTF-8", "DUPLICATE: You are already registered for this event.");
                            return;
                        }
                    }

                    // Check seats availability
                    String seatSql = "SELECT seats_filled, seats_total FROM events WHERE event_id = ?";
                    try (PreparedStatement seatStmt = conn.prepareStatement(seatSql)) {
                        seatStmt.setInt(1, eventId);
                        ResultSet rs = seatStmt.executeQuery();
                        if (rs.next() && rs.getInt("seats_filled") >= rs.getInt("seats_total")) {
                            sendResponse(exchange, 400, "text/plain; charset=UTF-8", "FULL: Event seats are full.");
                            return;
                        }
                    }

                    // Insert student registration
                    String insertSql = "INSERT INTO registrations (event_id, student_name, reg_number, student_email, department) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setInt(1, eventId);
                        insertStmt.setString(2, name);
                        insertStmt.setString(3, regNo);
                        insertStmt.setString(4, email);
                        insertStmt.setString(5, dept);
                        insertStmt.executeUpdate();
                    }

                    // Increment seat counter
                    String updateSql = "UPDATE events SET seats_filled = seats_filled + 1 WHERE event_id = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setInt(1, eventId);
                        updateStmt.executeUpdate();
                    }

                    sendResponse(exchange, 200, "text/plain; charset=UTF-8", "SUCCESS");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "text/plain; charset=UTF-8", "ERROR: " + e.getMessage());
                }
            }
        }
    }

    // GET /api/attendees?eventId=X
    static class GetAttendeesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            int eventId = 0;
            if (query != null && query.contains("eventId=")) {
                eventId = Integer.parseInt(query.split("eventId=")[1].split("&")[0]);
            }

            StringBuilder json = new StringBuilder("{");

            try (Connection conn = DBConnection.getConnection()) {
                String evSql = "SELECT e.*, c.name AS club_name FROM events e JOIN clubs c ON e.club_id = c.club_id WHERE e.event_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(evSql)) {
                    pstmt.setInt(1, eventId);
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) {
                        json.append(String.format("\"event\":{\"title\":\"%s\",\"club\":\"%s\",\"date\":\"%s\",\"startTime\":\"%s\",\"endTime\":\"%s\",\"venue\":\"%s\"},",
                            escapeJson(rs.getString("title")),
                            escapeJson(rs.getString("club_name")),
                            rs.getString("event_date"),
                            rs.getString("start_time"),
                            rs.getString("end_time"),
                            escapeJson(rs.getString("venue"))
                        ));
                    }
                }

                json.append("\"students\":[");
                String sql = "SELECT * FROM registrations WHERE event_id = ? ORDER BY reg_number ASC";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, eventId);
                    ResultSet rs = pstmt.executeQuery();
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) json.append(",");
                        json.append(String.format("{\"id\":%d,\"name\":\"%s\",\"regNo\":\"%s\",\"email\":\"%s\",\"dept\":\"%s\",\"time\":\"%s\"}",
                            rs.getInt("reg_id"),
                            escapeJson(rs.getString("student_name")),
                            rs.getString("reg_number"),
                            rs.getString("student_email"),
                            rs.getString("department"),
                            rs.getTimestamp("registered_at").toString()
                        ));
                        first = false;
                    }
                }
                json.append("]}");
            } catch (SQLException e) { e.printStackTrace(); }

            sendResponse(exchange, 200, "application/json; charset=UTF-8", json.toString());
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String contentType, String responseText) throws IOException {
        byte[] bytes = responseText.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static Map<String, String> parseFormData(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        String line = reader.readLine();
        Map<String, String> map = new HashMap<>();
        if (line != null) {
            String[] pairs = line.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=");
                if (kv.length == 2) {
                    map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                            URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                }
            }
        }
        return map;
    }
}