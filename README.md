# BookMyRoom – Guest House Room Booking System

Backend-only REST API for a college guest house. Staff can manage rooms and guests, check which rooms are free for a date range, create and cancel bookings, and see a monthly occupancy report. The main goal is that **the same room can never be double-booked**.

**Stack:** Java 17, Spring Boot 3.3, Spring Web, Spring Data JPA (Hibernate), Bean Validation, Maven, MySQL, Springdoc OpenAPI (Swagger UI).

---

## 1. Project structure

```
BookMyRoom/
├── pom.xml
├── database.sql
├── README.md
└── src/
    ├── main/java/com/example/bookmyroom/
    │   ├── BookMyRoomApplication.java
    │   ├── config/        OpenApiConfig
    │   ├── controller/    Room, Guest, Booking, Report controllers (thin, no business logic)
    │   ├── service/       RoomService, GuestService, BookingService, OccupancyReportService
    │   ├── repository/    RoomRepository, GuestRepository, BookingRepository
    │   ├── entity/        Room, Guest, Booking, RoomType, BookingStatus
    │   ├── dto/           request/response objects (entities are never returned directly)
    │   └── exception/     custom exceptions + GlobalExceptionHandler
    ├── main/resources/application.properties
    └── test/java/.../BookingServiceTest.java
```

---

## 2. How to run

### Prerequisites
* JDK 17 or newer
* Maven 3.8+ (IntelliJ has one bundled)
* MySQL 8.0.16 or newer

### Step 1 – Create the database
Either run the script (creates the database, tables and sample data):

```
mysql -u root -p < database.sql
```

or just create an empty database and let Hibernate create the tables (`ddl-auto=update`):

```sql
CREATE DATABASE bookmyroom;
```

### Step 2 – Set your MySQL password
Set the `DB_PASSWORD` environment variable to your MySQL password before starting the application. The application reads the password from the environment so database credentials are not stored in the project.

In PowerShell:

```powershell
$env:DB_PASSWORD = "your-local-password"
```

If your MySQL username differs from `root`, update `spring.datasource.username` in `src/main/resources/application.properties`.
### Step 3 – Build and start
```
mvn clean install
mvn spring-boot:run
```
In IntelliJ: *File → Open →* select `pom.xml → Open as Project*, wait for Maven to import, then run `BookMyRoomApplication`.

The API runs on `http://localhost:8080`.

### Swagger UI
`http://localhost:8080/swagger-ui.html` – lists every endpoint and lets you try them from the browser.
Raw OpenAPI JSON: `http://localhost:8080/v3/api-docs`.

---

## 3. Business rules and where they are implemented

| Rule | Implementation |
|------|----------------|
| No double booking | `BookingService.createBooking()` calls `BookingRepository.findConflictingBookings()` **before** saving. Only `CONFIRMED` bookings are considered. |
| Overlap condition | `existing.checkInDate < requested.checkOut AND existing.checkOutDate > requested.checkIn` |
| Valid dates | check-in must be strictly before check-out |
| Guests | must be > 0 and ≤ room capacity |
| Total cost | `nights × ratePerNight`, calculated on the server; the request has no `totalCost` field |
| Cancellation | status set to `CANCELLED`, record is kept; cancelled bookings are ignored by the overlap check, so the room can be booked again |
| Concurrency | the room row is locked (`SELECT ... FOR UPDATE`) inside the booking transaction, so two simultaneous requests for the same room are processed one after another |

Overlap examples for Room 101 already booked **10 Oct – 15 Oct**:

| New booking | Result | Why |
|-------------|--------|-----|
| 12 Oct – 14 Oct | rejected | inside the existing stay |
| 14 Oct – 16 Oct | rejected | 10 < 16 and 15 > 14 |
| 08 Oct – 11 Oct | rejected | overlaps the start |
| 15 Oct – 18 Oct | **allowed** | check-out day = next check-in day |
| 05 Oct – 10 Oct | **allowed** | ends the day the other begins |

