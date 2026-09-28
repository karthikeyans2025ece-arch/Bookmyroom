package com.example.bookmyroom.service;

import com.example.bookmyroom.dto.BookingRequest;
import com.example.bookmyroom.dto.BookingResponse;
import com.example.bookmyroom.entity.Booking;
import com.example.bookmyroom.entity.BookingStatus;
import com.example.bookmyroom.entity.Guest;
import com.example.bookmyroom.entity.Room;
import com.example.bookmyroom.exception.BookingAlreadyCancelledException;
import com.example.bookmyroom.exception.BookingConflictException;
import com.example.bookmyroom.exception.CapacityExceededException;
import com.example.bookmyroom.exception.InvalidDateRangeException;
import com.example.bookmyroom.exception.InvalidGuestException;
import com.example.bookmyroom.exception.InvalidRoomException;
import com.example.bookmyroom.exception.ResourceNotFoundException;
import com.example.bookmyroom.repository.BookingRepository;
import com.example.bookmyroom.repository.GuestRepository;
import com.example.bookmyroom.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {

    private static final long REFERENCE_START = 10000;

    private final BookingRepository bookingRepository;
    private final GuestRepository guestRepository;
    private final RoomRepository roomRepository;

    public BookingService(BookingRepository bookingRepository,
                          GuestRepository guestRepository,
                          RoomRepository roomRepository) {
        this.bookingRepository = bookingRepository;
        this.guestRepository = guestRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional
    public BookingResponse createBooking(BookingRequest request) {
        LocalDate checkIn = request.checkInDate();
        LocalDate checkOut = request.checkOutDate();

        if (!checkIn.isBefore(checkOut)) {
            throw new InvalidDateRangeException("Check-out date must be after check-in date");
        }

        Integer guestCount = request.numberOfGuests();
        if (guestCount == null || guestCount <= 0) {
            throw new CapacityExceededException("Number of guests must be greater than 0");
        }

        Guest guest = guestRepository.findById(request.guestId())
                .orElseThrow(() -> new InvalidGuestException("Guest with id " + request.guestId() + " does not exist"));

        // locks the room row until this transaction finishes
        Room room = roomRepository.findByIdForUpdate(request.roomId())
                .orElseThrow(() -> new InvalidRoomException("Room with id " + request.roomId() + " does not exist"));

        if (!room.isAvailable()) {
            throw new InvalidRoomException("Room " + room.getRoomNumber() + " is not available for booking");
        }

        if (guestCount > room.getCapacity()) {
            throw new CapacityExceededException("Room capacity is " + room.getCapacity()
                    + ". Cannot accommodate " + guestCount + " guests.");
        }

        List<Booking> conflicts = bookingRepository.findConflictingBookings(room.getId(), checkIn, checkOut);
        if (!conflicts.isEmpty()) {
            Booking existing = conflicts.get(0);
            throw new BookingConflictException("Room " + room.getRoomNumber() + " is already booked from "
                    + existing.getCheckInDate() + " to " + existing.getCheckOutDate() + ".");
        }

        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        BigDecimal totalCost = room.getRatePerNight().multiply(BigDecimal.valueOf(nights));

        Booking booking = new Booking();
        booking.setGuest(guest);
        booking.setRoom(room);
        booking.setCheckInDate(checkIn);
        booking.setCheckOutDate(checkOut);
        booking.setNumberOfGuests(guestCount);
        booking.setTotalCost(totalCost);
        booking.setStatus(BookingStatus.CONFIRMED);
        // placeholder, replaced below once the database has generated the id
        booking.setBookingReference("TMP-" + UUID.randomUUID());

        Booking saved = bookingRepository.save(booking);
        saved.setBookingReference("BMR-" + (REFERENCE_START + saved.getId()));
        return BookingResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getAllBookings() {
        return bookingRepository.findAll().stream().map(BookingResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Long id) {
        return BookingResponse.from(findBooking(id));
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingByReference(String reference) {
        return bookingRepository.findByBookingReference(reference)
                .map(BookingResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with reference " + reference));
    }

    @Transactional
    public BookingResponse cancelBooking(Long id) {
        Booking booking = findBooking(id);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingAlreadyCancelledException("Booking " + booking.getBookingReference() + " is already cancelled");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        return BookingResponse.from(bookingRepository.save(booking));
    }

    private Booking findBooking(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id " + id));
    }
}
