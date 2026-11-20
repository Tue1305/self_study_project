package com.example.demo.controller;

import com.example.demo.entity.Appointment;
import com.example.demo.entity.AppointmentTransaction;
import com.example.demo.entity.Customer;
import com.example.demo.entity.ServiceEntity;
import com.example.demo.entity.UserEntity;
import com.example.demo.repository.AppointmentRepository;
import com.example.demo.repository.AppointmentTransactionRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.StaffDayOffRepository;
import com.example.demo.repository.TenantConfigRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.TenantContextResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffPortalControllerTest {

    private UserRepository userRepository;
    private AppointmentRepository appointmentRepository;
    private AppointmentTransactionRepository transactionRepository;
    private CustomerRepository customerRepository;
    private StaffDayOffRepository staffDayOffRepository;
    private StaffPortalController controller;

    private UserEntity techChloe;
    private UserEntity techTony;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        appointmentRepository = mock(AppointmentRepository.class);
        transactionRepository = mock(AppointmentTransactionRepository.class);
        customerRepository = mock(CustomerRepository.class);
        staffDayOffRepository = mock(StaffDayOffRepository.class);
        TenantConfigRepository tenantConfigRepository = mock(TenantConfigRepository.class);

        TenantContextResolver resolver = new TenantContextResolver(tenantConfigRepository);
        controller = new StaffPortalController(
                userRepository, appointmentRepository, transactionRepository,
                customerRepository, staffDayOffRepository, resolver
        );

        techChloe = new UserEntity();
        techChloe.setId(5L);
        techChloe.setTenantId("default");
        techChloe.setUsername("chloe@salon.com");
        techChloe.setName("Chloe Vance");
        techChloe.setRole("ROLE_NAIL_TECH");
        techChloe.setCommissionRate(new BigDecimal("60.00"));

        techTony = new UserEntity();
        techTony.setId(7L);
        techTony.setTenantId("default");
        techTony.setUsername("tony@salon.com");
        techTony.setName("Tony");
        techTony.setRole("ROLE_NAIL_TECH");

        request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "default");

        // Authenticate as Chloe (ROLE_NAIL_TECH)
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "chloe@salon.com",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_NAIL_TECH"))
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void techSeesOnlyOwnAppointmentsAndPersonalEarnings() {
        LocalDate today = LocalDate.now();
        ServiceEntity mani = new ServiceEntity("default", "Gel Manicure", "Gel", new BigDecimal("50.00"), 45);

        Appointment chloeAppt = new Appointment();
        chloeAppt.setId(101L);
        chloeAppt.setTenantId("default");
        chloeAppt.setNailTech(techChloe);
        chloeAppt.setCustomerName("Emma Watson");
        chloeAppt.setCustomerEmail("emma@example.com");
        chloeAppt.setService(mani);
        chloeAppt.setAppointmentTime(today.atTime(11, 0));
        chloeAppt.setStatus(Appointment.AppointmentStatus.CONFIRMED);

        Appointment tonyAppt = new Appointment();
        tonyAppt.setId(102L);
        tonyAppt.setTenantId("default");
        tonyAppt.setNailTech(techTony);
        tonyAppt.setCustomerName("Other Client");
        tonyAppt.setService(mani);
        tonyAppt.setAppointmentTime(today.atTime(12, 0));
        tonyAppt.setStatus(Appointment.AppointmentStatus.CONFIRMED);

        Customer emma = new Customer("Emma Watson", "emma@example.com", "(555)-(111)-(2222)");
        emma.setMedicalFlags("Acetone allergy");
        emma.setColorFormula("OPI Bubble Bath");

        AppointmentTransaction chloeTx = new AppointmentTransaction();
        chloeTx.setNailTechId(5L);
        chloeTx.setSubtotal(new BigDecimal("100.00"));
        chloeTx.setTipAmount(new BigDecimal("20.00"));
        chloeTx.setPaymentMethod("CARD");

        AppointmentTransaction tonyTx = new AppointmentTransaction();
        tonyTx.setNailTechId(7L);
        tonyTx.setSubtotal(new BigDecimal("500.00"));
        tonyTx.setTipAmount(new BigDecimal("90.00"));
        tonyTx.setPaymentMethod("CASH");

        when(userRepository.findByTenantIdAndUsernameIgnoreCase("default", "chloe@salon.com")).thenReturn(Optional.of(techChloe));
        when(staffDayOffRepository.findByTenantIdAndStaffIdAndOffDate("default", 5L, today)).thenReturn(Optional.empty());
        when(appointmentRepository.findByTenantIdAndAppointmentTimeBetween(eq("default"), any(), any())).thenReturn(List.of(chloeAppt, tonyAppt));
        when(customerRepository.findByTenantIdAndEmail("default", "emma@example.com")).thenReturn(Optional.of(emma));
        when(transactionRepository.findAllByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc(eq("default"), any(), any()))
                .thenReturn(List.of(chloeTx, tonyTx));

        // Even if Chloe tries passing staffId=7 (Tony), ROLE_NAIL_TECH must only see her own data!
        ResponseEntity<?> resp = controller.getMyPortalData(today.toString(), 7L, request);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertNotNull(body);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> appts = (List<Map<String, Object>>) body.get("appointments");
        assertEquals(1, appts.size());
        assertEquals("Emma Watson", appts.get(0).get("customerName"));
        assertEquals("Acetone allergy", appts.get(0).get("medicalFlags"));
        assertEquals("OPI Bubble Bath", appts.get(0).get("colorFormula"));

        @SuppressWarnings("unchecked")
        Map<String, Object> dayEarnings = (Map<String, Object>) body.get("dayEarnings");
        // 60% of $100 = $60 commission + $20 tip = $80 take-home (Tony's $500 excluded!)
        assertEquals(new BigDecimal("60.00"), dayEarnings.get("commissionEarned"));
        assertEquals(new BigDecimal("20.00"), dayEarnings.get("totalTips"));
        assertEquals(new BigDecimal("80.00"), dayEarnings.get("totalTakeHome"));
    }

    @Test
    void techCannotUpdateAnotherTechsAppointmentNote() {
        Appointment tonyAppt = new Appointment();
        tonyAppt.setId(202L);
        tonyAppt.setTenantId("default");
        tonyAppt.setNailTech(techTony);

        when(userRepository.findByTenantIdAndUsernameIgnoreCase("default", "chloe@salon.com")).thenReturn(Optional.of(techChloe));
        when(appointmentRepository.findByTenantIdAndId("default", 202L)).thenReturn(Optional.of(tonyAppt));

        ResponseEntity<?> resp = controller.updateMyAppointmentNote(202L, Map.of("colorFormula", "Hack"), request);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        verify(appointmentRepository, never()).save(any());
    }
}
