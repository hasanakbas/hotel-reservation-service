package com.example.hotel;

import com.example.hotel.api.ApiServer;
import com.example.hotel.model.Room;
import com.example.hotel.model.RoomType;
import com.example.hotel.repository.jdbc.Database;
import com.example.hotel.repository.jdbc.JdbcGuestRepository;
import com.example.hotel.repository.jdbc.JdbcReservationRepository;
import com.example.hotel.service.PricingService;
import com.example.hotel.service.ReservationService;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Usage: {@code java -jar hotel-reservation-service.jar [port] [dataDir|memory]}.
 * Defaults: port 8080, data stored in ./data (H2 database files). "memory" runs without persistence.
 */
public class Main {

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        String store = args.length > 1 ? args[1] : "data";

        ReservationService service;
        if (store.equals("memory")) {
            service = ReservationService.inMemory(Clock.systemDefaultZone(), new PricingService(), defaultRooms());
            System.out.println("Storage: in memory (nothing is persisted)");
        } else {
            Database db = Database.file(Path.of(store));
            service = new ReservationService(Clock.systemDefaultZone(), new PricingService(), defaultRooms(),
                    new JdbcGuestRepository(db), new JdbcReservationRepository(db), db);
            System.out.println("Storage: H2 database files in " + Path.of(store).toAbsolutePath());
        }
        ApiServer server = new ApiServer(service, port);
        server.start();
        System.out.println("Hotel reservation service listening on http://localhost:" + server.port());
    }

    public static List<Room> defaultRooms() {
        List<Room> rooms = new ArrayList<>();
        for (int n = 101; n <= 104; n++) {
            rooms.add(new Room(n, RoomType.SINGLE));
        }
        for (int n = 201; n <= 206; n++) {
            rooms.add(new Room(n, RoomType.DOUBLE));
        }
        for (int n = 301; n <= 302; n++) {
            rooms.add(new Room(n, RoomType.SUITE));
        }
        return rooms;
    }
}
