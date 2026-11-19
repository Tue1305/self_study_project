package com.example.demo.controller;

import com.example.demo.dto.BookingRequestDto;
import com.example.demo.entity.Appointment;
import com.example.demo.entity.Customer;
import com.example.demo.entity.ServiceEntity;
import com.example.demo.entity.StaffDayOff;
import com.example.demo.entity.UserEntity;
import com.example.demo.repository.AppointmentRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.ServiceRepository;
import com.example.demo.repository.StaffDayOffRepository;
import com.example.demo.repository.TenantConfigRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffDayOffTest {

    private UserRepository userRepository;
    private ServiceRepository serviceRepository;
    private PasswordEncoder passwordEncoder;
    private TenantConfigRepository tenantConfigRepository;
    private TenantContextResolver tenantResolver;
    private StaffDayOffRepository staffDayOffRepository;
    private AppointmentRepository appointmentRepository;
    private CustomerRepository customerRepository;

    private NailTechController nailTechController;
    private AppointmentController appointmentController;
    private MockHttpServletRequest mockRequest;
    private Principal mockPrincipal;

    private final String TENANT = "test-salon";
    private UserEntity techChloe;
    private ServiceEntity gelManiService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        serviceRepository = mock(ServiceRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        tenantConfigRepository = mock(TenantConfigRepository.class);
        tenantResolver = new TenantContextResolver(tenantConfigRepository);
        staffDayOffRepository = mock(StaffDayOffRepository.class);
        appointmentRepository = mock(AppointmentRepository.class);
        customerRepository = mock(CustomerRepository.class);
        mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("X-Tenant-ID", TENANT);
        mockPrincipal = () -> "admin@salon.com";

        nailTechController = new NailTechController(userRepository, serviceRepository, passwordEncoder, tenantResolver);
        nailTechController.setStaffDayOffRepository(staffDayOffRepository);
        nailTechController.setAppointmentRepository(appointmentRepository);

        appointmentController = new AppointmentController(appointmentRepository, customerRepository, serviceRepository, userRepository, tenantResolver);
        appointmentController.setStaffDayOffRepository(staffDayOffRepository);

        techChloe = new UserEntity();
        techChloe.setId(5L);
        techChloe.setTenantId(TENANT);
        techChloe.setName("Chloe Vance");
        techChloe.setUsername("chloe");
        techChloe.setRole("ROLE_NAIL_TECH");
        techChloe.setSpecialty("MANICURE,PEDICURE");

        gelManiService = new ServiceEntity();
        gelManiService.setId(10L);
        gelManiService.setTenantId(TENANT);
        gelManiService.setName("Gel Manicure");
        gelManiService.setPrice(new BigDecimal("45.00"));
        gelManiService.setDurationMinutes(45);
    }

    @Test
    @DisplayName("NailTechController: toggleDayOff marks available staff as OFF and counts existing appointments")
    void testToggleDayOff_MarkOff() {
        LocalDate targetDate = LocalDate.now().plusDays(2);
        when(userRepository.findByTenantIdAndId(TENANT, 5L)).thenReturn(Optional.of(techChloe));
        when(staffDayOffRepository.findByTenantIdAndStaffIdAndOffDate(TENANT, 5L, targetDate)).thenReturn(Optional.empty());

        // Simulate 1 existing appointment on that date
        Appointment existingApt = new Appointment();
        existingApt.setId(101L);
        existingApt.setNailTech(techChloe);
        existingApt.setAppointmentTime(targetDate.atTime(11, 0));
        existingApt.setStatus(Appointment.AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findByTenantIdAndAppointmentTimeBetween(eq(TENANT), any(), any()))
                .thenReturn(List.of(existingApt));

        Map<String, Object> reqBody = Map.of("date", targetDate.toString(), "reason", "Vacation");
        ResponseEntity<?> resp = nailTechController.toggleDayOff(5L, reqBody, mockRequest, mockPrincipal);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertNotNull(body);
        assertEquals(true, body.get("isOff"));
        assertEquals(5L, body.get("staffId"));
        assertEquals("Chloe Vance", body.get("staffName"));
        assertEquals(1L, ((Number) body.get("existingAppointmentsCount")).longValue());
        verify(staffDayOffRepository, times(1)).save(any(StaffDayOff.class));
    }

    @Test
    @DisplayName("NailTechController: toggleDayOff unmarks OFF staff to Available")
    void testToggleDayOff_UnmarkToAvailable() {
        LocalDate targetDate = LocalDate.now().plusDays(2);
        StaffDayOff existingRecord = new StaffDayOff(TENANT, 5L, "Chloe Vance", targetDate, "Day Off", "admin");
        when(userRepository.findByTenantIdAndId(TENANT, 5L)).thenReturn(Optional.of(techChloe));
        when(staffDayOffRepository.findByTenantIdAndStaffIdAndOffDate(TENANT, 5L, targetDate)).thenReturn(Optional.of(existingRecord));

        Map<String, Object> reqBody = Map.of("date", targetDate.toString());
        ResponseEntity<?> resp = nailTechController.toggleDayOff(5L, reqBody, mockRequest, mockPrincipal);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertNotNull(body);
        assertEquals(false, body.get("isOff"));
        assertEquals(5L, body.get("staffId"));
        verify(staffDayOffRepository, times(1)).delete(existingRecord);
    }

    @Test
    @DisplayName("NailTechController: getDaysOff returns list for date")
    void testGetDaysOff() {
        LocalDate targetDate = LocalDate.now().plusDays(1);
        StaffDayOff record = new StaffDayOff(TENANT, 5L, "Chloe Vance", targetDate, "Sick", "admin");
        when(staffDayOffRepository.findAllByTenantIdAndOffDate(TENANT, targetDate)).thenReturn(List.of(record));

        List<StaffDayOff> results = nailTechController.getDaysOff(targetDate.toString(), mockRequest);
        assertEquals(1, results.size());
        assertEquals("Chloe Vance", results.get(0).getStaffName());
        assertEquals("Sick", results.get(0).getReason());
    }

    @Test
    @DisplayName("AppointmentController: getAvailableSlots marks OFF specialist as unavailable on that date")
    void testGetAvailableSlots_WhenTechIsOff() {
        // Pick a non-Sunday future date
        LocalDate futureDate = LocalDate.now().plusDays(5);
        if (futureDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            futureDate = futureDate.plusDays(1);
        }

        StaffDayOff record = new StaffDayOff(TENANT, 5L, "Chloe Vance", futureDate, "Day Off", "admin");
        when(staffDayOffRepository.findAllByTenantIdAndOffDate(TENANT, futureDate)).thenReturn(List.of(record));
        when(userRepository.findAllByTenantIdAndRole(TENANT, "ROLE_NAIL_TECH")).thenReturn(List.of(techChloe));
        when(userRepository.findByTenantIdAndId(TENANT, 5L)).thenReturn(Optional.of(techChloe));
        when(serviceRepository.findByTenantIdAndId(TENANT, 10L)).thenReturn(Optional.of(gelManiService));
        when(appointmentRepository.findByTenantIdAndAppointmentTimeBetween(eq(TENANT), any(), any())).thenReturn(List.of());

        ResponseEntity<?> resp = appointmentController.getAvailableSlots(
                futureDate.toString(), 45, 15, 5L, null, List.of(10L), null, null, null, mockRequest
        );

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertNotNull(body);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> slots = (List<Map<String, Object>>) body.get("availableSlots");
        assertNotNull(slots);
        // All slots for this tech should be marked available=false because tech is OFF
        for (Map<String, Object> slot : slots) {
            assertEquals(false, slot.get("available"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> breakdown = (List<Map<String, Object>>) slot.get("staffBreakdown");
            if (breakdown != null && !breakdown.isEmpty()) {
                assertEquals("Off on this date", breakdown.get(0).get("busyWindow"));
            }
        }
    }

    @Test
    @DisplayName("AppointmentController: bookAppointment rejects booking when specialist is OFF today")
    void testBookAppointment_RejectsWhenStaffOff() {
        LocalDate futureDate = LocalDate.now().plusDays(3);
        LocalDateTime aptTime = futureDate.atTime(14, 0);

        when(userRepository.findByTenantIdAndId(TENANT, 5L)).thenReturn(Optional.of(techChloe));
        when(serviceRepository.findByTenantIdAndId(TENANT, 10L)).thenReturn(Optional.of(gelManiService));
        when(staffDayOffRepository.existsByTenantIdAndStaffIdAndOffDate(TENANT, 5L, futureDate)).thenReturn(true);

        BookingRequestDto req = new BookingRequestDto();
        req.setCustomerName("Sarah Connor");
        req.setCustomerEmail("sarah@example.com");
        req.setCustomerPhone("(555) 234-5678");
        req.setAppointmentTime(aptTime);
        req.setNailTech(techChloe);
        req.setService(gelManiService);

        ResponseEntity<?> resp = appointmentController.bookAppointment(req, mockRequest);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertNotNull(body);
        assertTrue(body.get("message").toString().contains("Chloe Vance is off on this date"));
        verify(appointmentRepository, never()).save(any());
    }
}
