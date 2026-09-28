package com.example.bookmyroom.service;

import com.example.bookmyroom.dto.RoomRequest;
import com.example.bookmyroom.dto.RoomResponse;
import com.example.bookmyroom.entity.Room;
import com.example.bookmyroom.exception.DuplicateResourceException;
import com.example.bookmyroom.exception.InvalidDateRangeException;
import com.example.bookmyroom.exception.ResourceInUseException;
import com.example.bookmyroom.exception.ResourceNotFoundException;
import com.example.bookmyroom.repository.BookingRepository;
import com.example.bookmyroom.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    public RoomService(RoomRepository roomRepository, BookingRepository bookingRepository) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public RoomResponse addRoom(RoomRequest request) {
        String roomNumber = request.roomNumber().trim();
        if (roomRepository.existsByRoomNumber(roomNumber)) {
            throw new DuplicateResourceException("Room number " + roomNumber + " already exists");
        }

        Room room = new Room();
        room.setRoomNumber(roomNumber);
        room.setRoomType(request.roomType());
        room.setCapacity(request.capacity());
        room.setRatePerNight(request.ratePerNight());
        room.setAvailable(request.available() == null || request.available());
        return RoomResponse.from(roomRepository.save(room));
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getAllRooms() {
        return roomRepository.findAll().stream().map(RoomResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RoomResponse getRoomById(Long id) {
        return RoomResponse.from(findRoom(id));
    }

    @Transactional
    public RoomResponse updateRoom(Long id, RoomRequest request) {
        Room room = findRoom(id);
        String roomNumber = request.roomNumber().trim();

        roomRepository.findByRoomNumber(roomNumber)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new DuplicateResourceException("Room number " + roomNumber + " already exists");
                });

        room.setRoomNumber(roomNumber);
        room.setRoomType(request.roomType());
        room.setCapacity(request.capacity());
        room.setRatePerNight(request.ratePerNight());
        if (request.available() != null) {
            room.setAvailable(request.available());
        }
        return RoomResponse.from(roomRepository.save(room));
    }

    @Transactional
    public void deleteRoom(Long id) {
        Room room = findRoom(id);
        // booking history must be kept, so a room that has bookings can't be removed
        if (bookingRepository.existsByRoomId(id)) {
            throw new ResourceInUseException("Room " + room.getRoomNumber()
                    + " has booking records and cannot be deleted. Mark it as unavailable instead.");
        }
        roomRepository.delete(room);
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> findAvailableRooms(LocalDate checkIn, LocalDate checkOut) {
        if (!checkIn.isBefore(checkOut)) {
            throw new InvalidDateRangeException("Check-out date must be after check-in date");
        }
        return roomRepository.findAvailableRooms(checkIn, checkOut).stream()
                .map(RoomResponse::from)
                .toList();
    }

    private Room findRoom(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id " + id));
    }
}
