import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;

public class WebServer {

    public static void main(String[] args) throws IOException {
        int port = 8080;
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.isEmpty()) {
            port = Integer.parseInt(portEnv);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // API Endpoints
        server.createContext("/api/clubs", new ClubsHandler());
        server.createContext("/api/events", new EventsHandler());
        server.createContext("/api/event-detail", new EventDetailHandler());
        server.createContext("/api/register", new RegisterHandler());
        server.createContext("/api/add-event", new AddEventHandler());
        server.createContext("/api/update-event", new UpdateEventHandler());
        server.createContext("/api/delete-event", new DeleteEventHandler());
        server.createContext("/api/add-club", new AddClubHandler());
        server.createContext("/api/delete-club", new DeleteClubHandler());
        server.createContext("/api/attendees", new AttendeesHandler());

        // Static Files Handler
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null);
        System.out.println("REC Campus Event Hub Server running on port " + port);
        server.start();
    }

    // ==========================================
    // HTTP HANDLERS
    // ==========================================

    // GET /api/clubs
    static class ClubsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                StringBuilder json = new StringBuilder("[");
                String sql = "SELECT club_id, name, tagline, icon FROM clubs ORDER BY club_id ASC";

                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {

                    boolean first = true;
                    while (rs.next()) {
                        if (!first) json.append(",");
                        json.append("{")
                            .append("\"id\":").append(rs.getInt("club_id")).append(",")
                            .append("\"name\":\"").append(escapeJson(rs.getString("name"))).append("\",")
                            .append("\"tagline\":\"").append(escapeJson(rs.getString("tagline"))).append("\",")
                            .append("\"icon\":\"").append(escapeJson(rs.getString("icon"))).append("\"")
                            .append("}");
                        first = false;
                    }
                    json.append("]");
                    sendResponse(exchange, 200, "application/json; charset=utf-8", json.toString());
                } catch (Exception e) {
                    sendResponse(exchange, 500, "text/plain; charset=utf-8", "Database Error: " + e.getMessage());
                }
            }
        }
    }

    // GET /api/events
    static class EventsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                StringBuilder json = new StringBuilder("[");
                String sql = "SELECT e.event_id, e.club_id, c.name AS club_name, e.title, e.category, " +
                             "e.event_date, e.start_time, e.end_time, e.venue, e.seats_filled, e.seats_total " +
                             "FROM events e JOIN clubs c ON e.club_id = c.club_id ORDER BY e.event_date ASC";

                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {

                    boolean first = true;
                    while (rs.next()) {
                        if (!first) json.append(",");
                        json.append("{")
                            .append("\"id\":").append(rs.getInt("event_id")).append(",")
                            .append("\"clubId\":").append(rs.getInt("club_id")).append(",")
                            .append("\"clubName\":\"").append(escapeJson(rs.getString("club_name"))).append("\",")
                            .append("\"title\":\"").append(escapeJson(rs.getString("title"))).append("\",")
                            .append("\"category\":\"").append(escapeJson(rs.getString("category"))).append("\",")
                            .append("\"date\":\"").append(escapeJson(rs.getString("event_date"))).append("\",")
                            .append("\"startTime\":\"").append(escapeJson(rs.getString("start_time"))).append("\",")
                            .append("\"endTime\":\"").append(escapeJson(rs.getString("end_time"))).append("\",")
                            .append("\"venue\":\"").append(escapeJson(rs.getString("venue"))).append("\",")
                            .append("\"filled\":").append(rs.getInt("seats_filled")).append(",")
                            .append("\"total\":").append(rs.getInt("seats_total"))
                            .append("}");
                        first = false;
                    }
                    json.append("]");
                    sendResponse(exchange, 200, "application/json; charset=utf-8", json.toString());
                } catch (Exception e) {
                    sendResponse(exchange, 500, "text/plain; charset=utf-8", "Database Error: " + e.getMessage());
                }
            }
        }
    }

    // GET /api/event-detail?id=X
    static class EventDetailHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
                int eventId = Integer.parseInt(query.getOrDefault("id", "0"));

                String sql = "SELECT e.*, c.name AS club_name FROM events e " +
                             "JOIN clubs c ON e.club_id = c.club_id WHERE e.event_id = ?";

                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setInt(1, eventId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            String json = "{" +
                                "\"id\":" + rs.getInt("event_id") + "," +
                                "\"clubId\":" + rs.getInt("club_id") + "," +
                                "\"clubName\":\"" + escapeJson(rs.getString("club_name")) + "\"," +
                                "\"title\":\"" + escapeJson(rs.getString("title")) + "\"," +
                                "\"category\":\"" + escapeJson(rs.getString("category")) + "\"," +
                                "\"date\":\"" + escapeJson(rs.getString("event_date")) + "\"," +
                                "\"startTime\":\"" + escapeJson(rs.getString("start_time")) + "\"," +
                                "\"endTime\":\"" + escapeJson(rs.getString("end_time")) + "\"," +
                                "\"venue\":\"" + escapeJson(rs.getString("venue")) + "\"," +
                                "\"filled\":" + rs.getInt("seats_filled") + "," +
                                "\"total\":" + rs.getInt("seats_total") + "," +
                                "\"ps\":\"" + escapeJson(rs.getString("problem_statements")) + "\"," +
                                "\"rules\":\"" + escapeJson(rs.getString("rules")) + "\"" +
                                "}";
                            sendResponse(exchange, 200, "application/json; charset=utf-8", json);
                        } else {
                            sendResponse(exchange, 404, "text/plain; charset=utf-8", "Event Not Found");
                        }
                    }
                } catch (Exception e) {
                    sendResponse(exchange, 500, "text/plain; charset=utf-8", "Database Error: " + e.getMessage());
                }
            }
        }
    }

    // POST /api/register
    static class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> p = parseFormData(exchange.getRequestBody());
                String email = p.getOrDefault("email", "").trim().toLowerCase();
                int eventId = Integer.parseInt(p.getOrDefault("eventId", "0"));
                String name = p.getOrDefault("name", "").trim();
                String regNo = p.getOrDefault("regNo", "").trim();
                String dept = p.getOrDefault("dept", "").trim();

                if (!email.endsWith("@rajalakshmi.edu.in")) {
                    sendResponse(exchange, 400, "text/plain; charset=utf-8", "Only official @rajalakshmi.edu.in email domain allowed");
                    return;
                }

                String checkSql = "SELECT seats_filled, seats_total FROM events WHERE event_id = ?";
                String insertSql = "INSERT INTO registrations (event_id, student_name, reg_number, student_email, department) VALUES (?, ?, ?, ?, ?)";
                String updateSql = "UPDATE events SET seats_filled = seats_filled + 1 WHERE event_id = ?";

                try (Connection conn = DBConnection.getConnection()) {
                    conn.setAutoCommit(false);

                    try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                        checkStmt.setInt(1, eventId);
                        ResultSet rs = checkStmt.executeQuery();
                        if (rs.next()) {
                            if (rs.getInt("seats_filled") >= rs.getInt("seats_total")) {
                                conn.rollback();
                                sendResponse(exchange, 400, "text/plain; charset=utf-8", "Seat capacity is full");
                                return;
                            }
                        } else {
                            conn.rollback();
                            sendResponse(exchange, 404, "text/plain; charset=utf-8", "Event not found");
                            return;
                        }
                    }

                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setInt(1, eventId);
                        insertStmt.setString(2, name);
                        insertStmt.setString(3, regNo);
                        insertStmt.setString(4, email);
                        insertStmt.setString(5, dept);
                        insertStmt.executeUpdate();
                    }

                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setInt(1, eventId);
                        updateStmt.executeUpdate();
                    }

                    conn.commit();
                    sendResponse(exchange, 200, "text/plain; charset=utf-8", "Registration Successful");
                } catch (SQLException e) {
                    if (e.getErrorCode() == 1062) {
                        sendResponse(exchange, 409, "text/plain; charset=utf-8", "You have already registered for this event.");
                    } else {
                        sendResponse(exchange, 500, "text/plain; charset=utf-8", "Database Error: " + e.getMessage());
                    }
                }
            }
        }
    }

    // POST /api/add-event
    static class AddEventHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> p = parseFormData(exchange.getRequestBody());
                String sql = "INSERT INTO events (club_id, title, category, event_date, start_time, end_time, venue, seats_total, seats_filled, problem_statements, rules) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)";

                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setInt(1, Integer.parseInt(p.get("clubId")));
                    stmt.setString(2, p.get("title"));
                    stmt.setString(3, p.get("category"));
                    stmt.setString(4, p.get("date"));
                    stmt.setString(5, p.get("startTime"));
                    stmt.setString(6, p.get("endTime"));
                    stmt.setString(7, p.get("venue"));
                    stmt.setInt(8, Integer.parseInt(p.get("seats")));
                    stmt.setString(9, p.getOrDefault("problemStatements", ""));
                    stmt.setString(10, p.getOrDefault("rules", ""));

                    stmt.executeUpdate();
                    sendResponse(exchange, 200, "text/plain; charset=utf-8", "Event Added");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "text/plain; charset=utf-8", "Error: " + e.getMessage());
                }
            }
        }
    }

    // POST /api/update-event
    static class UpdateEventHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> p = parseFormData(exchange.getRequestBody());
                String sql = "UPDATE events SET club_id = ?, title = ?, category = ?, event_date = ?, start_time = ?, end_time = ?, venue = ?, seats_total = ?, problem_statements = ?, rules = ? WHERE event_id = ?";

                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setInt(1, Integer.parseInt(p.get("clubId")));
                    stmt.setString(2, p.get("title"));
                    stmt.setString(3, p.get("category"));
                    stmt.setString(4, p.get("date"));
                    stmt.setString(5, p.get("startTime"));
                    stmt.setString(6, p.get("endTime"));
                    stmt.setString(7, p.get("venue"));
                    stmt.setInt(8, Integer.parseInt(p.get("seats")));
                    stmt.setString(9, p.getOrDefault("problemStatements", ""));
                    stmt.setString(10, p.getOrDefault("rules", ""));
                    stmt.setInt(11, Integer.parseInt(p.get("eventId")));

                    stmt.executeUpdate();
                    sendResponse(exchange, 200, "text/plain; charset=utf-8", "Event Updated");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "text/plain; charset=utf-8", "Error: " + e.getMessage());
                }
            }
        }
    }

    // POST /api/delete-event (Safe deletion removing child registrations first)
    static class DeleteEventHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> p = parseFormData(exchange.getRequestBody());
                int eventId = Integer.parseInt(p.get("id"));

                try (Connection conn = DBConnection.getConnection()) {
                    conn.setAutoCommit(false);
                    try (PreparedStatement ps1 = conn.prepareStatement("DELETE FROM registrations WHERE event_id = ?");
                         PreparedStatement ps2 = conn.prepareStatement("DELETE FROM events WHERE event_id = ?")) {
                        ps1.setInt(1, eventId);
                        ps1.executeUpdate();

                        ps2.setInt(1, eventId);
                        ps2.executeUpdate();

                        conn.commit();
                        sendResponse(exchange, 200, "text/plain; charset=utf-8", "Event Deleted");
                    } catch (Exception ex) {
                        conn.rollback();
                        throw ex;
                    }
                } catch (Exception e) {
                    sendResponse(exchange, 500, "text/plain; charset=utf-8", "Error: " + e.getMessage());
                }
            }
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
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setString(1, p.get("name"));
                    stmt.setString(2, p.get("tagline"));
                    stmt.setString(3, p.getOrDefault("icon", "ph-users-three"));

                    stmt.executeUpdate();
                    sendResponse(exchange, 200, "text/plain; charset=utf-8", "Club Added");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "text/plain; charset=utf-8", "Error: " + e.getMessage());
                }
            }
        }
    }

    // POST /api/delete-club (Safe multi-tier deletion removing registrations, then events, then club)
    static class DeleteClubHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> p = parseFormData(exchange.getRequestBody());
                int clubId = Integer.parseInt(p.get("id"));

                try (Connection conn = DBConnection.getConnection()) {
                    conn.setAutoCommit(false);
                    try (PreparedStatement ps1 = conn.prepareStatement("DELETE FROM registrations WHERE event_id IN (SELECT event_id FROM events WHERE club_id = ?)");
                         PreparedStatement ps2 = conn.prepareStatement("DELETE FROM events WHERE club_id = ?");
                         PreparedStatement ps3 = conn.prepareStatement("DELETE FROM clubs WHERE club_id = ?")) {

                        ps1.setInt(1, clubId);
                        ps1.executeUpdate();

                        ps2.setInt(1, clubId);
                        ps2.executeUpdate();

                        ps3.setInt(1, clubId);
                        ps3.executeUpdate();

                        conn.commit();
                        sendResponse(exchange, 200, "text/plain; charset=utf-8", "Club Deleted");
                    } catch (Exception ex) {
                        conn.rollback();
                        throw ex;
                    }
                } catch (Exception e) {
                    sendResponse(exchange, 500, "text/plain; charset=utf-8", "Error: " + e.getMessage());
                }
            }
        }
    }

    // GET /api/attendees?eventId=X
    static class AttendeesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
                int eventId = Integer.parseInt(query.getOrDefault("eventId", "0"));

                String evSql = "SELECT e.title, e.event_date, e.start_time, e.end_time, e.venue, c.name AS club_name " +
                               "FROM events e JOIN clubs c ON e.club_id = c.club_id WHERE e.event_id = ?";
                String regSql = "SELECT reg_number, student_name, department, student_email " +
                                "FROM registrations WHERE event_id = ? ORDER BY registered_at ASC";

                try (Connection conn = DBConnection.getConnection()) {
                    StringBuilder json = new StringBuilder("{");

                    try (PreparedStatement stmt = conn.prepareStatement(evSql)) {
                        stmt.setInt(1, eventId);
                        ResultSet rs = stmt.executeQuery();
                        if (rs.next()) {
                            json.append("\"event\":{")
                                .append("\"title\":\"").append(escapeJson(rs.getString("title"))).append("\",")
                                .append("\"club\":\"").append(escapeJson(rs.getString("club_name"))).append("\",")
                                .append("\"date\":\"").append(escapeJson(rs.getString("event_date"))).append("\",")
                                .append("\"startTime\":\"").append(escapeJson(rs.getString("start_time"))).append("\",")
                                .append("\"endTime\":\"").append(escapeJson(rs.getString("end_time"))).append("\",")
                                .append("\"venue\":\"").append(escapeJson(rs.getString("venue"))).append("\"")
                                .append("},\"students\":[");
                        } else {
                            sendResponse(exchange, 404, "text/plain; charset=utf-8", "Event Not Found");
                            return;
                        }
                    }

                    try (PreparedStatement stmt = conn.prepareStatement(regSql)) {
                        stmt.setInt(1, eventId);
                        ResultSet rs = stmt.executeQuery();
                        boolean first = true;
                        while (rs.next()) {
                            if (!first) json.append(",");
                            json.append("{")
                                .append("\"regNo\":\"").append(escapeJson(rs.getString("reg_number"))).append("\",")
                                .append("\"name\":\"").append(escapeJson(rs.getString("student_name"))).append("\",")
                                .append("\"dept\":\"").append(escapeJson(rs.getString("department"))).append("\",")
                                .append("\"email\":\"").append(escapeJson(rs.getString("student_email"))).append("\"")
                                .append("}");
                            first = false;
                        }
                    }

                    json.append("]}");
                    sendResponse(exchange, 200, "application/json; charset=utf-8", json.toString());
                } catch (Exception e) {
                    sendResponse(exchange, 500, "text/plain; charset=utf-8", "Error: " + e.getMessage());
                }
            }
        }
    }

    // Static File Server
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/index.html";
            }

            File file = new File("web" + path);
            if (!file.exists() || file.isDirectory()) {
                sendResponse(exchange, 404, "text/plain; charset=utf-8", "404 Not Found");
                return;
            }

            String contentType = "text/html; charset=utf-8";
            if (path.endsWith(".css")) contentType = "text/css";
            else if (path.endsWith(".js")) contentType = "application/javascript";
            else if (path.endsWith(".png")) contentType = "image/png";
            else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) contentType = "image/jpeg";
            else if (path.endsWith(".svg")) contentType = "image/svg+xml";

            byte[] bytes = Files.readAllBytes(Paths.get(file.getAbsolutePath()));
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // ==========================================
    // UTILITY METHODS
    // ==========================================

    private static void sendResponse(HttpExchange exchange, int statusCode, String contentType, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> parseFormData(InputStream is) throws IOException {
        Map<String, String> map = new HashMap<>();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        if (body.isEmpty()) return map;

        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            } else if (kv.length == 1) {
                map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), "");
            }
        }
        return map;
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}