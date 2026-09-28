package com.example.bookmyroom.service;

import com.example.bookmyroom.dto.GuestRequest;
import com.example.bookmyroom.dto.GuestResponse;
import com.example.bookmyroom.entity.Guest;
import com.example.bookmyroom.exception.ResourceInUseException;
import com.example.bookmyroom.exception.ResourceNotFoundException;
import com.example.bookmyroom.repository.BookingRepository;
import com.example.bookmyroom.repository.GuestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GuestService {

    private final GuestRepository guestRepository;
    private final BookingRepository bookingRepository;

    public GuestService(GuestRepository guestRepository, BookingRepository bookingRepository) {
        this.guestRepository = guestRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public GuestResponse addGuest(GuestRequest request) {
        Guest guest = new Guest();
        copyFields(guest, request);
        return GuestResponse.from(guestRepository.save(guest));
    }

    @Transactional(readOnly = true)
    public List<GuestResponse> getAllGuests() {
        return guestRepository.findAll().stream().map(GuestResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public GuestResponse getGuestById(Long id) {
        return GuestResponse.from(findGuest(id));
    }

    @Transactional
    public GuestResponse updateGuest(Long id, GuestRequest request) {
        Guest guest = findGuest(id);
        copyFields(guest, request);
        return GuestResponse.from(guestRepository.save(guest));
    }

    @Transactional
    public void deleteGuest(Long id) {
        Guest guest = findGuest(id);
        if (bookingRepository.existsByGuestId(id)) {
            throw new ResourceInUseException("Guest " + guest.getName()
                    + " has booking records and cannot be deleted");
        }
        guestRepository.delete(guest);
    }

    private void copyFields(Guest guest, GuestRequest request) {
        guest.setName(request.name().trim());
        guest.setEmail(request.email().trim());
        guest.setPhone(request.phone().trim());
        guest.setAddress(request.address());
    }

    private Guest findGuest(Long id) {
        return guestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Guest not found with id " + id));
    }
}
