package com.example.demo.controller;

import com.example.demo.dto.CheckoutRequestDto;
import com.example.demo.entity.*;
import com.example.demo.repository.AppointmentRepository;
import com.example.demo.repository.AppointmentTransactionRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.service.AppointmentAuditService;
import com.example.demo.service.TenantContextResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PosFinancialControllerTest {

    @Mock
    private AppointmentTransactionRepository transactionRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private com.example.demo.repository.TenantConfigRepository tenantConfigRepository;
    @Mock
    private com.example.demo.repository.UserRepository userRepository;
    @Mock
    private com.example.demo.repository.GiftCardRepository giftCardRepository;
    @Mock
    private com.example.demo.repository.EodRegisterCloseRepository eodRegisterCloseRepository;

    private final com.example.demo.security.JwtUtil jwtUtil = new com.example.demo.security.JwtUtil();

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        TenantContextResolver tenantContextResolver = new TenantContextResolver(tenantConfigRepository);
        PosFinancialController controller = new PosFinancialController(
                transactionRepository,
                appointmentRepository,
                customerRepository,
                tenantContextResolver,
                null,
                null,
                objectMapper,
                userRepository,
                giftCardRepository,
                eodRegisterCloseRepository,
                null,
                jwtUtil
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testCheckoutWithAddonsTipsAndTax() throws Exception {
        ServiceEntity svc = new ServiceEntity("default", "Gel Manicure Deluxe", "Mani", new BigDecimal("48.00"), 45);
        svc.setId(101L);

        UserEntity tony = new UserEntity();
        tony.setId(7L);
        tony.setName("Tony");

        Appointment appt = new Appointment();
        appt.setId(201L);
        appt.setTenantId("default");
        appt.setCustomerName("Sophia Miller");
        appt.setCustomerEmail("sophia@example.com");
        appt.setService(svc);
        appt.setNailTech(tony);
        appt.setStatus(Appointment.AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findByTenantIdAndId("default", 201L)).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(AppointmentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutRequestDto dto = new CheckoutRequestDto();
        dto.setAppointmentId(201L);
        dto.setLineItems(List.of(
                new CheckoutRequestDto.LineItemDto("Gel Manicure Deluxe", new BigDecimal("48.00"), "SERVICE"),
                new CheckoutRequestDto.LineItemDto("French Tips", new BigDecimal("10.00"), "ADDON"),
                new CheckoutRequestDto.LineItemDto("2-Finger Nail Art", new BigDecimal("15.00"), "ADDON")
        ));
        dto.setSubtotal(new BigDecimal("73.00"));
        dto.setTaxRate(new BigDecimal("0.0825"));
        dto.setTaxAmount(new BigDecimal("6.02"));
        dto.setTipPercent(new BigDecimal("20.00"));
        dto.setTipAmount(new BigDecimal("14.60"));
        dto.setTotalAmount(new BigDecimal("93.62"));
        dto.setPaymentMethod("CARD");
        dto.setColorFormula("OPI Bubble Bath #23");
        dto.setNotes("Client requested shiny finish");

        mockMvc.perform(post("/api/pos/checkout")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Appointment checked out and payment recorded successfully."))
                .andExpect(jsonPath("$.transaction.subtotal").value(73.00))
                .andExpect(jsonPath("$.transaction.taxAmount").value(6.02))
                .andExpect(jsonPath("$.transaction.tipAmount").value(14.60))
                .andExpect(jsonPath("$.transaction.totalAmount").value(93.62))
                .andExpect(jsonPath("$.transaction.nailTechName").value("Tony"))
                .andExpect(jsonPath("$.appointment.status").value("COMPLETED"));

        org.junit.jupiter.api.Assertions.assertEquals(Appointment.AppointmentStatus.COMPLETED, appt.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("OPI Bubble Bath #23", appt.getColorFormula());
    }

    @Test
    void testCheckoutWithSplitPaymentCardAndCash() throws Exception {
        ServiceEntity svc = new ServiceEntity("default", "Basic Pedicure", "Pedi", new BigDecimal("40.00"), 40);
        svc.setId(102L);

        Appointment appt = new Appointment();
        appt.setId(202L);
        appt.setTenantId("default");
        appt.setCustomerName("Emma Stone");
        appt.setService(svc);

        when(appointmentRepository.findByTenantIdAndId("default", 202L)).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(AppointmentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutRequestDto dto = new CheckoutRequestDto();
        dto.setAppointmentId(202L);
        dto.setSubtotal(new BigDecimal("40.00"));
        dto.setTaxAmount(new BigDecimal("3.30"));
        dto.setTipAmount(new BigDecimal("6.70"));
        dto.setTotalAmount(new BigDecimal("50.00"));
        dto.setPaymentMethod("SPLIT");
        dto.setSplitPayments(List.of(
                new CheckoutRequestDto.SplitPaymentDto("CARD", new BigDecimal("30.00")),
                new CheckoutRequestDto.SplitPaymentDto("CASH", new BigDecimal("20.00"))
        ));

        mockMvc.perform(post("/api/pos/checkout")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.paymentMethod").value("SPLIT"))
                .andExpect(jsonPath("$.transaction.splitDetails").value("$30.00 CARD + $20.00 CASH"))
                .andExpect(jsonPath("$.transaction.totalAmount").value(50.00));
    }

    @Test
    void testCheckoutRejectsUnbalancedSplitPayment() throws Exception {
        ServiceEntity svc = new ServiceEntity("default", "Basic Pedicure", "Pedi", new BigDecimal("40.00"), 40);
        Appointment appt = new Appointment();
        appt.setId(203L);
        appt.setTenantId("default");
        appt.setService(svc);

        when(appointmentRepository.findByTenantIdAndId("default", 203L)).thenReturn(Optional.of(appt));

        CheckoutRequestDto dto = new CheckoutRequestDto();
        dto.setAppointmentId(203L);
        dto.setTotalAmount(new BigDecimal("50.00"));
        dto.setPaymentMethod("SPLIT");
        // Split sum is $40 instead of $50
        dto.setSplitPayments(List.of(
                new CheckoutRequestDto.SplitPaymentDto("CARD", new BigDecimal("25.00")),
                new CheckoutRequestDto.SplitPaymentDto("CASH", new BigDecimal("15.00"))
        ));

        mockMvc.perform(post("/api/pos/checkout")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("does not match the invoice total")));
    }

    @Test
    void testDailyFinancialSummaryAndStaffPayouts() throws Exception {
        LocalDate date = LocalDate.of(2026, 11, 20);
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        // Transaction 1: Tony - $100 services + $20 card tip
        AppointmentTransaction tx1 = new AppointmentTransaction();
        tx1.setTenantId("default");
        tx1.setNailTechId(7L);
        tx1.setNailTechName("Tony");
        tx1.setSubtotal(new BigDecimal("100.00"));
        tx1.setTaxAmount(new BigDecimal("8.25"));
        tx1.setTipAmount(new BigDecimal("20.00"));
        tx1.setTotalAmount(new BigDecimal("128.25"));
        tx1.setPaymentMethod("CARD");

        // Transaction 2: Tony - $120 services + $35 cash tip
        AppointmentTransaction tx2 = new AppointmentTransaction();
        tx2.setTenantId("default");
        tx2.setNailTechId(7L);
        tx2.setNailTechName("Tony");
        tx2.setSubtotal(new BigDecimal("120.00"));
        tx2.setTaxAmount(new BigDecimal("9.90"));
        tx2.setTipAmount(new BigDecimal("35.00"));
        tx2.setTotalAmount(new BigDecimal("164.90"));
        tx2.setPaymentMethod("CASH");

        UserEntity tonyUser = new UserEntity();
        tonyUser.setId(7L);
        tonyUser.setName("Tony");
        tonyUser.setCommissionRate(new BigDecimal("60.00"));
        when(userRepository.findByTenantIdAndId("default", 7L)).thenReturn(Optional.of(tonyUser));

        when(transactionRepository.findAllByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc("default", start, end))
                .thenReturn(List.of(tx1, tx2));

        mockMvc.perform(get("/api/pos/daily-summary")
                        .header("X-Tenant-ID", "default")
                        .param("date", "2026-11-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGrossRevenue").value(293.15))
                .andExpect(jsonPath("$.totalServiceSales").value(220.00))
                .andExpect(jsonPath("$.totalTipsCollected").value(55.00))
                .andExpect(jsonPath("$.totalCommissionPaid").value(132.00))
                .andExpect(jsonPath("$.totalSalonHouseShare").value(88.00))
                .andExpect(jsonPath("$.transactionCount").value(2))
                .andExpect(jsonPath("$.staffPayouts[0].nailTechName").value("Tony"))
                .andExpect(jsonPath("$.staffPayouts[0].serviceSales").value(220.00))
                .andExpect(jsonPath("$.staffPayouts[0].commissionRate").value(60.00))
                .andExpect(jsonPath("$.staffPayouts[0].commissionEarned").value(132.00))
                .andExpect(jsonPath("$.staffPayouts[0].cardTips").value(20.00))
                .andExpect(jsonPath("$.staffPayouts[0].cashTips").value(35.00))
                .andExpect(jsonPath("$.staffPayouts[0].totalTips").value(55.00))
                .andExpect(jsonPath("$.staffPayouts[0].totalStaffPayout").value(187.00))
                .andExpect(jsonPath("$.staffPayouts[0].salonHouseShare").value(88.00));
    }

    @Test
    void testWeeklyPayrollReportAggregation() throws Exception {
        LocalDate start = LocalDate.of(2026, 11, 16);
        LocalDate end = LocalDate.of(2026, 11, 22);
        LocalDateTime rStart = start.atStartOfDay();
        LocalDateTime rEnd = end.plusDays(1).atStartOfDay();

        // Tony (60% split): $500 services + $100 tips
        AppointmentTransaction tx1 = new AppointmentTransaction();
        tx1.setTenantId("default");
        tx1.setNailTechId(7L);
        tx1.setNailTechName("Tony");
        tx1.setSubtotal(new BigDecimal("500.00"));
        tx1.setTaxAmount(new BigDecimal("41.25"));
        tx1.setTipAmount(new BigDecimal("100.00"));
        tx1.setTotalAmount(new BigDecimal("641.25"));
        tx1.setPaymentMethod("CARD");

        // Jenny (70% split): $800 services + $160 tips
        AppointmentTransaction tx2 = new AppointmentTransaction();
        tx2.setTenantId("default");
        tx2.setNailTechId(8L);
        tx2.setNailTechName("Jenny");
        tx2.setSubtotal(new BigDecimal("800.00"));
        tx2.setTaxAmount(new BigDecimal("66.00"));
        tx2.setTipAmount(new BigDecimal("160.00"));
        tx2.setTotalAmount(new BigDecimal("1026.00"));
        tx2.setPaymentMethod("CASH");

        UserEntity tonyUser = new UserEntity();
        tonyUser.setId(7L);
        tonyUser.setCommissionRate(new BigDecimal("60.00"));

        UserEntity jennyUser = new UserEntity();
        jennyUser.setId(8L);
        jennyUser.setCommissionRate(new BigDecimal("70.00"));

        when(userRepository.findByTenantIdAndId("default", 7L)).thenReturn(Optional.of(tonyUser));
        when(userRepository.findByTenantIdAndId("default", 8L)).thenReturn(Optional.of(jennyUser));

        when(transactionRepository.findAllByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc("default", rStart, rEnd))
                .thenReturn(List.of(tx1, tx2));

        mockMvc.perform(get("/api/pos/payroll-report")
                        .header("X-Tenant-ID", "default")
                        .param("startDate", "2026-11-16")
                        .param("endDate", "2026-11-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2026-11-16"))
                .andExpect(jsonPath("$.endDate").value("2026-11-22"))
                .andExpect(jsonPath("$.totalGrossRevenue").value(1667.25))
                .andExpect(jsonPath("$.totalServiceSales").value(1300.00))
                .andExpect(jsonPath("$.totalCommissionPaid").value(860.00)) // Tony: $300 (60%) + Jenny: $560 (70%)
                .andExpect(jsonPath("$.totalTipsDistributed").value(260.00))
                .andExpect(jsonPath("$.totalSalonHouseShare").value(440.00)) // Tony: $200 + Jenny: $240
                .andExpect(jsonPath("$.totalSalonRetained").value(547.25)) // $440 + $107.25 tax
                .andExpect(jsonPath("$.staffPayroll[0].nailTechName").value("Tony"))
                .andExpect(jsonPath("$.staffPayroll[0].commissionEarned").value(300.00))
                .andExpect(jsonPath("$.staffPayroll[0].totalStaffPayout").value(400.00)) // $300 comm + $100 tip
                .andExpect(jsonPath("$.staffPayroll[1].nailTechName").value("Jenny"))
                .andExpect(jsonPath("$.staffPayroll[1].commissionEarned").value(560.00))
                .andExpect(jsonPath("$.staffPayroll[1].totalStaffPayout").value(720.00)); // $560 comm + $160 tip
    }

    @Test
    void testExportPayrollCsvDownload() throws Exception {
        LocalDate start = LocalDate.of(2026, 11, 16);
        LocalDate end = LocalDate.of(2026, 11, 22);
        LocalDateTime rStart = start.atStartOfDay();
        LocalDateTime rEnd = end.plusDays(1).atStartOfDay();

        AppointmentTransaction tx = new AppointmentTransaction();
        tx.setTenantId("default");
        tx.setNailTechId(7L);
        tx.setNailTechName("Tony");
        tx.setSubtotal(new BigDecimal("200.00"));
        tx.setTaxAmount(new BigDecimal("16.50"));
        tx.setTipAmount(new BigDecimal("40.00"));
        tx.setTotalAmount(new BigDecimal("256.50"));
        tx.setPaymentMethod("CARD");

        UserEntity tonyUser = new UserEntity();
        tonyUser.setId(7L);
        tonyUser.setCommissionRate(new BigDecimal("60.00"));
        when(userRepository.findByTenantIdAndId("default", 7L)).thenReturn(Optional.of(tonyUser));

        when(transactionRepository.findAllByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc("default", rStart, rEnd))
                .thenReturn(List.of(tx));

        mockMvc.perform(get("/api/pos/payroll-report/export-csv")
                        .header("X-Tenant-ID", "default")
                        .param("startDate", "2026-11-16")
                        .param("endDate", "2026-11-22"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string(
                        "Content-Disposition", org.hamcrest.Matchers.containsString("payroll-report-default-2026-11-16-to-2026-11-22.csv")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("text/csv; charset=UTF-8"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                        org.hamcrest.Matchers.containsString("Technician ID,Technician Name,Commission Rate (%)")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                        org.hamcrest.Matchers.containsString("\"Tony\",60.00%,1,200.00,120.00,40.00,0.00,40.00,160.00,80.00")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                        org.hamcrest.Matchers.containsString("TOTAL,ALL TECHNICIANS,--,1,200.00,120.00,40.00,0.00,40.00,160.00,80.00")));
    }

    @Test
    void testCheckoutEarningLoyaltyPoints() throws Exception {
        ServiceEntity svc = new ServiceEntity("default", "Classic Manicure", "Mani", new BigDecimal("40.00"), 30);
        Appointment appt = new Appointment();
        appt.setId(301L);
        appt.setTenantId("default");
        appt.setCustomerEmail("vip@example.com");
        appt.setService(svc);
        appt.setStatus(Appointment.AppointmentStatus.CONFIRMED);

        Customer cust = new Customer();
        cust.setId(50L);
        cust.setTenantId("default");
        cust.setEmail("vip@example.com");
        cust.setLoyaltyPoints(25);

        when(appointmentRepository.findByTenantIdAndId("default", 301L)).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(AppointmentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepository.findByTenantIdAndEmail("default", "vip@example.com")).thenReturn(Optional.of(cust));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutRequestDto dto = new CheckoutRequestDto();
        dto.setAppointmentId(301L);
        dto.setLineItems(List.of(
                new CheckoutRequestDto.LineItemDto("Classic Manicure", new BigDecimal("40.00"), "SERVICE"),
                new CheckoutRequestDto.LineItemDto("French Tips", new BigDecimal("10.00"), "ADDON")
        ));
        dto.setSubtotal(new BigDecimal("50.00"));
        dto.setTaxRate(new BigDecimal("0.0825"));
        dto.setTaxAmount(new BigDecimal("4.13"));
        dto.setTipAmount(new BigDecimal("10.00"));
        dto.setTotalAmount(new BigDecimal("64.13"));
        dto.setPaymentMethod("CARD");

        mockMvc.perform(post("/api/pos/checkout")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loyaltyPointsEarned").value(50))
                .andExpect(jsonPath("$.loyaltyPointsBalance").value(75));

        verify(customerRepository).save(cust);
        org.junit.jupiter.api.Assertions.assertEquals(75, cust.getLoyaltyPoints());
    }

    @Test
    void testCheckoutRedeemingLoyaltyPointsForDiscount() throws Exception {
        ServiceEntity svc = new ServiceEntity("default", "Deluxe Gel", "Mani", new BigDecimal("60.00"), 45);
        Appointment appt = new Appointment();
        appt.setId(302L);
        appt.setTenantId("default");
        appt.setCustomerEmail("member@example.com");
        appt.setService(svc);
        appt.setStatus(Appointment.AppointmentStatus.CONFIRMED);

        Customer cust = new Customer();
        cust.setId(51L);
        cust.setTenantId("default");
        cust.setEmail("member@example.com");
        cust.setLoyaltyPoints(100);

        when(appointmentRepository.findByTenantIdAndId("default", 302L)).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(AppointmentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepository.findByTenantIdAndEmail("default", "member@example.com")).thenReturn(Optional.of(cust));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutRequestDto dto = new CheckoutRequestDto();
        dto.setAppointmentId(302L);
        dto.setLineItems(List.of(
                new CheckoutRequestDto.LineItemDto("Deluxe Gel", new BigDecimal("60.00"), "SERVICE")
        ));
        dto.setSubtotal(new BigDecimal("60.00"));
        dto.setRedeemPoints(50); // $5.00 discount (50 * $0.10)
        dto.setPointsDiscountAmount(new BigDecimal("5.00"));
        dto.setTaxRate(new BigDecimal("0.0825"));
        dto.setTaxAmount(new BigDecimal("4.95"));
        dto.setTipAmount(new BigDecimal("10.00"));
        dto.setTotalAmount(new BigDecimal("69.95"));
        dto.setPaymentMethod("CARD");

        mockMvc.perform(post("/api/pos/checkout")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsRedeemed").value(50))
                .andExpect(jsonPath("$.pointsDiscountAmount").value(5.00))
                .andExpect(jsonPath("$.loyaltyPointsEarned").value(60))
                // Starts at 100 - 50 redeemed + 60 earned = 110
                .andExpect(jsonPath("$.loyaltyPointsBalance").value(110));

        verify(customerRepository).save(cust);
        org.junit.jupiter.api.Assertions.assertEquals(110, cust.getLoyaltyPoints());
    }

    @Test
    void testCheckoutRedeemingGiftCardDeductsBalance() throws Exception {
        ServiceEntity svc = new ServiceEntity("default", "Spa Pedicure", "Pedi", new BigDecimal("55.00"), 45);
        Appointment appt = new Appointment();
        appt.setId(303L);
        appt.setTenantId("default");
        appt.setCustomerEmail("guest@example.com");
        appt.setService(svc);
        appt.setStatus(Appointment.AppointmentStatus.CONFIRMED);

        GiftCard gc = new GiftCard("default", "GC-GIFT-50", new BigDecimal("50.00"));
        gc.setStatus("ACTIVE");

        when(appointmentRepository.findByTenantIdAndId("default", 303L)).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(AppointmentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(giftCardRepository.findByTenantIdAndCodeIgnoreCase("default", "GC-GIFT-50")).thenReturn(Optional.of(gc));
        when(giftCardRepository.save(any(GiftCard.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutRequestDto dto = new CheckoutRequestDto();
        dto.setAppointmentId(303L);
        dto.setLineItems(List.of(
                new CheckoutRequestDto.LineItemDto("Spa Pedicure", new BigDecimal("55.00"), "SERVICE")
        ));
        dto.setSubtotal(new BigDecimal("55.00"));
        dto.setTaxRate(new BigDecimal("0.0825"));
        dto.setTaxAmount(new BigDecimal("4.54"));
        dto.setTipAmount(new BigDecimal("10.00"));
        dto.setTotalAmount(new BigDecimal("69.54"));
        dto.setPaymentMethod("GIFT_CARD");
        dto.setGiftCardCode("GC-GIFT-50");
        dto.setGiftCardAmount(new BigDecimal("35.00"));

        mockMvc.perform(post("/api/pos/checkout")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.giftCardAmountApplied").value(35.00));

        verify(giftCardRepository).save(gc);
        // Original $50.00 - $35.00 = $15.00
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("15.00"), gc.getCurrentBalance());
    }

    @Test
    void testVerifyOwnerPasswordSuccess() throws Exception {
        mockMvc.perform(post("/api/pos/verify-owner-password")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("password", "admin123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.ownerName").exists());
    }

    @Test
    void testVerifyOwnerPasswordFailure() throws Exception {
        mockMvc.perform(post("/api/pos/verify-owner-password")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("password", "wrongpassword!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testRegisterCloseCalculationAndPersistence() throws Exception {
        LocalDate today = LocalDate.now();

        AppointmentTransaction tx1 = new AppointmentTransaction();
        tx1.setTenantId("default");
        tx1.setSubtotal(new BigDecimal("100.00"));
        tx1.setTaxAmount(new BigDecimal("8.25"));
        tx1.setTipAmount(new BigDecimal("20.00"));
        tx1.setTotalAmount(new BigDecimal("128.25"));
        tx1.setPaymentMethod("CARD");
        tx1.setNailTechId(1L);
        tx1.setNailTechName("Tony");
        tx1.setCreatedAt(today.atTime(11, 0));

        AppointmentTransaction tx2 = new AppointmentTransaction();
        tx2.setTenantId("default");
        tx2.setSubtotal(new BigDecimal("50.00"));
        tx2.setTaxAmount(new BigDecimal("4.13"));
        tx2.setTipAmount(new BigDecimal("10.00"));
        tx2.setTotalAmount(new BigDecimal("64.13"));
        tx2.setPaymentMethod("CASH");
        tx2.setNailTechId(1L);
        tx2.setNailTechName("Tony");
        tx2.setCreatedAt(today.atTime(14, 0));

        when(transactionRepository.findAllByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                eq("default"), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(List.of(tx1, tx2));

        when(eodRegisterCloseRepository.findByTenantIdAndClosingDate("default", today))
                .thenReturn(Optional.empty());
        when(eodRegisterCloseRepository.save(any(EodRegisterClose.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Submit register close with $200 opening float and $264.13 actual cash counted ($200 float + $64.13 cash sale)
        java.util.Map<String, Object> payload = java.util.Map.of(
                "closingDate", today.toString(),
                "openingFloat", 200.00,
                "actualCash", 264.13,
                "notes", "All registers balanced"
        );

        mockMvc.perform(post("/api/pos/register-close")
                        .header("X-Tenant-ID", "default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closingRecord.status").value("CLOSED"))
                .andExpect(jsonPath("$.cashVariance").value(0.00))
                .andExpect(jsonPath("$.summary.cardBatchTotal").value(128.25))
                .andExpect(jsonPath("$.summary.cashCollected").value(64.13));

        verify(eodRegisterCloseRepository).save(any(EodRegisterClose.class));
    }
}