Other design notes:
* Booking reference is `BMR-` + (10000 + booking id), e.g. `BMR-10001`.
* A room that has any booking history cannot be deleted (409); mark it `available: false` instead. Same for guests.
* Rooms with `available: false` don't appear in availability results and can't be booked.
* Occupancy report: a booking is counted in a month if any of its nights fall in that month. Occupied room nights and revenue count only the part of the stay inside the month (revenue is split in proportion to nights). `bookedRooms` = distinct rooms with at least one confirmed booking in the month.

---

## 4. API summary

| Method | URL | Description | Success |
|--------|-----|-------------|---------|
| POST | `/api/rooms` | Add room | 201 |
| GET | `/api/rooms` | All rooms | 200 |
| GET | `/api/rooms/{id}` | Room by id | 200 |
| PUT | `/api/rooms/{id}` | Update room | 200 |
| DELETE | `/api/rooms/{id}` | Delete room | 204 |
| GET | `/api/rooms/availability?checkIn=&checkOut=` | Free rooms for the date range | 200 |
| POST | `/api/guests` | Add guest | 201 |
| GET | `/api/guests` | All guests | 200 |
| GET | `/api/guests/{id}` | Guest by id | 200 |
| PUT | `/api/guests/{id}` | Update guest | 200 |
| DELETE | `/api/guests/{id}` | Delete guest | 204 |
| POST | `/api/bookings` | Create booking | 201 |
| GET | `/api/bookings` | All bookings | 200 |
| GET | `/api/bookings/{id}` | Booking by id | 200 |
| GET | `/api/bookings/reference/{ref}` | Booking by reference | 200 |
| PUT | `/api/bookings/{id}/cancel` | Cancel booking | 200 |
| GET | `/api/reports/occupancy?month=2026-10` | Monthly report | 200 |

Errors: `400` invalid input, `404` not found, `409` conflict (overlapping booking, duplicate room number, already cancelled, record in use), `500` unexpected error. Error body:

```json
{
  "timestamp": "2026-09-28T10:00:00",
  "status": 409,
  "error": "Conflict",
  "message": "Room 101 is already booked from 2026-10-10 to 2026-10-15."
}
```
Validation errors use `"error": "Validation Error"`. Stack traces are never returned.

---

## 5. Testing with Postman

Base URL: `http://localhost:8080`. Set header `Content-Type: application/json` on POST/PUT requests.

These tests assume you ran `database.sql` on a fresh database, so rooms 1–5 and guests 1–5 exist (room 1 = room `101`, capacity 2, ₹1500) plus 3 sample bookings in **November 2026**. The tests below use **October 2026**, so they don't clash with the sample bookings. (If you started from an empty database, first create rooms/guests with tests 1 and 3 and adjust the ids.)

### Test 1 – Create room
`POST /api/rooms`
```json
{ "roomNumber": "301", "roomType": "SINGLE", "capacity": 1, "ratePerNight": 1000 }
```
Expected **201**:
```json
{ "id": 6, "roomNumber": "301", "roomType": "SINGLE", "capacity": 1, "ratePerNight": 1000, "available": true }
```
Sending `roomNumber` `101` again returns **409** – `Room number 101 already exists`.

### Test 2 – Get rooms
`GET /api/rooms` → **200**, array of 6 rooms.

### Test 3 – Create guest
`POST /api/guests`
```json
{ "name": "Divya Raman", "email": "divya.raman@example.com", "phone": "9123456780", "address": "Madurai" }
```
Expected **201** with `"id": 6`. A bad email (`"email": "abc"`) returns **400** `Validation Error` – `Email is not valid`.

### Test 4 – Get guests
`GET /api/guests` → **200**, array of 6 guests.

### Test 5 – Check availability
`GET /api/rooms/availability?checkIn=2026-10-10&checkOut=2026-10-15`
Expected **200**: all 6 rooms (nothing is booked in October yet).

### Test 6 – Create booking
`POST /api/bookings`
```json
{ "guestId": 1, "roomId": 1, "checkInDate": "2026-10-10", "checkOutDate": "2026-10-15", "numberOfGuests": 2 }
```
Expected **201**:
```json
{
  "id": 4,
  "bookingReference": "BMR-10004",
  "guestId": 1,
  "roomId": 1,
  "roomNumber": "101",
  "checkInDate": "2026-10-10",
  "checkOutDate": "2026-10-15",
  "numberOfGuests": 2,
  "totalCost": 7500.00,
  "status": "CONFIRMED",
  "createdAt": "2026-09-28T10:15:30.123"
}
```
Run test 5 again – room `101` is no longer in the list.

