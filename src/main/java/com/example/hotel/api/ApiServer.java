package com.example.hotel.api;

import com.example.hotel.model.BookingException;
import com.example.hotel.model.Guest;
import com.example.hotel.model.Quote;
import com.example.hotel.model.Reservation;
import com.example.hotel.model.Room;
import com.example.hotel.model.RoomType;
import com.example.hotel.service.ReservationService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** HTTP layer. Endpoints are documented in the README (section "REST API"). */
public class ApiServer {

    private final ReservationService service;
    private final HttpServer server;

    public ApiServer(ReservationService service, int port) throws IOException {
        this.service = service;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange ex) throws IOException {
        int status;
        Object body;
        try {
            Map<String, String> params = parseParams(ex);
            String[] seg = ex.getRequestURI().getPath().replaceAll("^/+|/+$", "").split("/");
            String method = ex.getRequestMethod();
            Object[] result = route(method, seg, params);
            status = (Integer) result[0];
            body = result[1];
        } catch (BookingException e) {
            status = statusFor(e.getCode());
            body = error(e.getCode().name(), e.getMessage());
        } catch (IllegalArgumentException | DateTimeParseException e) {
            status = 400;
            body = error("BAD_REQUEST", e.getMessage());
        } catch (RuntimeException e) {
            status = 500;
            body = error("INTERNAL_ERROR", String.valueOf(e.getMessage()));
        }
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Object[] route(String method, String[] seg, Map<String, String> p) {
        String root = seg[0];
        if (root.equals("guests")) {
            if (seg.length == 1 && method.equals("POST")) {
                return ok(201, guestView(service.registerGuest(p.get("name"), p.get("email"))));
            }
            if (seg.length == 2 && method.equals("GET")) {
                return ok(200, guestView(service.getGuest(seg[1])));
            }
        } else if (root.equals("rooms") && seg.length == 2 && seg[1].equals("available") && method.equals("GET")) {
            List<Room> rooms = service.availableRooms(roomType(p), date(p, "checkIn"), date(p, "checkOut"));
            List<Object> out = new ArrayList<>();
            for (Room r : rooms) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("number", r.number());
                m.put("type", r.type());
                out.add(m);
            }
            return ok(200, out);
        } else if (root.equals("quote") && seg.length == 1 && method.equals("GET")) {
            Quote q = service.quote(roomType(p), date(p, "checkIn"), date(p, "checkOut"), integer(p, "guests"),
                    p.get("guestId"), p.get("promo"));
            return ok(200, quoteView(q));
        } else if (root.equals("reservations")) {
            if (seg.length == 1 && method.equals("POST")) {
                Reservation r = service.createReservation(p.get("guestId"), roomType(p), date(p, "checkIn"),
                        date(p, "checkOut"), integer(p, "guests"), p.get("promo"));
                return ok(201, reservationView(r));
            }
            if (seg.length == 2 && method.equals("GET")) {
                return ok(200, reservationView(service.getReservation(seg[1])));
            }
            if (seg.length == 3 && method.equals("POST")) {
                String id = seg[1];
                switch (seg[2]) {
                    case "confirm":
                        return ok(200, reservationView(service.confirm(id)));
                    case "checkin":
                        return ok(200, reservationView(service.checkIn(id)));
                    case "checkout":
                        return ok(200, reservationView(service.checkOut(id)));
                    case "cancel":
                        return ok(200, reservationView(service.cancel(id)));
                    case "noshow":
                        return ok(200, reservationView(service.markNoShow(id)));
                    case "dates":
                        return ok(200, reservationView(
                                service.changeDates(id, date(p, "newCheckIn"), date(p, "newCheckOut"))));
                    default:
                        break;
                }
            }
        }
        return ok(404, error("NOT_FOUND", "No route for " + method + " /" + String.join("/", seg)));
    }

    // ----------------------------------------------------------------- views

    private static Map<String, Object> guestView(Guest g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("name", g.getName());
        m.put("email", g.getEmail());
        m.put("points", g.getPoints());
        m.put("tier", g.tier());
        m.put("completedStays", g.getCompletedStays());
        return m;
    }

    private static Map<String, Object> quoteView(Quote q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nights", q.nights());
        m.put("roomCharge", q.roomCharge());
        m.put("extraGuestCharge", q.extraGuestCharge());
        m.put("discount", q.discount());
        m.put("vat", q.vat());
        m.put("tourismFee", q.tourismFee());
        m.put("total", q.total());
        m.put("appliedDiscounts", q.appliedDiscounts());
        return m;
    }

    private static Map<String, Object> reservationView(Reservation r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("guestId", r.getGuestId());
        m.put("roomNumber", r.getRoomNumber());
        m.put("roomType", r.getRoomType());
        m.put("checkIn", r.getCheckIn());
        m.put("checkOut", r.getCheckOut());
        m.put("guests", r.getGuests());
        m.put("promoCode", r.getPromoCode());
        m.put("status", r.getStatus());
        m.put("quote", quoteView(r.getQuote()));
        m.put("refundAmount", r.getRefundAmount());
        m.put("priceAdjustment", r.getPriceAdjustment());
        m.put("createdAt", r.getCreatedAt());
        return m;
    }

    // --------------------------------------------------------------- helpers

    private static Object[] ok(int status, Object body) {
        return new Object[] { status, body };
    }

    private static Map<String, Object> error(String code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", code);
        m.put("message", message);
        return m;
    }

    private static int statusFor(BookingException.Code code) {
        switch (code) {
            case GUEST_NOT_FOUND:
            case RESERVATION_NOT_FOUND:
                return 404;
            case NO_AVAILABILITY:
            case INVALID_STATE:
            case DUPLICATE_EMAIL:
                return 409;
            default:
                return 400;
        }
    }

    private static RoomType roomType(Map<String, String> p) {
        String v = p.get("type");
        if (v == null) {
            throw new IllegalArgumentException("Missing parameter: type");
        }
        return RoomType.valueOf(v.trim().toUpperCase(Locale.ROOT));
    }

    private static LocalDate date(Map<String, String> p, String name) {
        String v = p.get(name);
        if (v == null) {
            throw new IllegalArgumentException("Missing parameter: " + name);
        }
        return LocalDate.parse(v.trim());
    }

    private static int integer(Map<String, String> p, String name) {
        String v = p.get(name);
        if (v == null) {
            throw new IllegalArgumentException("Missing parameter: " + name);
        }
        return Integer.parseInt(v.trim());
    }

    private static Map<String, String> parseParams(HttpExchange ex) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        parseInto(params, ex.getRequestURI().getRawQuery());
        if (ex.getRequestMethod().equals("POST")) {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            parseInto(params, body);
        }
        return params;
    }

    private static void parseInto(Map<String, String> params, String raw) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        for (String pair : raw.split("&")) {
            int idx = pair.indexOf('=');
            String key = idx < 0 ? pair : pair.substring(0, idx);
            String value = idx < 0 ? "" : pair.substring(idx + 1);
            params.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
    }
}
