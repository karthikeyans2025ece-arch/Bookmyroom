package com.example.bookmyroom.repository;

import com.example.bookmyroom.entity.Booking;
import com.example.bookmyroom.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByBookingReference(String bookingReference);

    boolean existsByRoomId(Long roomId);

    boolean existsByGuestId(Long guestId);

    /**
     * CONFIRMED bookings of a room that overlap the requested stay.
     * existing.checkIn < requested.checkOut AND existing.checkOut > requested.checkIn
     * (so a stay that ends on the day another begins is NOT a conflict).
     */
    @Query("""
            select b from Booking b
            where b.room.id = :roomId
              and b.status = com.example.bookmyroom.entity.BookingStatus.CONFIRMED
              and b.checkInDate < :checkOut
              and b.checkOutDate > :checkIn
            order by b.checkInDate
            """)
    List<Booking> findConflictingBookings(@Param("roomId") Long roomId,
                                          @Param("checkIn") LocalDate checkIn,
                                          @Param("checkOut") LocalDate checkOut);

    /** Bookings with the given status that touch the period [periodStart, periodEnd). */
    @Query("""
            select b from Booking b
            where b.status = :status
              and b.checkInDate < :periodEnd
              and b.checkOutDate > :periodStart
            """)
    List<Booking> findByStatusOverlappingPeriod(@Param("status") BookingStatus status,
                                                @Param("periodStart") LocalDate periodStart,
                                                @Param("periodEnd") LocalDate periodEnd);

    @Query("""
            select count(b) from Booking b
            where b.status = :status
              and b.checkInDate < :periodEnd
              and b.checkOutDate > :periodStart
            """)
    long countByStatusOverlappingPeriod(@Param("status") BookingStatus status,
                                        @Param("periodStart") LocalDate periodStart,
                                        @Param("periodEnd") LocalDate periodEnd);
}
