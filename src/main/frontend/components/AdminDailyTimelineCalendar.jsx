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

/**
 * Side-by-Side Collision Stacking Algorithm
 * Groups overlapping appointments within a technician column into clusters,
 * assigns sub-columns, and calculates relative left/width offsets so
 * overlapping bookings render side-by-side without hiding one another.
 */
function computeStaffColumnLayout(colAppointments, openHour) {
  if (!colAppointments || colAppointments.length === 0) return [];

  // 1. Parse start and end minutes
  const parsed = colAppointments.map((apt) => {
    const dt = new Date(apt.appointmentTime);
    const startMinutes = dt.getHours() * 60 + dt.getMinutes();
    const durationMinutes = Number(apt.service?.durationMinutes || 45);
    const endMinutes = startMinutes + durationMinutes;
    return {
      apt,
      startMinutes,
      endMinutes,
      durationMinutes,
    };
  });

  // 2. Sort by startMinutes ascending, then longer duration first
  parsed.sort((a, b) => {
    if (a.startMinutes !== b.startMinutes) {
      return a.startMinutes - b.startMinutes;
    }
    return b.durationMinutes - a.durationMinutes;
  });

  // 3. Cluster mutually overlapping intervals
  const clusters = [];
  let currentCluster = [];
  let clusterEnd = -1;

  for (const item of parsed) {
    if (currentCluster.length === 0) {
      currentCluster.push(item);
      clusterEnd = item.endMinutes;
    } else if (item.startMinutes < clusterEnd) {
      currentCluster.push(item);
      clusterEnd = Math.max(clusterEnd, item.endMinutes);
    } else {
      clusters.push(currentCluster);
      currentCluster = [item];
      clusterEnd = item.endMinutes;
    }
  }
  if (currentCluster.length > 0) {
    clusters.push(currentCluster);
  }

  // 4. Assign sub-columns and calculate position styles
  const openMinutes = openHour * 60;
  const layoutItems = [];

  for (const cluster of clusters) {
    const subColumnEnds = [];

    for (const item of cluster) {
      let assignedCol = -1;
      for (let c = 0; c < subColumnEnds.length; c++) {
        if (subColumnEnds[c] <= item.startMinutes) {
          assignedCol = c;
          subColumnEnds[c] = item.endMinutes;
          break;
        }
      }
      if (assignedCol === -1) {
        assignedCol = subColumnEnds.length;
        subColumnEnds.push(item.endMinutes);
      }
      item.colIndex = assignedCol;
    }

    const totalCols = subColumnEnds.length;
    const isColliding = totalCols > 1;

    for (const item of cluster) {
      const offsetMinutes = Math.max(0, item.startMinutes - openMinutes);
      const topPx = (offsetMinutes / 60) * HOUR_ROW_HEIGHT_PX;
      const heightPx = Math.max(38, (item.durationMinutes / 60) * HOUR_ROW_HEIGHT_PX);
      const timeRangeLabel = `${formatMinutesToTimeLabel(item.startMinutes)} – ${formatMinutesToTimeLabel(item.endMinutes)}`;

      let leftStyle;
      let widthStyle;

      if (isColliding) {
        const colWidthPct = 100 / totalCols;
        leftStyle = `calc(${item.colIndex * colWidthPct}% + 3px)`;
        widthStyle = `calc(${colWidthPct}% - 6px)`;
      } else {
        leftStyle = '6px';
        widthStyle = 'calc(100% - 12px)';
      }

      layoutItems.push({
        apt: item.apt,
        topPx,
        heightPx,
        leftStyle,
        widthStyle,
        timeRangeLabel,
        isColliding,
        colIndex: item.colIndex,
        totalCols,
      });
    }
  }

  return layoutItems;
}

