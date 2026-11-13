import React, { useState, useEffect, useMemo, useCallback } from 'react';
import './AdminDailyTimelineCalendar.css';

// Default store operating hours (10:00 AM to 7:00 PM)
const DEFAULT_OPEN_HOUR = 10; // 10:00 AM
const DEFAULT_CLOSE_HOUR = 19; // 7:00 PM (19:00)
const HOUR_ROW_HEIGHT_PX = 88; // 88px per hour (1.466px per minute)

/**
 * Helper to infer staff pronouns based on seeded database names
 */
function getStaffPronouns(tech) {
  if (!tech) return '';
  const raw = (tech.name || tech.username || '').trim().toLowerCase();
  const first = raw.split(/[\s@._]/)[0];
  const maleNames = [
    'tony', 'michael', 'david', 'john', 'alex', 'kevin',
    'brian', 'james', 'robert', 'daniel', 'chris', 'ryan'
  ];
  return maleNames.includes(first) ? 'he/him' : 'she/her';
}

/**
 * Formats a Date object as "Friday, November 13, 2026"
 */
function formatFullDayHeader(dateObj) {
  return dateObj.toLocaleDateString('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
  });
}

/**
 * Formats YYYY-MM-DD local date key for filtering appointments
 */
function toDateKey(dateObj) {
  const year = dateObj.getFullYear();
  const month = String(dateObj.getMonth() + 1).padStart(2, '0');
  const day = String(dateObj.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/**
 * Formats minutes-from-midnight (e.g. 615) into "10:15 AM"
 */
function formatMinutesToTimeLabel(totalMinutes) {
  const clamped = Math.max(0, Math.min(23 * 60 + 59, Math.floor(totalMinutes)));
  const hours24 = Math.floor(clamped / 60);
  const mins = clamped % 60;
  const period = hours24 >= 12 ? 'PM' : 'AM';
  const hours12 = hours24 % 12 === 0 ? 12 : hours24 % 12;
  return `${hours12}:${String(mins).padStart(2, '0')} ${period}`;
}

export default function AdminDailyTimelineCalendar({
  tenantKey = 'default',
  tenantConfig = null,
  authToken = null,
}) {
  // 1. Selected Day State (Focused exclusively on Single-Day view)
  const [selectedDate, setSelectedDate] = useState(() => new Date());

  // 2. Staff & Appointments State
  const [staffList, setStaffList] = useState([]);
  const [appointments, setAppointments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedAppointment, setSelectedAppointment] = useState(null);

  // 3. Real-Time Clock State (updates every 15 seconds for smooth progression)
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    // Immediate sync + interval timer to advance the horizontal timeline indicator
    setNow(new Date());
    const intervalId = setInterval(() => {
      setNow(new Date());
    }, 15000); // Updates every 15 seconds (0.25 min resolution)

    return () => clearInterval(intervalId);
  }, []);

  // Store hours derived from TenantConfig (or 10:00 AM - 7:00 PM default)
  const openHour = tenantConfig?.openHour ?? DEFAULT_OPEN_HOUR;
  const closeHour = tenantConfig?.closeHour ?? DEFAULT_CLOSE_HOUR;
  const totalStoreHours = closeHour - openHour;
  const totalStoreMinutes = totalStoreHours * 60;
  const totalGridHeightPx = totalStoreHours * HOUR_ROW_HEIGHT_PX;

  // Fetch active tenant staff and appointments
  const fetchScheduleData = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
    const token = authToken || localStorage.getItem('accessToken') || localStorage.getItem('token');
    const headers = {
      'X-Tenant-ID': tenantKey,
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    };

    try {
      const [staffRes, apptRes] = await Promise.all([
        fetch('/api/nail-techs', { headers }),
        fetch('/api/appointments', { headers }),
      ]);

      const staffData = staffRes.ok ? await staffRes.json() : [];
      const apptData = apptRes.ok ? await apptRes.json() : [];

      setStaffList(Array.isArray(staffData) ? staffData : []);
      setAppointments(Array.isArray(apptData) ? apptData : []);
    } catch (err) {
      console.error('Failed to load daily staff timeline data:', err);
    } finally {
      if (!silent) setLoading(false);
    }
  }, [tenantKey, authToken]);

  useEffect(() => {
    fetchScheduleData();
    // Background polling every 30 seconds for live updates
    const pollId = setInterval(() => {
      fetchScheduleData(true);
    }, 30000);
    return () => clearInterval(pollId);
  }, [fetchScheduleData]);

  // Day navigation handlers (Previous Day, Today, Next Day)
  const handleShiftDay = (deltaDays) => {
    setSelectedDate((prev) => {
      const next = new Date(prev);
      next.setDate(next.getDate() + deltaDays);
      return next;
    });
  };

  const handleJumpToToday = () => {
    setSelectedDate(new Date());
  };

  const handleDateInputChange = (e) => {
    if (!e.target.value) return;
    const [yyyy, mm, dd] = e.target.value.split('-').map(Number);
    if (yyyy && mm && dd) {
      setSelectedDate(new Date(yyyy, mm - 1, dd));
    }
  };

  // Filter appointments for the currently selected day
  const selectedDateKey = useMemo(() => toDateKey(selectedDate), [selectedDate]);
  const isSelectedDateToday = useMemo(
    () => toDateKey(now) === selectedDateKey,
    [now, selectedDateKey]
  );

  const dayAppointments = useMemo(() => {
    return appointments.filter((apt) => {
      if (!apt.appointmentTime) return false;
      const aptDatePart = String(apt.appointmentTime).split('T')[0];
      return aptDatePart === selectedDateKey;
    });
  }, [appointments, selectedDateKey]);

  // Build columns for every active staff member (+ Unassigned column if needed)
  const staffColumns = useMemo(() => {
    const cols = staffList.map((tech) => ({
      id: `tech-${tech.id}`,
      techId: tech.id,
      name: tech.name || tech.username,
      pronouns: getStaffPronouns(tech),
      isUnassigned: false,
    }));

    const hasUnassignedAppts = dayAppointments.some((apt) => !apt.nailTech || !apt.nailTech.id);
    if (hasUnassignedAppts || cols.length === 0) {
      cols.push({
        id: 'tech-unassigned',
        techId: null,
        name: 'Anyone Available',
        pronouns: 'Open Queue',
        isUnassigned: true,
      });
    }

    return cols;
  }, [staffList, dayAppointments]);

  // Generate hourly time axis ticks (e.g. 10:00 AM, 11:00 AM ... 7:00 PM)
  const hourAxisTicks = useMemo(() => {
    const ticks = [];
    for (let hr = openHour; hr <= closeHour; hr++) {
      ticks.push({
        hour24: hr,
        label: formatMinutesToTimeLabel(hr * 60),
        topPx: (hr - openHour) * HOUR_ROW_HEIGHT_PX,
      });
    }
    return ticks;
  }, [openHour, closeHour]);

  // Real-Time Indicator Position Calculation
  const liveTimeIndicator = useMemo(() => {
    const currentMinutes =
      now.getHours() * 60 + now.getMinutes() + now.getSeconds() / 60;
    const openMinutes = openHour * 60;
    const closeMinutes = closeHour * 60;

    const isWithinStoreHours =
      currentMinutes >= openMinutes && currentMinutes <= closeMinutes;
    const clampedMinutes = Math.max(openMinutes, Math.min(closeMinutes, currentMinutes));
    const elapsedMinutes = clampedMinutes - openMinutes;

    const topPercent = (elapsedMinutes / totalStoreMinutes) * 100;
    const topPx = (elapsedMinutes / 60) * HOUR_ROW_HEIGHT_PX;

    return {
      currentMinutes,
      isWithinStoreHours,
      topPercent,
      topPx,
      formattedTime: now.toLocaleTimeString('en-US', {
        hour: 'numeric',
        minute: '2-digit',
      }),
    };
  }, [now, openHour, closeHour, totalStoreMinutes]);

  // Calculate exact top & height for an appointment block
  const computeAppointmentBlockStyle = (apt) => {
    const dt = new Date(apt.appointmentTime);
    const startMinutes = dt.getHours() * 60 + dt.getMinutes();
    const durationMinutes = Number(apt.service?.durationMinutes || 45);
    const openMinutes = openHour * 60;

    const offsetMinutes = Math.max(0, startMinutes - openMinutes);
    const topPx = (offsetMinutes / 60) * HOUR_ROW_HEIGHT_PX;
    const heightPx = Math.max(36, (durationMinutes / 60) * HOUR_ROW_HEIGHT_PX);

    const endMinutes = startMinutes + durationMinutes;
    const timeRangeLabel = `${formatMinutesToTimeLabel(startMinutes)} – ${formatMinutesToTimeLabel(endMinutes)}`;

    return {
      top: `${topPx}px`,
      height: `${heightPx}px`,
      timeRangeLabel,
      durationMinutes,
    };
  };

  // Status update handler (Confirm / Cancel)
  const handleUpdateStatus = async (appointmentId, newStatus) => {
    const token = authToken || localStorage.getItem('accessToken') || localStorage.getItem('token');
    try {
      const res = await fetch(
        `/api/appointments/${appointmentId}/status?status=${encodeURIComponent(newStatus)}`,
        {
          method: 'PATCH',
          headers: {
            'X-Tenant-ID': tenantKey,
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
        }
      );
      if (res.ok) {
        setSelectedAppointment(null);
        fetchScheduleData();
      }
    } catch (err) {
      console.error('Error updating appointment status:', err);
    }
  };

  return (
    <div className="admin-timeline-wrapper card border-0 shadow-sm rounded-4 overflow-hidden bg-white">
      {/* ==================== 1. DAY & DATE HEADER (NO MULTI-VIEW TOGGLES) ==================== */}
      <div className="timeline-day-header d-flex flex-wrap justify-content-between align-items-center gap-3 p-4 border-bottom">
        <div className="d-flex align-items-center gap-3">
          <div className="calendar-date-badge d-flex flex-column align-items-center justify-content-center rounded-3">
            <span className="month-abbr">
              {selectedDate.toLocaleDateString('en-US', { month: 'short' }).toUpperCase()}
            </span>
            <span className="day-num">{selectedDate.getDate()}</span>
          </div>
          <div>
            <div className="d-flex align-items-center gap-2">
              <h3 className="fw-bold text-dark mb-0 fs-4">
                {formatFullDayHeader(selectedDate)}
              </h3>
              {isSelectedDateToday && (
                <span className="badge rounded-pill live-today-badge">Today • Live</span>
              )}
            </div>
            <p className="text-muted small mb-0 mt-1">
              Daily Staff Timeline • Store Hours: 10:00 AM – 7:00 PM ({dayAppointments.length}{' '}
              booking{dayAppointments.length === 1 ? '' : 's'})
            </p>
          </div>
        </div>

        {/* Quick Day Shift Controls (Prev / Today / Next + Date Picker) */}
        <div className="d-flex align-items-center gap-2 flex-wrap">
          <div className="btn-group shadow-sm rounded-3" role="group" aria-label="Day navigation">
            <button
              type="button"
              className="btn btn-outline-secondary btn-sm px-3 fw-semibold"
              onClick={() => handleShiftDay(-1)}
              title="Previous Day"
            >
              ‹ Prev Day
            </button>
            <button
              type="button"
              className={`btn btn-sm px-3 fw-semibold ${
                isSelectedDateToday ? 'btn-pink text-white' : 'btn-outline-secondary'
              }`}
              onClick={handleJumpToToday}
            >
              Today
            </button>
            <button
              type="button"
              className="btn btn-outline-secondary btn-sm px-3 fw-semibold"
              onClick={() => handleShiftDay(1)}
              title="Next Day"
            >
              Next Day ›
            </button>
          </div>

          <input
            type="date"
            className="form-control form-control-sm timeline-date-picker"
            value={selectedDateKey}
            onChange={handleDateInputChange}
            aria-label="Jump to date"
          />
        </div>
      </div>

      {/* Sunday Closed Alert */}
      {selectedDate.getDay() === 0 && (
        <div className="alert alert-warning border-0 rounded-0 m-0 px-4 py-2 small d-flex align-items-center gap-2">
          <span>⚠️</span>
          <span>Store is closed on Sundays. Operating hours: Monday – Saturday: 10:00 AM – 7:00 PM.</span>
        </div>
      )}

      {/* ==================== 2. STAFF COLUMN TIMELINE GRID ==================== */}
      {loading ? (
        <div className="text-center py-5">
          <div className="spinner-border text-pink" role="status" />
          <p className="text-muted mt-2 mb-0">Loading daily staff timeline...</p>
        </div>
      ) : (
        <div className="timeline-scroll-container">
          <div
            className="timeline-grid-inner"
            style={{
              minWidth: `${Math.max(780, 92 + staffColumns.length * 185)}px`,
            }}
          >
            {/* Sticky Top Row: Staff Member Column Headers */}
            <div className="timeline-staff-header-row">
              <div className="timeline-time-gutter-header">
                <span className="small fw-bold text-uppercase text-muted">Time</span>
              </div>

              {staffColumns.map((col) => {
                const staffApptCount = dayAppointments.filter((apt) =>
                  col.isUnassigned
                    ? !apt.nailTech || !apt.nailTech.id
                    : apt.nailTech && apt.nailTech.id === col.techId
                ).length;

                return (
                  <div key={col.id} className="timeline-staff-col-header">
                    <div className="d-flex align-items-center justify-content-center gap-2">
                      <div className="staff-header-avatar">
                        {col.name.charAt(0).toUpperCase()}
                      </div>
                      <div className="text-start overflow-hidden">
                        <div className="fw-bold text-dark text-truncate staff-header-name">
                          {col.name}
                        </div>
                        <div className="d-flex align-items-center gap-1">
                          <span className="text-muted extra-small">{col.pronouns}</span>
                          <span className="badge rounded-pill bg-light text-secondary border extra-small">
                            {staffApptCount}
                          </span>
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Main Timeline Body: Left Time Axis + Staff Columns + Live Indicator */}
            <div
              className="timeline-body-area"
              style={{ height: `${totalGridHeightPx}px` }}
            >
              {/* Left Vertical Time Axis */}
              <div className="timeline-time-axis">
                {hourAxisTicks.map((tick) => (
                  <div
                    key={tick.hour24}
                    className="timeline-axis-tick"
                    style={{ top: `${tick.topPx}px` }}
                  >
                    <span className="axis-time-text">{tick.label}</span>
                  </div>
                ))}
              </div>

              {/* Staff Columns Area */}
              <div className="timeline-columns-Canvas">
                {/* Horizontal Hour & Half-Hour Grid Lines */}
                {hourAxisTicks.slice(0, -1).map((tick) => (
                  <React.Fragment key={`grid-${tick.hour24}`}>
                    <div
                      className="timeline-hour-line"
                      style={{ top: `${tick.topPx}px` }}
                    />
                    <div
                      className="timeline-half-hour-line"
                      style={{ top: `${tick.topPx + HOUR_ROW_HEIGHT_PX / 2}px` }}
                    />
                  </React.Fragment>
                ))}
                {/* Closing hour line */}
                <div
                  className="timeline-hour-line"
                  style={{ top: `${totalGridHeightPx}px` }}
                />

                {/* ==================== 3. REAL-TIME DYNAMIC TIMELINE INDICATOR (ACTIVE ON TODAY) ==================== */}
                {isSelectedDateToday && (
                  <div
                    className={`timeline-live-indicator ${
                      !liveTimeIndicator.isWithinStoreHours ? 'outside-hours' : ''
                    }`}
                    style={{ top: `${liveTimeIndicator.topPx}px` }}
                    title={`Current Time: ${liveTimeIndicator.formattedTime}`}
                  >
                    <span className="live-indicator-time-pill">
                      <span className="live-pulse-dot" />
                      {liveTimeIndicator.formattedTime}
                    </span>
                    <div className="live-indicator-line" />
                  </div>
                )}

                {/* Individual Staff Member Columns */}
                {staffColumns.map((col) => {
                  const colAppts = dayAppointments.filter((apt) =>
                    col.isUnassigned
                      ? !apt.nailTech || !apt.nailTech.id
                      : apt.nailTech && apt.nailTech.id === col.techId
                  );

                  return (
                    <div key={col.id} className="timeline-staff-column">
                      {colAppts.map((apt) => {
                        const blockStyle = computeAppointmentBlockStyle(apt);
                        const statusKey = (apt.status || 'PENDING').toUpperCase();

                        return (
                          <div
                            key={apt.id}
                            className={`timeline-appt-block status-${statusKey.toLowerCase()}`}
                            style={{
                              top: blockStyle.top,
                              height: blockStyle.height,
                            }}
                            onClick={() => setSelectedAppointment(apt)}
                          >
                            <div className="d-flex justify-content-between align-items-start gap-1">
                              <span className="appt-time-badge">
                                {blockStyle.timeRangeLabel}
                              </span>
                              <span className="appt-status-dot" title={statusKey} />
                            </div>
                            <div className="appt-customer-name text-truncate">
                              {apt.customerName || 'Walk-In Guest'}
                            </div>
                            <div className="appt-service-name text-truncate">
                              {apt.service?.name || 'Nail Service'}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ==================== 4. APPOINTMENT DETAILS MODAL ==================== */}
      {selectedAppointment && (
        <div
          className="modal show d-block"
          tabIndex="-1"
          style={{ backgroundColor: 'rgba(15, 23, 42, 0.45)', zIndex: 1060 }}
          onClick={() => setSelectedAppointment(null)}
        >
          <div
            className="modal-dialog modal-dialog-centered"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-content border-0 shadow-lg rounded-4">
              <div className="modal-header border-bottom">
                <h5 className="modal-title fw-bold text-dark">
                  Booking Details #{selectedAppointment.id}
                </h5>
                <button
                  type="button"
                  className="btn-close"
                  onClick={() => setSelectedAppointment(null)}
                />
              </div>
              <div className="modal-body p-4">
                <p className="mb-2">
                  <strong>Customer:</strong> {selectedAppointment.customerName}
                </p>
                <p className="mb-2">
                  <strong>Phone:</strong> {selectedAppointment.customerPhone || 'N/A'}
                </p>
                <p className="mb-2">
                  <strong>Email:</strong> {selectedAppointment.customerEmail || 'N/A'}
                </p>
                <p className="mb-2">
                  <strong>Service:</strong> {selectedAppointment.service?.name || 'Service'} ($
                  {Number(selectedAppointment.service?.price || 0).toFixed(2)} •{' '}
                  {selectedAppointment.service?.durationMinutes || 45} mins)
                </p>
                <p className="mb-2">
                  <strong>Technician:</strong>{' '}
                  {selectedAppointment.nailTech?.name || 'Anyone Available'}
                </p>
                <p className="mb-2">
                  <strong>Time:</strong>{' '}
                  {new Date(selectedAppointment.appointmentTime).toLocaleString()}
                </p>
                {selectedAppointment.notes && (
                  <p className="mb-2">
                    <strong>Notes:</strong> {selectedAppointment.notes}
                  </p>
                )}
                <p className="mb-0">
                  <strong>Status:</strong>{' '}
                  <span className="badge bg-secondary">{selectedAppointment.status}</span>
                </p>
              </div>
              <div className="modal-footer d-flex justify-content-between">
                <div className="d-flex gap-2">
                  <button
                    type="button"
                    className="btn btn-sm btn-success fw-semibold"
                    onClick={() => handleUpdateStatus(selectedAppointment.id, 'CONFIRMED')}
                  >
                    Confirm
                  </button>
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-danger fw-semibold"
                    onClick={() => handleUpdateStatus(selectedAppointment.id, 'CANCELLED')}
                  >
                    Cancel Booking
                  </button>
                </div>
                <button
                  type="button"
                  className="btn btn-sm btn-secondary"
                  onClick={() => setSelectedAppointment(null)}
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
