package com.example.bookmyroom.service;

import com.example.bookmyroom.dto.BookingRequest;
import com.example.bookmyroom.dto.BookingResponse;
import com.example.bookmyroom.entity.Booking;
import com.example.bookmyroom.entity.BookingStatus;
import com.example.bookmyroom.entity.Guest;
import com.example.bookmyroom.entity.Room;
import com.example.bookmyroom.entity.RoomType;
import com.example.bookmyroom.exception.BookingAlreadyCancelledException;
import com.example.bookmyroom.exception.BookingConflictException;
import com.example.bookmyroom.exception.CapacityExceededException;
import com.example.bookmyroom.exception.InvalidDateRangeException;
import com.example.bookmyroom.repository.BookingRepository;
import com.example.bookmyroom.repository.GuestRepository;
import com.example.bookmyroom.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the booking rules. The repositories are mocked, so no database is needed.
 * The SQL side of the overlap check is exercised through the Postman tests in the README.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private GuestRepository guestRepository;
    @Mock
    private RoomRepository roomRepository;

    private BookingService bookingService;
    private Guest guest;
    private Room room;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookingRepository, guestRepository, roomRepository);

        guest = new Guest();
        guest.setId(1L);
        guest.setName("Karthik Kumar");

        room = new Room();
        room.setId(1L);
        room.setRoomNumber("101");
        room.setRoomType(RoomType.DOUBLE);
        room.setCapacity(2);
        room.setRatePerNight(new BigDecimal("1500.00"));
        room.setAvailable(true);
    }

    private BookingRequest request(String in, String out, int guests) {
        return new BookingRequest(1L, 1L, LocalDate.parse(in), LocalDate.parse(out), guests);
    }

    private void stubGuestAndRoom() {
        when(guestRepository.findById(1L)).thenReturn(Optional.of(guest));
        when(roomRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(room));
    }

    @Test
    void createBooking_calculatesTotalCostAndReference() {
        stubGuestAndRoom();
        when(bookingRepository.findConflictingBookings(any(), any(), any())).thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });

        BookingResponse response = bookingService.createBooking(request("2026-10-10", "2026-10-13", 2));

        assertEquals(0, new BigDecimal("4500").compareTo(response.totalCost()));
        assertEquals("BMR-10001", response.bookingReference());
        assertEquals(BookingStatus.CONFIRMED, response.status());
    }

    @Test
    void createBooking_overlappingBookingIsRejected() {
        stubGuestAndRoom();
        Booking existing = new Booking();
        existing.setRoom(room);
        existing.setCheckInDate(LocalDate.parse("2026-10-10"));
        existing.setCheckOutDate(LocalDate.parse("2026-10-15"));
        when(bookingRepository.findConflictingBookings(any(), any(), any())).thenReturn(List.of(existing));

        BookingConflictException ex = assertThrows(BookingConflictException.class,
                () -> bookingService.createBooking(request("2026-10-12", "2026-10-14", 2)));

        assertEquals("Room 101 is already booked from 2026-10-10 to 2026-10-15.", ex.getMessage());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_checkOutBeforeCheckInIsRejected() {
        assertThrows(InvalidDateRangeException.class,
                () -> bookingService.createBooking(request("2026-10-15", "2026-10-10", 2)));
    }

    @Test
    void createBooking_sameDayCheckInAndOutIsRejected() {
        assertThrows(InvalidDateRangeException.class,
                () -> bookingService.createBooking(request("2026-10-10", "2026-10-10", 2)));
    }

    @Test
    void createBooking_tooManyGuestsIsRejected() {
        stubGuestAndRoom();

        CapacityExceededException ex = assertThrows(CapacityExceededException.class,
                () -> bookingService.createBooking(request("2026-10-10", "2026-10-13", 4)));

        assertEquals("Room capacity is 2. Cannot accommodate 4 guests.", ex.getMessage());
    }

    @Test
    void cancelBooking_setsStatusToCancelled() {
        Booking booking = new Booking();
        booking.setId(5L);
        booking.setBookingReference("BMR-10005");
        booking.setGuest(guest);
        booking.setRoom(room);
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        BookingResponse response = bookingService.cancelBooking(5L);

        assertEquals(BookingStatus.CANCELLED, response.status());
    }

    @Test
    void cancelBooking_alreadyCancelledIsRejected() {
        Booking booking = new Booking();
        booking.setId(5L);
        booking.setBookingReference("BMR-10005");
        booking.setStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));

        assertThrows(BookingAlreadyCancelledException.class, () -> bookingService.cancelBooking(5L));
        verify(bookingRepository, never()).save(any());
    }
}