export default function AdminDailyTimelineCalendar({
  tenantKey = 'default',
  tenantConfig = null,
  authToken = null,
}) {
  // 1. Selected Day State (Focused exclusively on Single-Day view)
  const [selectedDate, setSelectedDate] = useState(() => new Date());

  // 2. Staff, Appointments & Services State
  const [staffList, setStaffList] = useState([]);
  const [appointments, setAppointments] = useState([]);
  const [servicesCatalog, setServicesCatalog] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedAppointment, setSelectedAppointment] = useState(null);
  const [statusReason, setStatusReason] = useState('');

  // 3. Quick Walk-In / Manual Booking Modal State
  const [walkInModal, setWalkInModal] = useState({
    isOpen: false,
    techId: null,
    techName: '',
    isUnassigned: false,
    date: '',
    time: '10:00',
    customerName: '',
    customerPhone: '',
    customerEmail: '',
    selectedServiceIds: [],
    notes: '',
    saving: false,
    error: null,
  });

  // 4. Real-Time Clock State (updates every 15 seconds for smooth progression)
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    setNow(new Date());
    const intervalId = setInterval(() => {
      setNow(new Date());
    }, 15000); // 15s refresh for live indicator
    return () => clearInterval(intervalId);
  }, []);

  // Store hours derived from TenantConfig (or 10:00 AM - 7:00 PM default)
  const openHour = tenantConfig?.openHour ?? DEFAULT_OPEN_HOUR;
  const closeHour = tenantConfig?.closeHour ?? DEFAULT_CLOSE_HOUR;
  const totalStoreHours = closeHour - openHour;
  const totalStoreMinutes = totalStoreHours * 60;
  const totalGridHeightPx = totalStoreHours * HOUR_ROW_HEIGHT_PX;

  // Fetch active tenant staff, appointments, and service catalog
  const fetchScheduleData = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
    const token = authToken || localStorage.getItem('accessToken') || localStorage.getItem('token');
    const headers = {
      'X-Tenant-ID': tenantKey,
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    };

    try {
      const [staffRes, apptRes, srvRes] = await Promise.all([
        fetch('/api/nail-techs', { headers }),
        fetch('/api/appointments', { headers }),
        fetch('/api/services', { headers }),
      ]);

      const staffData = staffRes.ok ? await staffRes.json() : [];
      const apptData = apptRes.ok ? await apptRes.json() : [];
      const srvData = srvRes.ok ? await srvRes.json() : [];

      setStaffList(Array.isArray(staffData) ? staffData : []);
      setAppointments(Array.isArray(apptData) ? apptData : []);
      setServicesCatalog(Array.isArray(srvData) ? srvData : []);
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

  // Day navigation handlers
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

  // Generate hourly time axis ticks
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

    const topPx = (elapsedMinutes / 60) * HOUR_ROW_HEIGHT_PX;

    return {
      isWithinStoreHours,
      topPx,
      formattedTime: now.toLocaleTimeString('en-US', {
        hour: 'numeric',
        minute: '2-digit',
      }),
    };
  }, [now, openHour, closeHour]);

  // Status update handler (Confirm / Cancel / No-Show with optional audit reason)
  const handleUpdateStatus = async (appointmentId, newStatus, reason = '') => {
    const token = authToken || localStorage.getItem('accessToken') || localStorage.getItem('token');
    const url = `/api/appointments/${appointmentId}/status?status=${encodeURIComponent(newStatus)}${
      reason ? `&reason=${encodeURIComponent(reason)}` : ''
    }`;
    try {
      const res = await fetch(url, {
        method: 'PATCH',
        headers: {
          'X-Tenant-ID': tenantKey,
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
      });
      if (res.ok) {
        setSelectedAppointment(null);
        setStatusReason('');
        fetchScheduleData(true);
      }
    } catch (err) {
      console.error('Error updating appointment status:', err);
    }
  };

  // Click handler on empty column slot to open Quick Walk-In Modal
  const handleColumnClick = (e, col) => {
    if (e.target.closest('.timeline-appt-block')) {
      return; // Clicking an appointment block is handled separately
    }
    const rect = e.currentTarget.getBoundingClientRect();
    const clickY = e.clientY - rect.top;
    const elapsedMins = (clickY / HOUR_ROW_HEIGHT_PX) * 60;
    // Round to nearest 15-minute slot interval
    const roundedMins = Math.round(elapsedMins / 15) * 15;
    const targetTotalMins = openHour * 60 + roundedMins;
    const clampedTotalMins = Math.max(openHour * 60, Math.min(closeHour * 60 - 15, targetTotalMins));

    const hours24 = Math.floor(clampedTotalMins / 60);
    const mins = clampedTotalMins % 60;
    const timeStr = `${String(hours24).padStart(2, '0')}:${String(mins).padStart(2, '0')}`;

    setWalkInModal({
      isOpen: true,
      techId: col.isUnassigned ? null : col.techId,
      techName: col.name,
      isUnassigned: col.isUnassigned,
      date: selectedDateKey,
      time: timeStr,
      customerName: '',
      customerPhone: '',
      customerEmail: '',
      selectedServiceIds: servicesCatalog.length > 0 ? [servicesCatalog[0].id] : [],
      notes: '',
      saving: false,
      error: null,
    });
  };

  // Quick-fill Walk-In Guest shortcut button
  const handleQuickFillWalkIn = () => {
    setWalkInModal((prev) => ({
      ...prev,
      customerName: 'Walk-In Guest',
      customerPhone: '(555) 000-0000',
      customerEmail: 'walkin@salon.com',
      notes: 'Walk-in guest (Front desk entry)',
    }));
  };

  // Toggle service selection in Walk-In Modal
  const handleToggleService = (srvId) => {
    setWalkInModal((prev) => {
      const exists = prev.selectedServiceIds.includes(srvId);
      const nextIds = exists
        ? prev.selectedServiceIds.filter((id) => id !== srvId)
        : [...prev.selectedServiceIds, srvId];
      return { ...prev, selectedServiceIds: nextIds };
    });
  };

  // Selected services summary in Walk-In Modal
  const walkInSelectedServices = useMemo(() => {
    return servicesCatalog.filter((s) => walkInModal.selectedServiceIds.includes(s.id));
  }, [servicesCatalog, walkInModal.selectedServiceIds]);

  const walkInTotalCost = useMemo(() => {
    return walkInSelectedServices.reduce((sum, s) => sum + Number(s.price || 0), 0);
  }, [walkInSelectedServices]);

  const walkInTotalDuration = useMemo(() => {
    return walkInSelectedServices.reduce((sum, s) => sum + Number(s.durationMinutes || 45), 0);
  }, [walkInSelectedServices]);

  // Submit Quick Walk-In Booking
  const handleSubmitWalkIn = async (e) => {
    if (e) e.preventDefault();
    if (!walkInModal.selectedServiceIds || walkInModal.selectedServiceIds.length === 0) {
      setWalkInModal((prev) => ({ ...prev, error: 'Please select at least one service.' }));
      return;
    }

    setWalkInModal((prev) => ({ ...prev, saving: true, error: null }));

    const custName = walkInModal.customerName.trim() || 'Walk-In Guest';
    const custPhone = walkInModal.customerPhone.trim() || '(555) 000-0000';
    const custEmail = walkInModal.customerEmail.trim() || 'walkin@salon.com';
    const appointmentTimeStr = `${walkInModal.date}T${walkInModal.time}:00`;

    const payload = {
      appointmentTime: appointmentTimeStr,
      customerName: custName,
      customerPhone: custPhone,
      customerEmail: custEmail,
      notes: walkInModal.notes ? walkInModal.notes.trim() : 'Walk-in manual booking',
      nailTech: walkInModal.techId ? { id: Number(walkInModal.techId) } : null,
      serviceIds: walkInModal.selectedServiceIds.map(Number),
      manualOverride: true,
    };

    const token = authToken || localStorage.getItem('accessToken') || localStorage.getItem('token');
    const headers = {
      'Content-Type': 'application/json',
      'X-Tenant-ID': tenantKey,
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    };

    try {
      const res = await fetch('/api/appointments', {
        method: 'POST',
        headers,
        body: JSON.stringify(payload),
      });

      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        throw new Error(errData.message || 'Failed to create walk-in appointment.');
      }

      // Close modal and silently refresh timeline without full-page reload
      setWalkInModal((prev) => ({ ...prev, isOpen: false, saving: false }));
      await fetchScheduleData(true);
    } catch (err) {
      console.error('Walk-in booking error:', err);
      setWalkInModal((prev) => ({
        ...prev,
        saving: false,
        error: err.message || 'Error creating walk-in booking.',
      }));
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
              booking{dayAppointments.length === 1 ? '' : 's'}) • Click empty slots to add Walk-In
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

              {/* Staff Columns Canvas */}
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

                {/* Real-Time Dynamic Timeline Indicator (Active on Today) */}
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

                {/* Individual Staff Member Columns with Side-by-Side Collision Positioning */}
                {staffColumns.map((col) => {
                  const colAppts = dayAppointments.filter((apt) =>
                    col.isUnassigned
                      ? !apt.nailTech || !apt.nailTech.id
                      : apt.nailTech && apt.nailTech.id === col.techId
                  );

                  // Compute layout with side-by-side collision handling
                  const layoutBlocks = computeStaffColumnLayout(colAppts, openHour);

                  return (
                    <div
                      key={col.id}
                      className="timeline-staff-column"
                      onClick={(e) => handleColumnClick(e, col)}
                      title={`Click empty slot on ${col.name}'s column to book walk-in`}
                    >
                      {layoutBlocks.map((item) => {
                        const { apt, topPx, heightPx, leftStyle, widthStyle, timeRangeLabel, isColliding } = item;
                        const statusKey = (apt.status || 'PENDING').toUpperCase();

                        return (
                          <div
                            key={apt.id}
                            className={`timeline-appt-block status-${statusKey.toLowerCase()} ${
                              isColliding ? 'is-colliding' : ''
                            }`}
                            style={{
                              top: `${topPx}px`,
                              height: `${heightPx}px`,
                              left: leftStyle,
                              width: widthStyle,
                            }}
                            onClick={(e) => {
                              e.stopPropagation();
                              setSelectedAppointment(apt);
                            }}
                            title={`${apt.customerName || 'Walk-In'} - ${apt.service?.name || 'Service'} (${timeRangeLabel})`}
                          >
                            <div className="d-flex justify-content-between align-items-start gap-1">
                              <span className="appt-time-badge">{timeRangeLabel}</span>
                              <div className="d-flex align-items-center gap-1">
                                {isColliding && (
                                  <span className="collision-badge" title="Side-by-side overlapping appointment">
                                    Overlap
                                  </span>
                                )}
                                <span className="appt-status-dot" title={statusKey} />
                              </div>
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

      {/* ==================== 3. DIRECT WALK-IN / MANUAL BOOKING MODAL ==================== */}
      {walkInModal.isOpen && (
        <div
          className="modal show d-block"
          tabIndex="-1"
          style={{ backgroundColor: 'rgba(15, 23, 42, 0.5)', zIndex: 1070 }}
          onClick={() => setWalkInModal((prev) => ({ ...prev, isOpen: false }))}
        >
          <div
            className="modal-dialog modal-dialog-centered modal-lg"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-content border-0 shadow-lg rounded-4 overflow-hidden">
              <div className="modal-header bg-light border-bottom py-3 px-4">
                <div>
                  <h5 className="modal-title fw-bold text-dark mb-0">
                    ⚡ Quick Walk-In / Manual Booking
                  </h5>
                  <p className="text-muted small mb-0 mt-1">
                    Book directly for <strong>{walkInModal.techName}</strong> • {walkInModal.date} at {walkInModal.time}
                  </p>
                </div>
                <button
                  type="button"
                  className="btn-close"
                  onClick={() => setWalkInModal((prev) => ({ ...prev, isOpen: false }))}
                />
              </div>

              <div className="modal-body p-4">
                {walkInModal.error && (
                  <div className="alert alert-danger py-2 px-3 small rounded-3 mb-3">
                    {walkInModal.error}
                  </div>
                )}

                {/* Quick Fill Shortcut */}
                <div className="d-flex justify-content-between align-items-center mb-3 p-2 rounded-3 bg-light border">
                  <span className="small text-muted fw-semibold">
                    Need to seat an unannounced walk-in immediately?
                  </span>
                  <button
                    type="button"
                    className="btn btn-outline-pink btn-sm fw-semibold"
                    onClick={handleQuickFillWalkIn}
                  >
                    ⚡ Quick Fill: Walk-In Guest
                  </button>
                </div>

                <div className="row g-3">
                  {/* Assigned Technician */}
                  <div className="col-md-6">
                    <label className="form-label small fw-bold text-secondary">
                      Assigned Technician
                    </label>
                    <select
                      className="form-select form-select-sm"
                      value={walkInModal.techId ?? ''}
                      onChange={(e) => {
                        const val = e.target.value ? Number(e.target.value) : null;
                        const match = staffList.find((t) => t.id === val);
                        setWalkInModal((prev) => ({
                          ...prev,
                          techId: val,
                          techName: match ? (match.name || match.username) : 'Anyone Available',
                        }));
                      }}
                    >
                      <option value="">Anyone Available (Open Queue)</option>
                      {staffList.map((t) => (
                        <option key={t.id} value={t.id}>
                          {t.name || t.username} ({getStaffPronouns(t)})
                        </option>
                      ))}
                    </select>
                  </div>

                  {/* Date & Start Time */}
                  <div className="col-md-3">
                    <label className="form-label small fw-bold text-secondary">Date</label>
                    <input
                      type="date"
                      className="form-control form-control-sm"
                      value={walkInModal.date}
                      onChange={(e) =>
                        setWalkInModal((prev) => ({ ...prev, date: e.target.value }))
                      }
                    />
                  </div>
                  <div className="col-md-3">
                    <label className="form-label small fw-bold text-secondary">Start Time</label>
                    <input
                      type="time"
                      className="form-control form-control-sm"
                      value={walkInModal.time}
                      onChange={(e) =>
                        setWalkInModal((prev) => ({ ...prev, time: e.target.value }))
                      }
                    />
                  </div>

                  {/* Customer Information */}
                  <div className="col-md-4">
                    <label className="form-label small fw-bold text-secondary">
                      Customer Name <span className="text-danger">*</span>
                    </label>
                    <input
                      type="text"
                      className="form-control form-control-sm"
                      placeholder="e.g. Walk-In Guest or Jane Doe"
                      value={walkInModal.customerName}
                      onChange={(e) =>
                        setWalkInModal((prev) => ({ ...prev, customerName: e.target.value }))
                      }
                    />
                  </div>
                  <div className="col-md-4">
                    <label className="form-label small fw-bold text-secondary">Phone Number</label>
                    <input
                      type="tel"
                      className="form-control form-control-sm"
                      placeholder="(555) 000-0000"
                      value={walkInModal.customerPhone}
                      onChange={(e) =>
                        setWalkInModal((prev) => ({ ...prev, customerPhone: e.target.value }))
                      }
                    />
                  </div>
                  <div className="col-md-4">
                    <label className="form-label small fw-bold text-secondary">Email Address</label>
                    <input
                      type="email"
                      className="form-control form-control-sm"
                      placeholder="walkin@salon.com"
                      value={walkInModal.customerEmail}
                      onChange={(e) =>
                        setWalkInModal((prev) => ({ ...prev, customerEmail: e.target.value }))
                      }
                    />
                  </div>

                  {/* Service Selection Catalog */}
                  <div className="col-12">
                    <div className="d-flex justify-content-between align-items-center mb-2">
                      <label className="form-label small fw-bold text-secondary mb-0">
                        Select Service(s) <span className="text-danger">*</span>
                      </label>
                      <span className="badge bg-light text-dark border">
                        {walkInSelectedServices.length} Selected • ${walkInTotalCost.toFixed(2)} • {walkInTotalDuration} mins
                      </span>
                    </div>

                    <div className="walkin-service-catalog border rounded-3 p-2 bg-light">
                      {servicesCatalog.length === 0 ? (
                        <p className="text-muted small mb-0 p-2">Loading service catalog...</p>
                      ) : (
                        <div className="row g-2">
                          {servicesCatalog.map((srv) => {
                            const isChecked = walkInModal.selectedServiceIds.includes(srv.id);
                            return (
                              <div key={srv.id} className="col-md-6 col-lg-4">
                                <div
                                  className={`service-select-card p-2 rounded-2 border d-flex align-items-center gap-2 ${
                                    isChecked ? 'selected bg-white border-pink shadow-sm' : 'bg-white'
                                  }`}
                                  onClick={() => handleToggleService(srv.id)}
                                  style={{ cursor: 'pointer' }}
                                >
                                  <input
                                    type="checkbox"
                                    className="form-check-input mt-0"
                                    checked={isChecked}
                                    onChange={() => handleToggleService(srv.id)}
                                    onClick={(e) => e.stopPropagation()}
                                  />
                                  <div className="overflow-hidden flex-grow-1">
                                    <div className="fw-semibold small text-truncate text-dark">
                                      {srv.name}
                                    </div>
                                    <div className="text-muted extra-small">
                                      ${Number(srv.price || 0).toFixed(2)} • {srv.durationMinutes || 45} mins
                                    </div>
                                  </div>
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Notes / Special Instructions */}
                  <div className="col-12">
                    <label className="form-label small fw-bold text-secondary">
                      Notes / Station Request
                    </label>
                    <input
                      type="text"
                      className="form-control form-control-sm"
                      placeholder="e.g. Station 2, prefers quiet technician, color request"
                      value={walkInModal.notes}
                      onChange={(e) =>
                        setWalkInModal((prev) => ({ ...prev, notes: e.target.value }))
                      }
                    />
                  </div>
                </div>
              </div>

              <div className="modal-footer bg-light border-top d-flex justify-content-between">
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary px-3"
                  onClick={() => setWalkInModal((prev) => ({ ...prev, isOpen: false }))}
                  disabled={walkInModal.saving}
                >
                  Cancel
                </button>

                <button
                  type="button"
                  className="btn btn-sm btn-pink text-white fw-semibold px-4"
                  onClick={handleSubmitWalkIn}
                  disabled={walkInModal.saving}
                >
                  {walkInModal.saving ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-1" role="status" />
                      Saving Walk-In...
                    </>
                  ) : (
                    '⚡ Confirm & Save Booking'
                  )}
                </button>
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
                <p className="mb-3">
                  <strong>Status:</strong>{' '}
                  <span className="badge bg-secondary">{selectedAppointment.status}</span>
                </p>

                {/* Optional Reason for Cancellation or No-Show */}
                <div className="p-3 bg-light rounded-3 border">
                  <label className="form-label small fw-bold text-secondary mb-1">
                    Cancellation / No-Show Reason (Audit Log Note):
                  </label>
                  <input
                    type="text"
                    className="form-control form-control-sm"
                    placeholder="e.g. Client unwell, Late cancellation, No call no show"
                    value={statusReason}
                    onChange={(e) => setStatusReason(e.target.value)}
                  />
                  <div className="d-flex gap-1 mt-2">
                    {['Late Cancel', 'Sick / Emergency', 'No Call / No Show'].map((preset) => (
                      <button
                        key={preset}
                        type="button"
                        className="btn btn-outline-secondary btn-sm py-0 px-2"
                        style={{ fontSize: '0.72rem' }}
                        onClick={() => setStatusReason(preset)}
                      >
                        {preset}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
              <div className="modal-footer d-flex justify-content-between">
                <div className="d-flex gap-2 flex-wrap">
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
                    onClick={() => handleUpdateStatus(selectedAppointment.id, 'CANCELLED', statusReason)}
                  >
                    Cancel Booking
                  </button>
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-warning text-dark fw-semibold"
                    onClick={() => handleUpdateStatus(selectedAppointment.id, 'NO_SHOW', statusReason)}
                  >
                    Mark No-Show
                  </button>
                </div>
                <button
                  type="button"
                  className="btn btn-sm btn-secondary"
                  onClick={() => {
                    setSelectedAppointment(null);
                    setStatusReason('');
                  }}
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