### Test 7 – Create another, non-conflicting booking (same room, same-day turnover)
`POST /api/bookings`
```json
{ "guestId": 2, "roomId": 1, "checkInDate": "2026-10-15", "checkOutDate": "2026-10-18", "numberOfGuests": 1 }
```
Expected **201**, `BMR-10005`, `totalCost` 4500.00. Check-in on 15 Oct is allowed because the first guest checks out on 15 Oct.

### Test 8 – Overlapping booking (must fail)
`POST /api/bookings`
```json
{ "guestId": 3, "roomId": 1, "checkInDate": "2026-10-12", "checkOutDate": "2026-10-14", "numberOfGuests": 2 }
```
Also try `2026-10-14` → `2026-10-16` (overlaps both bookings) and `2026-10-08` → `2026-10-11`.

### Test 9 – Verify HTTP 409
Test 8 must return **409 Conflict**:
```json
{
  "timestamp": "2026-09-28T10:20:00.456",
  "status": 409,
  "error": "Conflict",
  "message": "Room 101 is already booked from 2026-10-10 to 2026-10-15."
}
```
(For `14 Oct – 16 Oct` the message mentions the first conflicting stay.) Confirm with `GET /api/bookings` that no extra booking was saved.

### Test 10 – Cancel booking
`PUT /api/bookings/4/cancel` → **200**, same booking with `"status": "CANCELLED"`.
Calling it again returns **409** – `Booking BMR-10004 is already cancelled`.
`GET /api/bookings/4` still returns the record (nothing is deleted).

### Test 11 – Book the previously cancelled dates
`POST /api/bookings`
```json
{ "guestId": 3, "roomId": 1, "checkInDate": "2026-10-10", "checkOutDate": "2026-10-15", "numberOfGuests": 2 }
```
Expected **201**, `BMR-10006`, `totalCost` 7500.00.

### Test 12 – Invalid dates
`POST /api/bookings`
```json
{ "guestId": 1, "roomId": 2, "checkInDate": "2026-10-20", "checkOutDate": "2026-10-18", "numberOfGuests": 1 }
```
Expected **400**:
```json
{ "timestamp": "2026-09-28T10:30:00", "status": 400, "error": "Bad Request", "message": "Check-out date must be after check-in date" }
```
(Same result if check-in equals check-out.)

### Test 13 – Exceed room capacity
`POST /api/bookings`
```json
{ "guestId": 1, "roomId": 1, "checkInDate": "2026-12-01", "checkOutDate": "2026-12-03", "numberOfGuests": 4 }
```
Expected **400** – `Room capacity is 2. Cannot accommodate 4 guests.`
`numberOfGuests: 0` returns **400** `Validation Error` – `Number of guests must be greater than 0`.

### Test 14 – Monthly occupancy report
`GET /api/reports/occupancy?month=2026-10`

After tests 1–13 the October data is: `BMR-10005` (15–18 Oct, 3 nights, ₹4500), `BMR-10006` (10–15 Oct, 5 nights, ₹7500) confirmed, and `BMR-10004` cancelled. Expected **200**:
```json
{
  "month": "2026-10",
  "totalRooms": 6,
  "bookedRooms": 1,
  "availableRooms": 5,
  "confirmedBookings": 2,
  "cancelledBookings": 1,
  "occupiedRoomNights": 8,
  "totalRevenue": 12000.00
}
```
Try `?month=2026-11` to see the sample bookings (a stay that crosses a month boundary is counted for both months). `?month=abc` returns **400**.

---

## 6. Unit tests

`BookingServiceTest` checks the cost calculation, booking reference, conflict rejection, invalid dates, capacity check and cancellation using mocked repositories (no database needed). It runs as part of `mvn clean install`.

## 7. Notes / limitations
* MySQL has no exclusion constraint for date ranges, so the overlap rule is enforced in the service layer (with a row lock on the room) rather than in the database.
* There is no authentication – it is a small internal college tool and out of scope for this project.

---
*Author: <your name> | Course: <course / semester> | Guide: <faculty name>*
