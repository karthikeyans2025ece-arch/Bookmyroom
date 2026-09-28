package com.example.bookmyroom.service;

import com.example.bookmyroom.dto.OccupancyReportResponse;
import com.example.bookmyroom.entity.Booking;
import com.example.bookmyroom.entity.BookingStatus;
import com.example.bookmyroom.exception.InvalidRequestException;
import com.example.bookmyroom.repository.BookingRepository;
import com.example.bookmyroom.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class OccupancyReportService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;

    public OccupancyReportService(BookingRepository bookingRepository, RoomRepository roomRepository) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
    }

    /**
     * Any booking that overlaps the month is counted, even if it starts in the previous
     * month or ends in the next one. Only the nights that fall inside the month are counted
     * as occupied nights, and revenue is split across months in the same proportion.
     */
    @Transactional(readOnly = true)
    public OccupancyReportResponse getMonthlyReport(String month) {
        YearMonth yearMonth = parseMonth(month);
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate nextMonthStart = yearMonth.plusMonths(1).atDay(1);

        List<Booking> confirmed = bookingRepository
                .findByStatusOverlappingPeriod(BookingStatus.CONFIRMED, monthStart, nextMonthStart);
        long cancelledCount = bookingRepository
                .countByStatusOverlappingPeriod(BookingStatus.CANCELLED, monthStart, nextMonthStart);

        Set<Long> bookedRoomIds = new HashSet<>();
        long occupiedNights = 0;
        BigDecimal revenue = BigDecimal.ZERO;

        for (Booking booking : confirmed) {
            LocalDate from = booking.getCheckInDate().isAfter(monthStart) ? booking.getCheckInDate() : monthStart;
            LocalDate to = booking.getCheckOutDate().isBefore(nextMonthStart) ? booking.getCheckOutDate() : nextMonthStart;

            long nightsInMonth = ChronoUnit.DAYS.between(from, to);
            long totalNights = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());

            occupiedNights += nightsInMonth;
            revenue = revenue.add(booking.getTotalCost()
                    .multiply(BigDecimal.valueOf(nightsInMonth))
                    .divide(BigDecimal.valueOf(totalNights), 2, RoundingMode.HALF_UP));
            bookedRoomIds.add(booking.getRoom().getId());
        }

        long totalRooms = roomRepository.count();
        long bookedRooms = bookedRoomIds.size();

        return new OccupancyReportResponse(
                yearMonth.toString(),
                totalRooms,
                bookedRooms,
                totalRooms - bookedRooms,
                confirmed.size(),
                cancelledCount,
                occupiedNights,
                revenue);
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new InvalidRequestException("Month must be in yyyy-MM format, for example 2026-10");
        }
    }
}
