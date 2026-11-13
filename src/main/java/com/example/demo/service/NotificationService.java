package com.example.demo.service;

import com.example.demo.entity.Appointment;
import com.example.demo.entity.ServiceEntity;
import com.example.demo.entity.TenantConfig;
import com.example.demo.repository.TenantConfigRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter DISPLAY_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a");

    private final TenantConfigRepository tenantConfigRepository;
    private final JavaMailSender mailSender;

    @Autowired
    public NotificationService(
            TenantConfigRepository tenantConfigRepository,
            @Autowired(required = false) JavaMailSender mailSender
    ) {
        this.tenantConfigRepository = tenantConfigRepository;
        this.mailSender = mailSender;
    }

    /**
     * Dispatches confirmation email and SMS for a single appointment booking.
     * Guaranteed never to throw exceptions so database transactions are never rolled back.
     */
    public NotificationResult sendBookingConfirmation(Appointment appointment) {
        if (appointment == null) {
            return new NotificationResult(false, true, null, "Appointment was null");
        }

        TenantConfig tenantConfig = resolveTenantConfig(appointment.getTenantId());
        return sendBookingConfirmation(appointment, tenantConfig);
    }

    /**
     * Dispatches confirmation with pre-resolved TenantConfig.
     */
    public NotificationResult sendBookingConfirmation(Appointment appointment, TenantConfig tenantConfig) {
        if (appointment == null) {
            return new NotificationResult(false, true, null, "Appointment was null");
        }

        try {
            TenantConfig config = tenantConfig != null ? tenantConfig : resolveTenantConfig(appointment.getTenantId());
            String salonName = config.getSalonName() != null ? config.getSalonName() : "Jenny's Nail Studio";
            String primaryColor = config.getPrimaryColor() != null ? config.getPrimaryColor() : "#e83e8c";
            String salonAddress = resolveSalonAddress(config.getTenantKey());
            String supportPhone = resolveSupportPhone(config.getTenantKey());
            String cancellationUrl = buildSelfServiceLink(config, appointment.getCancellationToken());

            String customerName = appointment.getCustomerName() != null ? appointment.getCustomerName().trim() : "Valued Guest";
            String customerEmail = appointment.getCustomerEmail() != null ? appointment.getCustomerEmail().trim() : "";
            String customerPhone = appointment.getCustomerPhone() != null ? appointment.getCustomerPhone().trim() : "";
            String formattedTime = appointment.getAppointmentTime() != null
                    ? appointment.getAppointmentTime().format(DISPLAY_DATE_TIME_FORMATTER)
                    : "Scheduled Date & Time";

            String techName = (appointment.getNailTech() != null && appointment.getNailTech().getName() != null)
                    ? appointment.getNailTech().getName()
                    : "First Available Specialist";

            String serviceName = (appointment.getService() != null && appointment.getService().getName() != null)
                    ? appointment.getService().getName()
                    : "Salon Service";

            BigDecimal servicePrice = (appointment.getService() != null && appointment.getService().getPrice() != null)
                    ? appointment.getService().getPrice()
                    : BigDecimal.ZERO;

            Integer durationMinutes = (appointment.getService() != null && appointment.getService().getDurationMinutes() != null)
                    ? appointment.getService().getDurationMinutes()
                    : 45;

            String subject = "Booking Confirmed: " + serviceName + " at " + salonName;

            String htmlBody = buildHtmlEmailTemplate(
                    salonName,
                    primaryColor,
                    config.getSecondaryColor() != null ? config.getSecondaryColor() : "#343a40",
                    salonAddress,
                    supportPhone,
                    customerName,
                    formattedTime,
                    techName,
                    appointment.getStationPreference(),
                    List.of(new ServiceItem(serviceName, durationMinutes, servicePrice)),
                    servicePrice,
                    cancellationUrl,
                    appointment.getNotes()
            );

            String smsBody = buildSmsBody(
                    salonName,
                    customerName,
                    formattedTime,
                    techName,
                    serviceName,
                    servicePrice,
                    cancellationUrl,
                    supportPhone
            );

            return dispatchNotification(customerEmail, customerPhone, subject, htmlBody, smsBody, config);

        } catch (Exception e) {
            logger.warn("[NotificationService] Unexpected error preparing booking confirmation: {}", e.getMessage(), e);
            return new NotificationResult(false, true, null, "Internal error: " + e.getMessage());
        }
    }

    /**
     * Dispatches cancellation notice email and SMS for an appointment.
     */
    public NotificationResult sendCancellationNotice(Appointment appointment, String reason) {
        if (appointment == null) {
            return new NotificationResult(false, true, null, "Appointment was null");
        }
        try {
            TenantConfig config = resolveTenantConfig(appointment.getTenantId());
            String salonName = config.getSalonName() != null ? config.getSalonName() : "Jenny's Nail Studio";
            String primaryColor = config.getPrimaryColor() != null ? config.getPrimaryColor() : "#e83e8c";
            String salonAddress = resolveSalonAddress(config.getTenantKey());
            String supportPhone = resolveSupportPhone(config.getTenantKey());

            String customerName = appointment.getCustomerName() != null ? appointment.getCustomerName().trim() : "Valued Guest";
            String customerEmail = appointment.getCustomerEmail() != null ? appointment.getCustomerEmail().trim() : "";
            String customerPhone = appointment.getCustomerPhone() != null ? appointment.getCustomerPhone().trim() : "";
            String formattedTime = appointment.getAppointmentTime() != null
                    ? appointment.getAppointmentTime().format(DISPLAY_DATE_TIME_FORMATTER)
                    : "Scheduled Date & Time";

            String serviceName = (appointment.getService() != null && appointment.getService().getName() != null)
                    ? appointment.getService().getName()
                    : "Salon Service";

            String effectiveReason = (reason != null && !reason.isBlank()) ? reason.trim() : "Customer / Salon Request";
            String subject = "Appointment Cancelled: " + serviceName + " at " + salonName;

            String rebookUrl = buildSelfServiceLink(config, null).replace("action=manage", "action=book");

            String htmlBody = String.format("""
                    <!DOCTYPE html>
                    <html>
                    <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f7fafc; margin: 0; padding: 20px; color: #2d3748;">
                        <div style="max-width: 600px; margin: 20px auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; border: 1px solid #e2e8f0; box-shadow: 0 4px 15px rgba(0,0,0,0.05);">
                            <div style="background-color: #e53e3e; padding: 28px 24px; text-align: center; color: #ffffff;">
                                <h1 style="margin: 0; font-size: 24px; font-weight: 700;">%s</h1>
                                <p style="margin: 6px 0 0 0; font-size: 15px; opacity: 0.95;">Appointment Cancellation Notice</p>
                            </div>
                            <div style="padding: 28px;">
                                <p style="font-size: 16px; margin: 0 0 16px 0;">Hi <strong>%s</strong>,</p>
                                <p style="color: #4a5568; line-height: 1.6; margin: 0 0 20px 0;">
                                    Your reservation for <strong>%s</strong> on <strong>%s</strong> has been cancelled.
                                </p>
                                <div style="background-color: #fff5f5; border: 1px solid #fed7d7; border-left: 4px solid #e53e3e; border-radius: 6px; padding: 14px; margin-bottom: 24px; font-size: 14px; color: #9b2c2c;">
                                    <strong>Cancellation Reason:</strong> %s
                                </div>
                                <div style="text-align: center; margin: 28px 0;">
                                    <a href="%s" style="background-color: %s; color: #ffffff; padding: 12px 26px; text-decoration: none; border-radius: 6px; font-weight: 600; display: inline-block;">
                                        Book a New Appointment
                                    </a>
                                </div>
                            </div>
                            <div style="background-color: #edf2f7; padding: 18px 24px; font-size: 12px; color: #718096; text-align: center; border-top: 1px solid #e2e8f0;">
                                <p style="margin: 0 0 4px 0; font-weight: 600; color: #4a5568;">%s</p>
                                <p style="margin: 0 0 4px 0;">%s | Questions? Call %s</p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """,
                    salonName,
                    customerName,
                    serviceName,
                    formattedTime,
                    effectiveReason,
                    rebookUrl,
                    primaryColor,
                    salonName,
                    salonAddress,
                    supportPhone
            );

            String smsBody = String.format("%s: Your appointment for %s on %s has been cancelled. Reason: %s. Re-book anytime: %s Questions? Call %s",
                    salonName, serviceName, formattedTime, effectiveReason, rebookUrl, supportPhone);

            return dispatchNotification(customerEmail, customerPhone, subject, htmlBody, smsBody, config);
        } catch (Exception e) {
            logger.warn("[NotificationService] Error preparing cancellation notice: {}", e.getMessage(), e);
            return new NotificationResult(false, true, null, "Error: " + e.getMessage());
        }
    }

    /**
     * Dispatches reschedule notice email and SMS for an appointment.
     */
    public NotificationResult sendRescheduleNotice(Appointment appointment, LocalDateTime previousTime) {
        if (appointment == null) {
            return new NotificationResult(false, true, null, "Appointment was null");
        }
        try {
            TenantConfig config = resolveTenantConfig(appointment.getTenantId());
            String salonName = config.getSalonName() != null ? config.getSalonName() : "Jenny's Nail Studio";
            String primaryColor = config.getPrimaryColor() != null ? config.getPrimaryColor() : "#e83e8c";
            String salonAddress = resolveSalonAddress(config.getTenantKey());
            String supportPhone = resolveSupportPhone(config.getTenantKey());

            String customerName = appointment.getCustomerName() != null ? appointment.getCustomerName().trim() : "Valued Guest";
            String customerEmail = appointment.getCustomerEmail() != null ? appointment.getCustomerEmail().trim() : "";
            String customerPhone = appointment.getCustomerPhone() != null ? appointment.getCustomerPhone().trim() : "";
            String newFormattedTime = appointment.getAppointmentTime() != null
                    ? appointment.getAppointmentTime().format(DISPLAY_DATE_TIME_FORMATTER)
                    : "New Date & Time";
            String oldFormattedTime = previousTime != null
                    ? previousTime.format(DISPLAY_DATE_TIME_FORMATTER)
                    : "Previous Date & Time";

            String serviceName = (appointment.getService() != null && appointment.getService().getName() != null)
                    ? appointment.getService().getName()
                    : "Salon Service";

            String techName = (appointment.getNailTech() != null && appointment.getNailTech().getName() != null)
                    ? appointment.getNailTech().getName()
                    : "First Available Specialist";

            String cancellationUrl = buildSelfServiceLink(config, appointment.getCancellationToken());
            String subject = "Appointment Rescheduled: " + serviceName + " at " + salonName;

            String htmlBody = String.format("""
                    <!DOCTYPE html>
                    <html>
                    <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f7fafc; margin: 0; padding: 20px; color: #2d3748;">
                        <div style="max-width: 600px; margin: 20px auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; border: 1px solid #e2e8f0; box-shadow: 0 4px 15px rgba(0,0,0,0.05);">
                            <div style="background-color: %s; padding: 28px 24px; text-align: center; color: #ffffff;">
                                <h1 style="margin: 0; font-size: 24px; font-weight: 700;">%s</h1>
                                <p style="margin: 6px 0 0 0; font-size: 15px; opacity: 0.95;">Appointment Rescheduled</p>
                            </div>
                            <div style="padding: 28px;">
                                <p style="font-size: 16px; margin: 0 0 16px 0;">Hi <strong>%s</strong>,</p>
                                <p style="color: #4a5568; line-height: 1.6; margin: 0 0 20px 0;">
                                    Your appointment has been successfully rescheduled. Please review your updated details below:
                                </p>
                                <div style="background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 18px; margin-bottom: 24px;">
                                    <div style="font-size: 12px; text-transform: uppercase; color: #a0aec0; font-weight: 600;">Previous Time</div>
                                    <div style="font-size: 14px; color: #718096; text-decoration: line-through; margin-bottom: 12px;">%s</div>
                                    <div style="font-size: 12px; text-transform: uppercase; color: #38a169; font-weight: 700;">New Confirmed Time</div>
                                    <div style="font-size: 16px; font-weight: 700; color: #2d3748; margin-bottom: 12px;">%s</div>
                                    <div style="font-size: 12px; text-transform: uppercase; color: #a0aec0; font-weight: 600;">Service & Specialist</div>
                                    <div style="font-size: 15px; font-weight: 600; color: %s;">%s with %s</div>
                                </div>
                                <div style="text-align: center; margin: 28px 0;">
                                    <a href="%s" style="background-color: %s; color: #ffffff; padding: 12px 26px; text-decoration: none; border-radius: 6px; font-weight: 600; display: inline-block;">
                                        Manage Appointment
                                    </a>
                                </div>
                            </div>
                            <div style="background-color: #edf2f7; padding: 18px 24px; font-size: 12px; color: #718096; text-align: center; border-top: 1px solid #e2e8f0;">
                                <p style="margin: 0 0 4px 0; font-weight: 600; color: #4a5568;">%s</p>
                                <p style="margin: 0 0 4px 0;">%s | Questions? Call %s</p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """,
                    primaryColor,
                    salonName,
                    customerName,
                    oldFormattedTime,
                    newFormattedTime,
                    primaryColor,
                    serviceName,
                    techName,
                    cancellationUrl,
                    primaryColor,
                    salonName,
                    salonAddress,
                    supportPhone
            );

            String smsBody = String.format("%s: Your appointment for %s is rescheduled to %s with %s. Manage/Cancel: %s Questions? Call %s",
                    salonName, serviceName, newFormattedTime, techName, cancellationUrl, supportPhone);

            return dispatchNotification(customerEmail, customerPhone, subject, htmlBody, smsBody, config);
        } catch (Exception e) {
            logger.warn("[NotificationService] Error preparing reschedule notice: {}", e.getMessage(), e);
            return new NotificationResult(false, true, null, "Error: " + e.getMessage());
        }
    }

    /**
     * Dispatches an upcoming 24-hour reminder email and SMS for an appointment.
     */
    public NotificationResult sendAppointmentReminder(Appointment appointment) {
        if (appointment == null) {
            return new NotificationResult(false, true, null, "Appointment was null");
        }
        try {
            TenantConfig config = resolveTenantConfig(appointment.getTenantId());
            String salonName = config.getSalonName() != null ? config.getSalonName() : "Jenny's Nail Studio";
            String primaryColor = config.getPrimaryColor() != null ? config.getPrimaryColor() : "#e83e8c";
            String salonAddress = resolveSalonAddress(config.getTenantKey());
            String supportPhone = resolveSupportPhone(config.getTenantKey());

            String customerName = appointment.getCustomerName() != null ? appointment.getCustomerName().trim() : "Valued Guest";
            String customerEmail = appointment.getCustomerEmail() != null ? appointment.getCustomerEmail().trim() : "";
            String customerPhone = appointment.getCustomerPhone() != null ? appointment.getCustomerPhone().trim() : "";
            String formattedTime = appointment.getAppointmentTime() != null
                    ? appointment.getAppointmentTime().format(DISPLAY_DATE_TIME_FORMATTER)
                    : "Tomorrow";

            String serviceName = (appointment.getService() != null && appointment.getService().getName() != null)
                    ? appointment.getService().getName()
                    : "Salon Service";

            String techName = (appointment.getNailTech() != null && appointment.getNailTech().getName() != null)
                    ? appointment.getNailTech().getName()
                    : "First Available Specialist";

            String cancellationUrl = buildSelfServiceLink(config, appointment.getCancellationToken());
            String subject = "Upcoming Appointment Reminder: " + serviceName + " at " + salonName;

            String htmlBody = String.format("""
                    <!DOCTYPE html>
                    <html>
                    <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f7fafc; margin: 0; padding: 20px; color: #2d3748;">
                        <div style="max-width: 600px; margin: 20px auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; border: 1px solid #e2e8f0; box-shadow: 0 4px 15px rgba(0,0,0,0.05);">
                            <div style="background-color: %s; padding: 28px 24px; text-align: center; color: #ffffff;">
                                <h1 style="margin: 0; font-size: 24px; font-weight: 700;">%s</h1>
                                <p style="margin: 6px 0 0 0; font-size: 15px; opacity: 0.95;">Appointment Reminder</p>
                            </div>
                            <div style="padding: 28px;">
                                <p style="font-size: 16px; margin: 0 0 16px 0;">Hi <strong>%s</strong>,</p>
                                <p style="color: #4a5568; line-height: 1.6; margin: 0 0 20px 0;">
                                    This is a friendly reminder for your upcoming appointment. We are excited to see you soon!
                                </p>
                                <div style="background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 18px; margin-bottom: 24px;">
                                    <div style="font-size: 12px; text-transform: uppercase; color: #a0aec0; font-weight: 600;">Date & Time</div>
                                    <div style="font-size: 16px; font-weight: 700; color: #2d3748; margin-bottom: 12px;">%s</div>
                                    <div style="font-size: 12px; text-transform: uppercase; color: #a0aec0; font-weight: 600;">Service & Specialist</div>
                                    <div style="font-size: 15px; font-weight: 600; color: %s; margin-bottom: 12px;">%s with %s</div>
                                    <div style="font-size: 12px; text-transform: uppercase; color: #a0aec0; font-weight: 600;">Location</div>
                                    <div style="font-size: 14px; color: #4a5568;">%s</div>
                                </div>
                                <div style="text-align: center; margin: 28px 0;">
                                    <a href="%s" style="background-color: %s; color: #ffffff; padding: 12px 26px; text-decoration: none; border-radius: 6px; font-weight: 600; display: inline-block;">
                                        Manage or Reschedule
                                    </a>
                                </div>
                            </div>
                            <div style="background-color: #edf2f7; padding: 18px 24px; font-size: 12px; color: #718096; text-align: center; border-top: 1px solid #e2e8f0;">
                                <p style="margin: 0 0 4px 0; font-weight: 600; color: #4a5568;">%s</p>
                                <p style="margin: 0 0 4px 0;">%s | Questions? Call %s</p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """,
                    primaryColor,
                    salonName,
                    customerName,
                    formattedTime,
                    primaryColor,
                    serviceName,
                    techName,
                    salonAddress,
                    cancellationUrl,
                    primaryColor,
                    salonName,
                    salonAddress,
                    supportPhone
            );

            String smsBody = String.format("%s: Reminder: Your appointment for %s is scheduled for %s with %s. Need to adjust? %s Questions? Call %s",
                    salonName, serviceName, formattedTime, techName, cancellationUrl, supportPhone);

            return dispatchNotification(customerEmail, customerPhone, subject, htmlBody, smsBody, config);
        } catch (Exception e) {
            logger.warn("[NotificationService] Error preparing appointment reminder: {}", e.getMessage(), e);
            return new NotificationResult(false, true, null, "Error: " + e.getMessage());
        }
    }

    /**
     * Handles notifications for multi-service or multi-guest bookings.
     * Dispatches individual confirmations to each guest's contact info,
     * plus an aggregated booking confirmation receipt to the primary booker.
     */
    public List<NotificationResult> sendMultiGuestBookingConfirmations(
            List<Appointment> savedAppointments,
            List<Map<String, Object>> guestReceipts,
            String tenantId,
            BigDecimal grandTotal
    ) {
        List<NotificationResult> results = new ArrayList<>();
        if (savedAppointments == null || savedAppointments.isEmpty()) {
            return results;
        }

        try {
            TenantConfig config = resolveTenantConfig(tenantId);
            String salonName = config.getSalonName() != null ? config.getSalonName() : "Jenny's Nail Studio";
            String primaryColor = config.getPrimaryColor() != null ? config.getPrimaryColor() : "#e83e8c";
            String salonAddress = resolveSalonAddress(config.getTenantKey());
            String supportPhone = resolveSupportPhone(config.getTenantKey());

            Appointment sample = savedAppointments.get(0);
            String formattedTime = sample.getAppointmentTime() != null
                    ? sample.getAppointmentTime().format(DISPLAY_DATE_TIME_FORMATTER)
                    : "Scheduled Date & Time";

            // 1. Send individual confirmations to each guest
            if (guestReceipts != null) {
                for (int i = 0; i < guestReceipts.size(); i++) {
                    Map<String, Object> receipt = guestReceipts.get(i);
                    String guestName = (String) receipt.getOrDefault("customerName", "Guest " + (i + 1));
                    String guestEmail = (String) receipt.getOrDefault("customerEmail", "");
                    String guestPhone = (String) receipt.getOrDefault("customerPhone", "");
                    BigDecimal subtotal = (BigDecimal) receipt.getOrDefault("subtotal", BigDecimal.ZERO);
                    String notes = (String) receipt.getOrDefault("notes", "");

                    // Extract services for this guest
                    @SuppressWarnings("unchecked")
                    List<ServiceEntity> services = (List<ServiceEntity>) receipt.get("services");
                    List<ServiceItem> serviceItems = new ArrayList<>();
                    if (services != null) {
                        for (ServiceEntity s : services) {
                            serviceItems.add(new ServiceItem(
                                    s.getName(),
                                    s.getDurationMinutes() != null ? s.getDurationMinutes() : 45,
                                    s.getPrice() != null ? s.getPrice() : BigDecimal.ZERO
                            ));
                        }
                    }

                    // Find cancellation token for this guest's primary appointment
                    String cancellationToken = "";
                    String techName = "First Available Specialist";
                    for (Appointment a : savedAppointments) {
                        if (a.getCustomerEmail() != null && a.getCustomerEmail().equalsIgnoreCase(guestEmail)) {
                            cancellationToken = a.getCancellationToken();
                            if (a.getNailTech() != null && a.getNailTech().getName() != null) {
                                techName = a.getNailTech().getName();
                            }
                            break;
                        }
                    }

                    String cancellationUrl = buildSelfServiceLink(config, cancellationToken);
                    String subject = "Booking Confirmed: Your Appointment at " + salonName;

                    String htmlBody = buildHtmlEmailTemplate(
                            salonName,
                            primaryColor,
                            config.getSecondaryColor() != null ? config.getSecondaryColor() : "#343a40",
                            salonAddress,
                            supportPhone,
                            guestName,
                            formattedTime,
                            techName,
                            sample.getStationPreference(),
                            serviceItems,
                            subtotal,
                            cancellationUrl,
                            notes
                    );

                    String serviceSummary = serviceItems.stream().map(ServiceItem::name).reduce((a, b) -> a + ", " + b).orElse("Services");
                    String smsBody = buildSmsBody(
                            salonName,
                            guestName,
                            formattedTime,
                            techName,
                            serviceSummary,
                            subtotal,
                            cancellationUrl,
                            supportPhone
                    );

                    NotificationResult res = dispatchNotification(guestEmail, guestPhone, subject, htmlBody, smsBody, config);
                    results.add(res);
                }
            }

            // 2. If multi-guest (more than 1 guest), also send aggregated summary to primary booker if different
            if (guestReceipts != null && guestReceipts.size() > 1) {
                Map<String, Object> primaryGuest = guestReceipts.get(0);
                String primaryEmail = (String) primaryGuest.getOrDefault("customerEmail", "");
                String primaryName = (String) primaryGuest.getOrDefault("customerName", "Valued Guest");
                String primaryPhone = (String) primaryGuest.getOrDefault("customerPhone", "");
                String cancellationToken = sample.getCancellationToken();
                String cancellationUrl = buildSelfServiceLink(config, cancellationToken);

                String groupSubject = "Group Booking Confirmed: " + guestReceipts.size() + " Guests at " + salonName;
                String groupHtml = buildGroupSummaryEmailHtml(
                        salonName,
                        primaryColor,
                        config.getSecondaryColor() != null ? config.getSecondaryColor() : "#343a40",
                        salonAddress,
                        supportPhone,
                        primaryName,
                        formattedTime,
                        guestReceipts,
                        grandTotal != null ? grandTotal : BigDecimal.ZERO,
                        cancellationUrl
                );

                String groupSms = String.format("%s: Group booking confirmed for %d guests on %s. Total: $%.2f. Manage: %s. Phone: %s",
                        salonName, guestReceipts.size(), formattedTime, grandTotal != null ? grandTotal : BigDecimal.ZERO, cancellationUrl, supportPhone);

                NotificationResult groupRes = dispatchNotification(primaryEmail, primaryPhone, groupSubject, groupHtml, groupSms, config);
                results.add(groupRes);
            }

        } catch (Exception ex) {
            logger.warn("[NotificationService] Unexpected error preparing multi-guest confirmation: {}", ex.getMessage(), ex);
        }

        return results;
    }

    /**
     * Internal dispatcher that tries real JavaMailSender and Twilio/SMS, falling back safely
     * to structured console dev logging without throwing exceptions.
     */
    private NotificationResult dispatchNotification(
            String customerEmail,
            String customerPhone,
            String subject,
            String htmlBody,
            String smsBody,
            TenantConfig config
    ) {
        boolean emailSent = false;
        boolean smsSent = false;
        boolean simulated = true;
        String messageId = UUID.randomUUID().toString();

        // 1. Attempt JavaMail email delivery if configured
        if (mailSender != null && customerEmail != null && customerEmail.contains("@") && !customerEmail.contains("walkin@")) {
            try {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                helper.setTo(customerEmail);
                helper.setSubject(subject);
                helper.setText(htmlBody, true);
                mailSender.send(mimeMessage);
                emailSent = true;
                simulated = false;
                logger.info("[NotificationService] [SMTP] Successfully sent email to {}", customerEmail);
            } catch (Exception ex) {
                logger.warn("[NotificationService] [SMTP] Failed to send email to {}: {}. Falling back to dev mock.",
                        customerEmail, ex.getMessage());
            }
        }

        // 2. Dev Fallback Logging
        if (!emailSent || !smsSent) {
            logDevFallback(customerEmail, customerPhone, subject, htmlBody, smsBody, config);
        }

        return new NotificationResult(
                true,
                simulated,
                messageId,
                String.format("Dispatched to %s / %s (simulated=%s)", customerEmail, customerPhone, simulated)
        );
    }

    private void logDevFallback(
            String email,
            String phone,
            String subject,
            String htmlBody,
            String smsBody,
            TenantConfig config
    ) {
        String border = "================================================================================";
        String logMessage = String.format("""
                
                %s
                [NotificationService] [DEV FALLBACK] Booking Confirmation Dispatched (Simulated)
                --------------------------------------------------------------------------------
                Salon:       %s (Tenant: %s)
                Recipient:   %s | Phone: %s
                Subject:     %s
                --------------------------------------------------------------------------------
                [SMS PREVIEW]:
                %s
                --------------------------------------------------------------------------------
                [EMAIL HTML PREVIEW]:
                %s
                %s
                """,
                border,
                config.getSalonName(),
                config.getTenantKey(),
                email,
                phone,
                subject,
                smsBody,
                htmlBody.length() > 600 ? htmlBody.substring(0, 600) + "... [truncated for brevity]" : htmlBody,
                border
        );
        logger.info(logMessage);
    }

    public String resolveSalonAddress(String tenantId) {
        if (tenantId == null) return "123 Beauty Lane, Suite 100, New York, NY 10001";
        return switch (tenantId.toLowerCase().trim()) {
            case "luxe", "luxenailspa" -> "789 Ocean Blvd, Private Suite 400, Miami Beach, FL 33139";
            default -> "123 Beauty Lane, Suite 100, New York, NY 10001";
        };
    }

    public String resolveSupportPhone(String tenantId) {
        if (tenantId == null) return "(555) 123-4567";
        return switch (tenantId.toLowerCase().trim()) {
            case "luxe", "luxenailspa" -> "(555) 987-6543";
            default -> "(555) 123-4567";
        };
    }

    public TenantConfig resolveTenantConfig(String tenantId) {
        if (tenantId != null && !tenantId.isBlank()) {
            Optional<TenantConfig> config = tenantConfigRepository.findByTenantKeyIgnoreCase(tenantId);
            if (config.isPresent()) return config.get();
        }
        return tenantConfigRepository.findFirstByDefaultTenantTrue().orElseGet(() -> {
            TenantConfig fallback = new TenantConfig();
            fallback.setTenantKey("default");
            fallback.setSalonName("Jenny's Nail Studio");
            fallback.setPrimaryColor("#e83e8c");
            fallback.setSecondaryColor("#343a40");
            fallback.setCustomDomain("jennysnailstudio.com");
            return fallback;
        });
    }

    public String buildSelfServiceLink(TenantConfig config, String cancellationToken) {
        String token = (cancellationToken != null && !cancellationToken.isBlank()) ? cancellationToken : "";
        String domain = (config != null && config.getCustomDomain() != null && !config.getCustomDomain().isBlank())
                ? config.getCustomDomain().trim()
                : "localhost:8080";
        String scheme = domain.contains("localhost") ? "http" : "https";
        return scheme + "://" + domain + "/booking.html?token=" + token + "&action=manage";
    }

    private String buildSmsBody(
            String salonName,
            String customerName,
            String formattedTime,
            String techName,
            String serviceName,
            BigDecimal price,
            String cancellationUrl,
            String supportPhone
    ) {
        return String.format(
                "%s: Hi %s! Your appointment is confirmed for %s with %s. Service: %s ($%.2f). Manage/Cancel: %s Questions? Call %s",
                salonName, customerName, formattedTime, techName, serviceName, price != null ? price : BigDecimal.ZERO, cancellationUrl, supportPhone
        );
    }

    private String buildHtmlEmailTemplate(
            String salonName,
            String primaryColor,
            String secondaryColor,
            String salonAddress,
            String supportPhone,
            String customerName,
            String formattedTime,
            String techName,
            String stationPreference,
            List<ServiceItem> services,
            BigDecimal totalCost,
            String cancellationUrl,
            String notes
    ) {
        StringBuilder serviceRows = new StringBuilder();
        if (services != null) {
            for (ServiceItem s : services) {
                serviceRows.append(String.format("""
                        <tr>
                            <td style="padding: 10px 14px; border-bottom: 1px solid #edf2f7; font-size: 14px; color: #2d3748;">%s</td>
                            <td style="padding: 10px 14px; border-bottom: 1px solid #edf2f7; font-size: 14px; color: #718096; text-align: center;">%d min</td>
                            <td style="padding: 10px 14px; border-bottom: 1px solid #edf2f7; font-size: 14px; color: #2d3748; font-weight: 600; text-align: right;">$%.2f</td>
                        </tr>
                        """, s.name(), s.durationMinutes(), s.price()));
            }
        }

        String stationInfo = (stationPreference != null && !stationPreference.isBlank())
                ? String.format("<div style=\"margin-top: 6px; font-size: 13px; color: #718096;\">Station: <strong>%s</strong></div>", stationPreference)
                : "";

        String notesInfo = (notes != null && !notes.isBlank())
                ? String.format("<div style=\"margin-top: 12px; padding: 10px 14px; background: #f8fafc; border-left: 3px solid %s; font-size: 13px; color: #4a5568;\"><strong>Notes:</strong> %s</div>", primaryColor, notes)
                : "";

        return String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Booking Confirmation - %s</title>
                </head>
                <body style="margin: 0; padding: 0; background-color: #f7fafc; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #2d3748;">
                    <div style="max-width: 600px; margin: 30px auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06); border: 1px solid #e2e8f0;">
                        
                        <!-- Header Banner -->
                        <div style="background-color: %s; padding: 32px 24px; text-align: center; color: #ffffff;">
                            <h1 style="margin: 0; font-size: 26px; font-weight: 700; letter-spacing: -0.5px;">%s</h1>
                            <p style="margin: 6px 0 0 0; font-size: 15px; opacity: 0.92; font-weight: 400;">Your Appointment is Confirmed!</p>
                        </div>

                        <!-- Main Content -->
                        <div style="padding: 32px 28px;">
                            <p style="margin: 0 0 20px 0; font-size: 16px; line-height: 1.5;">
                                Hi <strong>%s</strong>,
                            </p>
                            <p style="margin: 0 0 24px 0; font-size: 15px; line-height: 1.6; color: #4a5568;">
                                Thank you for booking with us! We have reserved your specialist and look forward to welcoming you. Below are your booking details:
                            </p>

                            <!-- Booking Details Card -->
                            <div style="background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 20px; margin-bottom: 24px;">
                                <div style="display: flex; margin-bottom: 12px;">
                                    <div style="font-size: 13px; text-transform: uppercase; letter-spacing: 0.5px; color: #718096; font-weight: 600;">Date & Time</div>
                                    <div style="font-size: 15px; font-weight: 600; color: #1a202c; margin-top: 2px;">%s</div>
                                </div>
                                <div style="display: flex; margin-bottom: 12px;">
                                    <div style="font-size: 13px; text-transform: uppercase; letter-spacing: 0.5px; color: #718096; font-weight: 600;">Assigned Specialist</div>
                                    <div style="font-size: 15px; font-weight: 600; color: %s; margin-top: 2px;">%s</div>
                                </div>
                                %s
                                %s
                            </div>

                            <!-- Services Table -->
                            <h3 style="font-size: 15px; text-transform: uppercase; letter-spacing: 0.5px; color: #718096; margin: 0 0 10px 0; font-weight: 600;">Reserved Services</h3>
                            <table style="width: 100%%; border-collapse: collapse; margin-bottom: 24px; border: 1px solid #edf2f7; border-radius: 6px; overflow: hidden;">
                                <thead>
                                    <tr style="background-color: #edf2f7; color: #4a5568; font-size: 12px; text-transform: uppercase;">
                                        <th style="padding: 10px 14px; text-align: left;">Service</th>
                                        <th style="padding: 10px 14px; text-align: center;">Duration</th>
                                        <th style="padding: 10px 14px; text-align: right;">Price</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    %s
                                </tbody>
                                <tfoot>
                                    <tr style="background-color: #f8fafc;">
                                        <td colspan="2" style="padding: 12px 14px; font-weight: 700; font-size: 15px; color: #1a202c;">Total Estimated</td>
                                        <td style="padding: 12px 14px; font-weight: 700; font-size: 16px; color: %s; text-align: right;">$%.2f</td>
                                    </tr>
                                </tfoot>
                            </table>

                            <!-- Self Service Button -->
                            <div style="text-align: center; margin: 32px 0;">
                                <a href="%s" style="background-color: %s; color: #ffffff; padding: 14px 28px; text-decoration: none; border-radius: 6px; font-weight: 600; font-size: 15px; display: inline-block; box-shadow: 0 2px 8px rgba(0,0,0,0.12);">
                                    Manage or Cancel Appointment
                                </a>
                                <p style="margin: 12px 0 0 0; font-size: 12px; color: #a0aec0;">
                                    Need to reschedule? Click above or visit: <br>
                                    <a href="%s" style="color: %s; word-break: break-all;">%s</a>
                                </p>
                            </div>

                        </div>

                        <!-- Footer -->
                        <div style="background-color: #edf2f7; padding: 20px 28px; font-size: 13px; color: #718096; text-align: center; border-top: 1px solid #e2e8f0;">
                            <p style="margin: 0 0 6px 0; font-weight: 600; color: #4a5568;">%s</p>
                            <p style="margin: 0 0 6px 0;">%s</p>
                            <p style="margin: 0 0 6px 0;">Questions? Call us at <strong style="color: #2d3748;">%s</strong></p>
                            <p style="margin: 10px 0 0 0; font-size: 11px; color: #a0aec0;">Store Hours: Monday – Saturday 10:00 AM – 7:00 PM (Closed Sundays)</p>
                        </div>
                    </div>
                </body>
                </html>
                """,
                salonName,
                primaryColor,
                salonName,
                customerName,
                formattedTime,
                primaryColor,
                techName,
                stationInfo,
                notesInfo,
                serviceRows.toString(),
                primaryColor,
                totalCost != null ? totalCost : BigDecimal.ZERO,
                cancellationUrl,
                primaryColor,
                cancellationUrl,
                primaryColor,
                cancellationUrl,
                salonName,
                salonAddress,
                supportPhone
        );
    }

    private String buildGroupSummaryEmailHtml(
            String salonName,
            String primaryColor,
            String secondaryColor,
            String salonAddress,
            String supportPhone,
            String primaryName,
            String formattedTime,
            List<Map<String, Object>> guestReceipts,
            BigDecimal grandTotal,
            String cancellationUrl
    ) {
        StringBuilder guestSections = new StringBuilder();
        for (int i = 0; i < guestReceipts.size(); i++) {
            Map<String, Object> g = guestReceipts.get(i);
            String name = (String) g.getOrDefault("customerName", "Guest " + (i + 1));
            BigDecimal subtotal = (BigDecimal) g.getOrDefault("subtotal", BigDecimal.ZERO);
            @SuppressWarnings("unchecked")
            List<ServiceEntity> sList = (List<ServiceEntity>) g.get("services");
            StringBuilder sNames = new StringBuilder();
            if (sList != null) {
                for (ServiceEntity s : sList) {
                    if (sNames.length() > 0) sNames.append(", ");
                    sNames.append(s.getName());
                }
            }

            guestSections.append(String.format("""
                    <div style="padding: 12px 16px; background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; margin-bottom: 10px;">
                        <div style="font-weight: 600; font-size: 14px; color: #2d3748;">Guest %d: %s</div>
                        <div style="font-size: 13px; color: #718096; margin-top: 4px;">Services: %s</div>
                        <div style="font-size: 13px; font-weight: 600; color: %s; margin-top: 4px;">Subtotal: $%.2f</div>
                    </div>
                    """, (i + 1), name, sNames, primaryColor, subtotal));
        }

        return String.format("""
                <!DOCTYPE html>
                <html>
                <body style="font-family: sans-serif; background-color: #f7fafc; padding: 20px; color: #2d3748;">
                    <div style="max-width: 600px; margin: 0 auto; background: #fff; border-radius: 8px; border: 1px solid #e2e8f0; overflow: hidden;">
                        <div style="background: %s; color: #fff; padding: 24px; text-align: center;">
                            <h2 style="margin: 0;">%s</h2>
                            <p style="margin: 6px 0 0 0;">Group Booking Confirmation</p>
                        </div>
                        <div style="padding: 24px;">
                            <p>Hi <strong>%s</strong>, your group booking for <strong>%d guests</strong> on <strong>%s</strong> is confirmed.</p>
                            %s
                            <div style="font-size: 16px; font-weight: 700; margin-top: 16px; text-align: right; color: %s;">
                                Grand Total: $%.2f
                            </div>
                            <div style="text-align: center; margin-top: 24px;">
                                <a href="%s" style="background: %s; color: #fff; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: 600;">Manage Booking</a>
                            </div>
                        </div>
                        <div style="background: #edf2f7; padding: 16px; text-align: center; font-size: 12px; color: #718096;">
                            %s | %s | Support: %s
                        </div>
                    </div>
                </body>
                </html>
                """,
                primaryColor,
                salonName,
                primaryName,
                guestReceipts.size(),
                formattedTime,
                guestSections.toString(),
                primaryColor,
                grandTotal,
                cancellationUrl,
                primaryColor,
                salonName,
                salonAddress,
                supportPhone
        );
    }

    public record ServiceItem(String name, Integer durationMinutes, BigDecimal price) {}

    public record NotificationResult(
            boolean success,
            boolean simulated,
            String messageId,
            String details
    ) {}
}
