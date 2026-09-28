package com.example.bookmyroom.repository;

import com.example.bookmyroom.entity.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    boolean existsByRoomNumber(String roomNumber);

    Optional<Room> findByRoomNumber(String roomNumber);

    /**
     * Loads the room with a row lock (SELECT ... FOR UPDATE). Two requests booking the same
     * room at the same moment are serialised, so the overlap check below cannot be raced.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);

    /** Rooms in service that have no CONFIRMED booking overlapping [checkIn, checkOut). */
    @Query("""
            select r from Room r
            where r.available = true
              and not exists (
                  select b.id from Booking b
                  where b.room = r
                    and b.status = com.example.bookmyroom.entity.BookingStatus.CONFIRMED
                    and b.checkInDate < :checkOut
                    and b.checkOutDate > :checkIn)
            order by r.roomNumber
            """)
    List<Room> findAvailableRooms(@Param("checkIn") LocalDate checkIn,
                                  @Param("checkOut") LocalDate checkOut);
}
