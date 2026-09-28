BookMyRoom Frontend (plain HTML / CSS / JavaScript)

No Node.js, React or npm is required.

To use it with the Spring Boot backend:
1. Copy index.html, style.css, app.js and favicon.svg into:
   src/main/resources/static/
2. Run BookMyRoomApplication (IntelliJ) or: mvn spring-boot:run
3. Open http://localhost:8080

The frontend calls relative API URLs: /api/rooms, /api/guests, /api/bookings,
/api/rooms/availability and /api/reports/occupancy.
