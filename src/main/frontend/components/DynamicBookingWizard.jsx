import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useTenant } from '../context/TenantContext';

const STEP_METADATA = {
  SERVICE: { title: 'Select Services', subtitle: 'Choose one or more treatments from the full menu' },
  STAFF: { title: 'Select Staff', subtitle: 'Choose an available technician for your visit' },
  STATION: { title: 'Station / Room', subtitle: 'Select your preferred seating or private suite' },
  DETAILS: { title: 'Details & Confirm', subtitle: 'Enter guest contact details and preferred time' },
};

const SERVICE_CATEGORIES = [
  { id: 'manicures', title: 'Manicures', keywords: ['manicure'] },
  { id: 'pedicures', title: 'Pedicures', keywords: ['pedicure'] },
  { id: 'combos', title: 'Combo', keywords: ['combo'] },
  { id: 'kids', title: 'Kids', keywords: ['kid', 'kids'] },
  {
    id: 'nail-enhancement',
    title: 'Nail Enhancement & Art',
    keywords: [
      'bio sculpture',
      'gel powder',
      'dipping powder',
      'nail art',
      'french tip',
      'ombre',
      'chrome',
      'cuticle',
      'paraffin',
      'take off',
      'polish change',
      'nail cut',
      'nail repair',
      'shellac',
    ],
  },
  { id: 'body-massage', title: 'Body Massage', keywords: ['massage', 'hot stone'] },
  { id: 'waxing', title: 'Waxing', keywords: ['wax', 'waxing'] },
  { id: 'facial', title: 'Facial Treatments (Babor®)', keywords: ['facial', 'babor', 'collagen'] },
  { id: 'specialty-services', title: 'Specialty Services', keywords: ['tinting', 'tint'] },
  { id: 'gift-packages', title: 'Gift Packages', keywords: ['rejuvenation', 'executive', 'package'] },
];

function formatMinutesTo12HrLabel(totalMinutes) {
  const clamped = ((totalMinutes % 1440) + 1440) % 1440;
  const hour24 = Math.floor(clamped / 60);
  const min = clamped % 60;
  const period = hour24 >= 12 ? 'PM' : 'AM';
  const hour12 = hour24 > 12 ? hour24 - 12 : hour24 === 0 ? 12 : hour24;
  return `${hour12}:${String(min).padStart(2, '0')} ${period}`;
}

function formatTime24ToRangeLabel(value24, durationMinutes = 45) {
  if (!value24) return '';
  const parts = String(value24).split(':');
  const h = parseInt(parts[0], 10);
  const m = parseInt(parts[1] || '0', 10);
  if (isNaN(h) || isNaN(m)) return value24;
  const startMins = h * 60 + m;
  const endMins = startMins + (Number(durationMinutes) || 45);
  return `${formatMinutesTo12HrLabel(startMins)} – ${formatMinutesTo12HrLabel(endMins)}`;
}

function getLocalTodayDateStr() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

function getLocalNowIsoStr() {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  const hh = String(now.getHours()).padStart(2, '0');
  const mm = String(now.getMinutes()).padStart(2, '0');
  const ss = String(now.getSeconds()).padStart(2, '0');
  return `${y}-${m}-${d}T${hh}:${mm}:${ss}`;
}

function buildStoreTimeSlots(durationMinutes = 45) {
  const slots = [];
  for (let hour = 10; hour <= 19; hour++) {
    const maxMinute = hour === 19 ? 0 : 45;
    for (let min = 0; min <= maxMinute; min += 15) {
      const hh = String(hour).padStart(2, '0');
      const mm = String(min).padStart(2, '0');
      const value = `${hh}:${mm}:00`;
      const period = hour >= 12 ? 'PM' : 'AM';
      const hour12 = hour > 12 ? hour - 12 : hour === 0 ? 12 : hour;
      const label = `${hour12}:${mm} ${period}`;
      slots.push({
        value,
        label,
        timeRangeLabel: formatTime24ToRangeLabel(value, durationMinutes),
      });
    }
  }
  return slots;
}

function getCategoryForService(srv) {
  if (!srv || !srv.name) return 'manicures';
  const nameLower = srv.name.toLowerCase();
  if (nameLower.includes('combo')) return 'combos';
  if (nameLower.includes('kid') || nameLower.includes('kids')) return 'kids';
  for (const cat of SERVICE_CATEGORIES) {
    if (cat.id === 'combos' || cat.id === 'kids') continue;
    if (cat.keywords && cat.keywords.some((kw) => nameLower.includes(kw))) {
      return cat.id;
    }
  }
  return 'manicures';
}

function mapToBackendCategory(catId) {
  switch (catId) {
    case 'pedicures':
    case 'combos':
      return 'PEDICURE';
    case 'kids':
      return 'KID_SERVICES';
    case 'body-massage':
      return 'BODY_MASSAGE';
    case 'waxing':
      return 'WAXING';
    case 'facial':
    case 'specialty-services':
      return 'FACIAL';
    case 'nail-enhancement':
      return 'NAIL_ART';
    case 'gift-packages':
      return 'ALL';
    case 'manicures':
    default:
      return 'MANICURE';
  }
}

function isTechQualifiedForBackendCategory(tech, category, srv = null) {
  if (!tech) return false;
  const targetCat = String(category || '').trim().toUpperCase();
  if (targetCat === 'PEDICURE' || targetCat === 'KID_SERVICES' || targetCat === 'ALL') {
    return true;
  }
  const caps = Array.isArray(tech.capabilities)
    ? tech.capabilities.map((s) => String(s).trim().toUpperCase())
    : [];
  const specTags = tech.specialty
    ? String(tech.specialty).split(',').map((s) => s.trim().toUpperCase())
    : [];
  const tags = [...new Set([...caps, ...specTags])].filter(Boolean);
  if (tags.length === 0) return false;
  if (tags.includes('ALL') || tags.includes('FULL EVERYTHING') || tags.includes('GENERAL')) {
    return true;
  }
  if (srv && srv.category) {
    const explicitCat = String(srv.category).trim().toUpperCase();
    if (tags.includes(explicitCat)) return true;
  }
  return tags.includes(targetCat);
}


function getStaffPronouns(tech) {
  if (!tech) return 'she/her';
  const raw = (tech.name || tech.username || '').trim().toLowerCase();
  const first = raw.split(/[\s@._]/)[0];
  const maleNames = ['tony', 'michael', 'david', 'john', 'alex', 'kevin', 'brian', 'james', 'robert', 'daniel', 'chris', 'ryan'];
  if (maleNames.includes(first)) {
    return 'he/him';
  }
  return 'she/her';
}

function createEmptyGuest(guestNumber) {
  return {
    guestNumber,
    services: [],
    selectedStaff: null,
    staffMode: 'anyone', // 'anyone' | 'per-service' | 'master'
    serviceStaffAssignments: {},
    customerName: '',
    customerPhone: '',
    customerEmail: '',
    notes: '',
  };
}

function calculateGuestTotals(guest) {
  const list = guest?.services || [];
  const subtotal = list.reduce((sum, srv) => sum + Number(srv.price || 0), 0);
  const durationMinutes = list.reduce((sum, srv) => sum + Number(srv.durationMinutes || 45), 0);
  return { subtotal, durationMinutes, count: list.length };
}

export default function DynamicBookingWizard({ onRegisterResetHandler }) {
  const { tenant, preselectedServiceId, startBlankBooking } = useTenant();
  const { wizard, layoutVariant, salonName } = tenant;

  const activeSteps = useMemo(() => {
    const rawOrder = Array.isArray(wizard?.stepOrder)
      ? wizard.stepOrder
      : ['SERVICE', 'STAFF', 'DETAILS'];
    return rawOrder.filter((step) => step !== 'STATION' || wizard?.enableStationSelection);
  }, [wizard]);

  const [currentStepIdx, setCurrentStepIdx] = useState(0);
  const [services, setServices] = useState([]);
  const [openCategoryId, setOpenCategoryId] = useState(null);
  const [staffList, setStaffList] = useState([]);
  const [loadingStaff, setLoadingStaff] = useState(false);

  // Multi-guest & multi-service state
  const [guests, setGuests] = useState(() => [createEmptyGuest(1)]);
  const [activeGuestIndex, setActiveGuestIndex] = useState(0); // 0 = Guest 1, 1 = Guest 2
  const [showSoloOrGroupPrompt, setShowSoloOrGroupPrompt] = useState(false);
  const [staffGuestIndex, setStaffGuestIndex] = useState(0); // Sequential guest staff step index
  const [detailsGuestIndex, setDetailsGuestIndex] = useState(0); // Sequential guest details step index

  const [selectedStaff, setSelectedStaff] = useState(null);
  const [selectedStation, setSelectedStation] = useState('Any Available Station');
  
  // Date & Availability state
  const getInitialBookingDate = () => {
    const d = new Date();
    // If today is Sunday, skip to Monday
    if (d.getDay() === 0) {
      d.setDate(d.getDate() + 1);
    }
    return d.toISOString().split('T')[0];
  };

  const [appointmentSchedule, setAppointmentSchedule] = useState(() => ({
    appointmentDate: getInitialBookingDate(),
    appointmentTime: '',
  }));

  const [availableSlots, setAvailableSlots] = useState([]);
  const [loadingSlots, setLoadingSlots] = useState(false);
  const [isDateClosed, setIsDateClosed] = useState(false);
  const [closedReason, setClosedReason] = useState('');
  const [hideUnavailableSlots, setHideUnavailableSlots] = useState(false);

  const [alertMessage, setAlertMessage] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [confirmedBooking, setConfirmedBooking] = useState(null);

  const currentStepKey = activeSteps[currentStepIdx] || 'SERVICE';

  const guest1Totals = useMemo(() => calculateGuestTotals(guests[0]), [guests]);
  const guest2Totals = useMemo(
    () => (guests.length > 1 ? calculateGuestTotals(guests[1]) : { subtotal: 0, durationMinutes: 0, count: 0 }),
    [guests]
  );
  const combinedTotals = useMemo(
    () => ({
      grandTotal: guest1Totals.subtotal + guest2Totals.subtotal,
      totalDurationMinutes: guest1Totals.durationMinutes + guest2Totals.durationMinutes,
      totalServicesCount: guest1Totals.count + guest2Totals.count,
    }),
    [guest1Totals, guest2Totals]
  );

  // Complete reset to clean/blank wizard session
  const resetToBlankWizard = useCallback(() => {
    setGuests([createEmptyGuest(1)]);
    setActiveGuestIndex(0);
    setShowSoloOrGroupPrompt(false);
    setStaffGuestIndex(0);
    setDetailsGuestIndex(0);
    setSelectedStaff(null);
    setSelectedStation('Any Available Station');
    setAppointmentSchedule({
      appointmentDate: getInitialBookingDate(),
      appointmentTime: '',
    });
    setAlertMessage(null);
    setConfirmedBooking(null);
    setCurrentStepIdx(0);
    startBlankBooking();
  }, [startBlankBooking]);

  useEffect(() => {
    if (onRegisterResetHandler) {
      onRegisterResetHandler(resetToBlankWizard);
    }
  }, [onRegisterResetHandler, resetToBlankWizard]);

  // Fetch FULL service catalog scoped to active tenant
  useEffect(() => {
    fetch('/api/services', {
      headers: { 'X-Tenant-ID': tenant.tenantKey },
    })
      .then((res) => (res.ok ? res.json() : []))
      .then((data) => {
        const list = Array.isArray(data) ? data : [];
        setServices(list);

        if (preselectedServiceId) {
          const matched = list.find(
            (s) =>
              String(s.id) === String(preselectedServiceId) ||
              (s.name && s.name.toLowerCase() === preselectedServiceId.toLowerCase())
          );
          if (matched) {
            setGuests((prev) => {
              const first = prev[0] || createEmptyGuest(1);
              const alreadyHas = first.services.some((item) => item.id === matched.id);
              const updatedFirst = {
                ...first,
                services: alreadyHas ? first.services : [matched],
              };
              return [updatedFirst, ...prev.slice(1)];
            });

            const targetCatId = getCategoryForService(matched);
            setOpenCategoryId(targetCatId);
          }
        }
      })
      .catch(() => setAlertMessage('Unable to load salon services.'));
  }, [preselectedServiceId, tenant.tenantKey]);

  // Dynamically fetch staff scoped to active tenant
  useEffect(() => {
    if (currentStepKey !== 'STAFF' && currentStepKey !== 'DETAILS') return;
    setLoadingStaff(true);

    fetch('/api/nail-techs', {
      headers: { 'X-Tenant-ID': tenant.tenantKey },
    })
      .then((res) => (res.ok ? res.json() : []))
      .then((data) => {
        const list = Array.isArray(data) ? data : [];
        setStaffList(list);
      })
      .catch(() => setAlertMessage('Unable to load available technicians.'))
      .finally(() => setLoadingStaff(false));
  }, [currentStepKey, tenant.tenantKey]);

  // Dynamically fetch available time slots whenever date, staff mode, or staff assignment changes
  useEffect(() => {
    if (currentStepKey !== 'DETAILS') return;
    const selectedDate = appointmentSchedule.appointmentDate;
    if (!selectedDate) return;

    // Check Sunday client-side
    const d = new Date(selectedDate + 'T00:00:00');
    if (d.getDay() === 0) {
      setIsDateClosed(true);
      setClosedReason('Our salon is closed on Sundays. Please choose Monday through Saturday.');
      setAvailableSlots([]);
      setAppointmentSchedule((prev) => ({ ...prev, appointmentTime: '' }));
      return;
    }

    setIsDateClosed(false);
    setClosedReason('');
    setLoadingSlots(true);

    const activeStaffGuest = guests[0];
    const duration = Math.max(guest1Totals.durationMinutes || 0, guest2Totals.durationMinutes || 0) || 45;
    const params = new URLSearchParams();
    params.set('date', selectedDate);
    params.set('durationMinutes', String(duration));
    params.set('bufferMinutes', '15');
    params.set('clientNow', getLocalNowIsoStr());

    const g0Services = Array.isArray(activeStaffGuest?.services) ? activeStaffGuest.services : [];
    if (g0Services.length > 0) {
      params.set('serviceIds', g0Services.map((s) => s.id).join(','));
      params.set('serviceDurations', g0Services.map((s) => s.durationMinutes || 45).join(','));
      const srvTechs = g0Services.map((s) => {
        const assigned = activeStaffGuest?.serviceStaffAssignments?.[s.id];
        if (assigned != null && assigned !== 'anyone' && !isNaN(Number(assigned))) {
          return Number(assigned);
        }
        if (activeStaffGuest?.selectedStaff?.id && !isNaN(Number(activeStaffGuest.selectedStaff.id))) {
          return Number(activeStaffGuest.selectedStaff.id);
        }
        return 0;
      });
      params.set('serviceTechIds', srvTechs.join(','));
    }

    const assignedIds = Object.values(activeStaffGuest?.serviceStaffAssignments || {})
      .filter((id) => id != null && id !== 'anyone' && !isNaN(Number(id)));
    if (assignedIds.length === 1) {
      params.set('techId', String(assignedIds[0]));
    } else if (assignedIds.length > 1) {
      params.set('techIds', [...new Set(assignedIds)].join(','));
    } else if (activeStaffGuest?.selectedStaff?.id) {
      params.set('techId', String(activeStaffGuest.selectedStaff.id));
    }

    const applyRealTimeTodayFilter = (rawList) => {
      const isToday = selectedDate === getLocalTodayDateStr();
      const nowPlusBuffer = new Date(Date.now() + 15 * 60 * 1000);
      return (rawList || []).map((slot) => {
        let isAvail = Boolean(slot.available);
        let reason = slot.reason || null;
        if (isToday && slot.value) {
          const parts = String(slot.value).split(':');
          const slotDt = new Date();
          slotDt.setHours(parseInt(parts[0], 10), parseInt(parts[1] || '0', 10), 0, 0);
          if (slotDt < nowPlusBuffer) {
            isAvail = false;
            reason = 'Passed';
          }
        }
        return {
          ...slot,
          available: isAvail,
          reason,
          timeRangeLabel: slot.timeRangeLabel || formatTime24ToRangeLabel(slot.value, duration),
        };
      });
    };

    fetch(`/api/appointments/available-slots?${params.toString()}`, {
      headers: { 'X-Tenant-ID': tenant.tenantKey },
    })
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (!data) {
          setAvailableSlots(
            applyRealTimeTodayFilter(buildStoreTimeSlots(duration).map((s) => ({ ...s, available: true })))
          );
          return;
        }
        if (data.isClosed) {
          setIsDateClosed(true);
          setClosedReason(data.reason || 'Salon is closed on this date.');
          setAvailableSlots([]);
          setAppointmentSchedule((prev) => ({ ...prev, appointmentTime: '' }));
        } else {
          setIsDateClosed(false);
          const slots = applyRealTimeTodayFilter(data.availableSlots || []);
          setAvailableSlots(slots);
          const currentValid = slots.find(
            (s) => s.value === appointmentSchedule.appointmentTime && s.available
          );
          if (!currentValid) {
            setAppointmentSchedule((prev) => ({ ...prev, appointmentTime: '' }));
          }
        }
      })
      .catch((err) => {
        console.warn('Availability check failed, using local slots:', err);
        setAvailableSlots(
          applyRealTimeTodayFilter(buildStoreTimeSlots(duration).map((s) => ({ ...s, available: true })))
        );
      })
      .finally(() => setLoadingSlots(false));
  }, [
    appointmentSchedule.appointmentDate,
    guest1Totals.durationMinutes,
    guest2Totals.durationMinutes,
    currentStepKey,
    guests,
    tenant.tenantKey,
  ]);

  // Categorize the FULL tenant service catalog
  const categorizedServices = useMemo(() => {
    const enabledLower = (wizard?.enabledCategories || []).map((c) => c.toLowerCase().trim());
    const visibleCategories =
      enabledLower.length > 0
        ? SERVICE_CATEGORIES.filter(
            (cat) =>
              enabledLower.includes(cat.id.toLowerCase()) ||
              enabledLower.includes(cat.title.toLowerCase())
          )
        : SERVICE_CATEGORIES;

    const categorizedMap = {};
    visibleCategories.forEach((cat) => {
      categorizedMap[cat.id] = [];
    });

    services.forEach((srv) => {
      const catId = getCategoryForService(srv);
      if (categorizedMap[catId]) {
        categorizedMap[catId].push(srv);
      } else if (categorizedMap['manicures']) {
        categorizedMap['manicures'].push(srv);
      }
    });

    return visibleCategories
      .map((cat) => ({
        ...cat,
        items: categorizedMap[cat.id] || [],
      }))
      .filter((cat) => cat.items.length > 0);
  }, [services, wizard?.enabledCategories]);

  // Single Selection per Category Rule
  const toggleServiceForActiveGuest = (srv) => {
    setAlertMessage(null);
    const targetCat = getCategoryForService(srv);
    setGuests((prev) =>
      prev.map((guest, idx) => {
        if (idx !== activeGuestIndex) return guest;
        const exists = guest.services.some((item) => item.id === srv.id);
        let updatedServices;
        if (exists) {
          updatedServices = guest.services.filter((item) => item.id !== srv.id);
        } else {
          updatedServices = [
            ...guest.services.filter((item) => getCategoryForService(item) !== targetCat),
            srv,
          ];
        }
        return { ...guest, services: updatedServices };
      })
    );
  };

  const handleFinishServiceSelectionForActiveGuest = () => {
    setAlertMessage(null);
    const currentGuest = guests[activeGuestIndex];
    if (!currentGuest || currentGuest.services.length === 0) {
      setAlertMessage(`Please select at least one service for Guest ${activeGuestIndex + 1}.`);
      return;
    }

    if (activeGuestIndex === 0 && guests.length === 1) {
      setShowSoloOrGroupPrompt(true);
      return;
    }

    if (guests.length > 1 && guests[1].services.length === 0) {
      setActiveGuestIndex(1);
      setShowSoloOrGroupPrompt(false);
      setAlertMessage('Please select at least one service for Guest 2.');
      return;
    }

    setShowSoloOrGroupPrompt(false);
    if (currentStepIdx + 1 < activeSteps.length) {
      setCurrentStepIdx((prev) => prev + 1);
    }
  };

  const handleChooseJustMe = () => {
    setAlertMessage(null);
    setGuests((prev) => [prev[0]]);
    setActiveGuestIndex(0);
    setShowSoloOrGroupPrompt(false);
    if (currentStepIdx + 1 < activeSteps.length) {
      setCurrentStepIdx((prev) => prev + 1);
    }
  };

  const handleChooseAddGuest = () => {
    setAlertMessage(null);
    setGuests((prev) => (prev.length > 1 ? prev : [prev[0], createEmptyGuest(2)]));
    setActiveGuestIndex(1);
    setShowSoloOrGroupPrompt(false);
  };

  const handleRemoveSecondGuest = () => {
    setAlertMessage(null);
    setGuests((prev) => [prev[0]]);
    setActiveGuestIndex(0);
    setStaffGuestIndex(0);
    setDetailsGuestIndex(0);
  };

  // Staff Selection Handlers
  const handleSelectSingleStaff = (tech) => {
    setAlertMessage(null);
    const chosen = tech
      ? { id: tech.id, name: tech.name || tech.username, isAnyoneAvailable: false }
      : { id: null, name: 'Anyone Available', isAnyoneAvailable: true };

    setGuests((prev) =>
      prev.map((g, idx) => {
        if (idx !== staffGuestIndex) return g;
        const srv = g.services[0];
        const currentAssignments = { ...(g.serviceStaffAssignments || {}) };
        if (srv) {
          currentAssignments[srv.id] = tech ? tech.id : null;
        }
        return {
          ...g,
          selectedStaff: chosen,
          serviceStaffAssignments: currentAssignments,
        };
      })
    );

    if (staffGuestIndex === 0) {
      setSelectedStaff(chosen);
    }
  };

  const updateMultiServiceStaffStatus = (guest, assignments, overrideName) => {
    const allServices = guest.services || [];
    const allAssigned = allServices.every((s) => assignments.hasOwnProperty(s.id));
    let chosen = null;

    if (allAssigned && allServices.length > 0) {
      if (overrideName) {
        chosen = { id: null, name: overrideName, isAnyoneAvailable: false };
      } else {
        const assignedValues = allServices.map((s) => assignments[s.id]);
        const allNull = assignedValues.every((v) => v === null);
        const uniqueTechIds = [...new Set(assignedValues.filter((v) => v !== null))];

        if (allNull) {
          chosen = { id: null, name: 'Anyone Available', isAnyoneAvailable: true };
        } else if (uniqueTechIds.length === 1 && assignedValues.every((v) => v === uniqueTechIds[0])) {
          const found = staffList.find((t) => t.id === uniqueTechIds[0]);
          chosen = {
            id: uniqueTechIds[0],
            name: found ? (found.name || found.username) : 'Assigned Technician',
            isAnyoneAvailable: false,
          };
        } else {
          chosen = { id: null, name: 'Selected Staff Per Service', isAnyoneAvailable: false };
        }
      }
    }

    return {
      ...guest,
      serviceStaffAssignments: assignments,
      selectedStaff: chosen,
    };
  };

  const handleSelectServiceStaff = (srvId, tech) => {
    setAlertMessage(null);
    setGuests((prev) =>
      prev.map((g, idx) => {
        if (idx !== staffGuestIndex) return g;
        const assignments = { ...(g.serviceStaffAssignments || {}) };
        assignments[srvId] = tech ? tech.id : null;
        const updated = updateMultiServiceStaffStatus(g, assignments);
        if (idx === 0 && updated.selectedStaff) {
          setSelectedStaff(updated.selectedStaff);
        }
        return updated;
      })
    );
  };

  const handleSelectCategoryStaff = (catId, tech) => {
    setAlertMessage(null);
    const catGroup = currentGuestCategoryGroups.find((g) => g.catId === catId);
    if (!catGroup) return;

    setGuests((prev) =>
      prev.map((g, idx) => {
        if (idx !== staffGuestIndex) return g;
        const assignments = { ...(g.serviceStaffAssignments || {}) };
        catGroup.services.forEach((s) => {
          assignments[s.id] = tech ? tech.id : null;
        });
        const updated = updateMultiServiceStaffStatus(g, assignments);
        if (idx === 0 && updated.selectedStaff) {
          setSelectedStaff(updated.selectedStaff);
        }
        return updated;
      })
    );
  };

  const handleStep2Next = () => {
    setAlertMessage(null);
    const activeStaffGuest = guests[staffGuestIndex] || guests[0];
    if (!activeStaffGuest?.selectedStaff) {
      setAlertMessage(`Please choose a technician option for Guest ${staffGuestIndex + 1}.`);
      return;
    }

    if (staffGuestIndex + 1 < guests.length) {
      setStaffGuestIndex((prev) => prev + 1);
      return;
    }

    if (currentStepIdx + 1 < activeSteps.length) {
      const upcomingKey = activeSteps[currentStepIdx + 1];
      if (upcomingKey === 'DETAILS') {
        setDetailsGuestIndex(0);
      }
      setCurrentStepIdx((prev) => prev + 1);
    }
  };

  const handleStep2Back = () => {
    setAlertMessage(null);
    if (staffGuestIndex > 0) {
      setStaffGuestIndex((prev) => prev - 1);
      return;
    }
    handlePrevStep();
  };

  const validateBeforeNext = (stepKey) => {
    if (stepKey === 'SERVICE') {
      if (guest1Totals.count === 0) {
        setAlertMessage('Please select at least one service for Guest 1.');
        return false;
      }
      if (guests.length > 1 && guest2Totals.count === 0) {
        setAlertMessage('Please select at least one service for Guest 2.');
        return false;
      }
    }
    if (stepKey === 'STAFF') {
      if (!guests[0]?.selectedStaff) {
        setAlertMessage('Please choose a technician option for Guest 1.');
        return false;
      }
      if (guests.length > 1 && !guests[1]?.selectedStaff) {
        setAlertMessage('Please choose a technician option for Guest 2.');
        return false;
      }
    }
    return true;
  };

  const handleNextStep = () => {
    setAlertMessage(null);
    if (currentStepKey === 'SERVICE') {
      handleFinishServiceSelectionForActiveGuest();
      return;
    }
    if (currentStepKey === 'STAFF') {
      handleStep2Next();
      return;
    }
    if (!validateBeforeNext(currentStepKey)) return;
    if (currentStepIdx + 1 < activeSteps.length) {
      const upcomingKey = activeSteps[currentStepIdx + 1];
      if (upcomingKey === 'DETAILS') {
        setDetailsGuestIndex(0);
      }
      setCurrentStepIdx((prev) => prev + 1);
    }
  };

  const handlePrevStep = () => {
    setAlertMessage(null);
    if (currentStepKey === 'DETAILS' && detailsGuestIndex > 0) {
      setDetailsGuestIndex((prev) => prev - 1);
      return;
    }
    if (currentStepKey === 'STAFF' && staffGuestIndex > 0) {
      setStaffGuestIndex((prev) => prev - 1);
      return;
    }
    if (currentStepIdx > 0) {
      setCurrentStepIdx((prev) => prev - 1);
    }
  };

  const updateGuestDetailField = (guestIdx, field, value) => {
    setGuests((prev) =>
      prev.map((g, idx) => (idx === guestIdx ? { ...g, [field]: value } : g))
    );
  };

  const handleDateChange = (newDate) => {
    setAlertMessage(null);
    if (!newDate) return;
    const d = new Date(newDate + 'T00:00:00');
    if (d.getDay() === 0) {
      setIsDateClosed(true);
      setClosedReason('Our studio is closed on Sundays. Please pick Monday through Saturday.');
      setAppointmentSchedule({ appointmentDate: newDate, appointmentTime: '' });
      return;
    }
    setIsDateClosed(false);
    setClosedReason('');
    setAppointmentSchedule({ appointmentDate: newDate, appointmentTime: '' });
  };

  const handleGuestDetailsAction = async (e) => {
    e.preventDefault();
    setAlertMessage(null);

    const activeDetailGuest = guests[detailsGuestIndex];
    if (
      !activeDetailGuest.customerName.trim() ||
      !activeDetailGuest.customerPhone.trim() ||
      !activeDetailGuest.customerEmail.trim()
    ) {
      setAlertMessage(
        `Please complete Full Name, Phone Number, and Email Address for Guest ${detailsGuestIndex + 1}.`
      );
      return;
    }

    if (isDateClosed) {
      setAlertMessage('Selected date is closed. Please choose another date.');
      return;
    }

    if (!appointmentSchedule.appointmentDate || !appointmentSchedule.appointmentTime) {
      setAlertMessage('Please select an available Appointment Date and Time Slot.');
      return;
    }

    if (detailsGuestIndex + 1 < guests.length) {
      setDetailsGuestIndex((prev) => prev + 1);
      return;
    }

    setSubmitting(true);
    const primaryGuest = guests[0] || {};
    const payload = {
      appointmentTime: `${appointmentSchedule.appointmentDate}T${appointmentSchedule.appointmentTime}`,
      stationPreference:
        wizard?.enableStationSelection && activeSteps.includes('STATION')
          ? selectedStation
          : null,
      nailTech: (guests[0]?.selectedStaff?.id || selectedStaff?.id)
        ? { id: guests[0]?.selectedStaff?.id || selectedStaff.id }
        : null,
      customerName: primaryGuest.customerName ? primaryGuest.customerName.trim() : '',
      customerPhone: primaryGuest.customerPhone ? primaryGuest.customerPhone.trim() : '',
      customerEmail: primaryGuest.customerEmail ? primaryGuest.customerEmail.trim() : '',
      notes: wizard?.showAppointmentNotes && primaryGuest.notes ? primaryGuest.notes.trim() : '',
      serviceIds: (primaryGuest.services && Array.isArray(primaryGuest.services))
        ? primaryGuest.services.map((s) => s.id)
        : [],
      service: (primaryGuest.services && primaryGuest.services.length > 0)
        ? { id: primaryGuest.services[0].id }
        : null,
      guests: guests.map((g, idx) => ({
        guestNumber: idx + 1,
        customerName: g.customerName.trim(),
        customerPhone: g.customerPhone.trim(),
        customerEmail: g.customerEmail.trim(),
        notes: wizard?.showAppointmentNotes ? g.notes.trim() : '',
        serviceIds: g.services.map((s) => s.id),
        serviceStaffAssignments: g.serviceStaffAssignments || {},
      })),
    };

    try {
      const response = await fetch('/api/bookings', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Tenant-ID': tenant.tenantKey,
        },
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        const errData = await response.json().catch(() => ({}));
        throw new Error(errData.message || 'Could not complete booking.');
      }

      const saved = await response.json();
      setConfirmedBooking({
        ...saved,
        submittedPayload: payload,
        guestsSnapshot: guests,
        grandTotal: combinedTotals.grandTotal,
        totalDurationMinutes: combinedTotals.totalDurationMinutes,
      });
    } catch (err) {
      setAlertMessage(err.message || 'Network error while submitting your booking.');
    } finally {
      setSubmitting(false);
    }
  };

  if (confirmedBooking) {
    return (
      <div className="container my-5">
        <div
          className="card border-0 shadow-sm rounded-4 p-4 p-md-5 text-center mx-auto"
          style={{ maxWidth: 680 }}
        >
          <h3 className="fw-bold mb-2" style={{ color: 'var(--primary-color)' }}>
            Appointment Confirmed!
          </h3>
          <p className="text-muted mb-4">
            Thank you for booking with {salonName}. Reference #{confirmedBooking.id || 'NEW'}
          </p>

          <div className="bg-light rounded-3 p-3 text-start mb-4">
            {confirmedBooking.guestsSnapshot?.map((guest, idx) => {
              const totals = calculateGuestTotals(guest);
              return (
                <div key={guest.guestNumber || idx} className="mb-3 pb-3 border-bottom">
                  <div className="d-flex justify-content-between align-items-center mb-1">
                    <strong style={{ color: 'var(--primary-color)' }}>
                      Guest {idx + 1}: {guest.customerName}
                    </strong>
                    <span className="fw-bold">
                      Subtotal: ${totals.subtotal.toFixed(2)} ({totals.durationMinutes} mins)
                    </span>
                  </div>
                  <div className="small text-muted mb-1">
                    {guest.customerPhone} • {guest.customerEmail}
                  </div>
                  <ul className="mb-1 ps-3 small">
                    {guest.services.map((srv) => (
                      <li key={srv.id}>
                        {srv.name} — ${Number(srv.price || 0).toFixed(2)} ({srv.durationMinutes || 45} mins)
                      </li>
                    ))}
                  </ul>
                  {guest.notes && (
                    <div className="small text-muted">
                      <em>Notes: {guest.notes}</em>
                    </div>
                  )}
                </div>
              );
            })}

            <div className="d-flex justify-content-between py-1 border-bottom">
              <span className="text-muted">Technician</span>
              <strong>{selectedStaff?.name || 'Anyone Available'}</strong>
            </div>
            {confirmedBooking.submittedPayload?.stationPreference && (
              <div className="d-flex justify-content-between py-1 border-bottom">
                <span className="text-muted">Station / Room</span>
                <strong>{confirmedBooking.submittedPayload.stationPreference}</strong>
              </div>
            )}
            <div className="d-flex justify-content-between py-1 border-bottom">
              <span className="text-muted">Date &amp; Time Window</span>
              <strong>
                {(() => {
                  const raw = confirmedBooking.submittedPayload?.appointmentTime || '';
                  if (!raw.includes('T')) return raw;
                  const [datePart, timePart] = raw.split('T');
                  return `${datePart} • ${formatTime24ToRangeLabel(
                    timePart,
                    confirmedBooking.totalDurationMinutes || 45
                  )}`;
                })()}
              </strong>
            </div>
            <div className="d-flex justify-content-between pt-2 fs-5">
              <span className="fw-bold">Grand Total</span>
              <strong style={{ color: 'var(--primary-color)' }}>
                ${Number(confirmedBooking.grandTotal || 0).toFixed(2)} (
                {confirmedBooking.totalDurationMinutes} mins)
              </strong>
            </div>
          </div>

          <button
            type="button"
            className="btn text-white fw-bold px-4 py-2 rounded-pill"
            style={{ backgroundColor: 'var(--primary-color)' }}
            onClick={resetToBlankWizard}
          >
            Start New Booking
          </button>
        </div>
      </div>
    );
  }

  const nextStepKey = activeSteps[currentStepIdx + 1];
  const nextStepLabel = nextStepKey ? STEP_METADATA[nextStepKey]?.title : 'Next';
  const activeGuest = guests[activeGuestIndex] || guests[0];
  const activeGuestTotals = activeGuestIndex === 0 ? guest1Totals : guest2Totals;
  const currentStaffGuest = guests[staffGuestIndex] || guests[0];
  const currentGuestStaff = currentStaffGuest?.selectedStaff;

  // Group services by category for Step 2 Big Cards
  const currentGuestCategoryGroups = useMemo(() => {
    const list = currentStaffGuest?.services || [];
    const groupMap = new Map();
    list.forEach((srv) => {
      const catId = getCategoryForService(srv);
      const backendCat = mapToBackendCategory(catId);
      const catConfig = SERVICE_CATEGORIES.find((c) => c.id === catId);
      const catTitle = catConfig ? catConfig.title : 'Treatments';
      if (!groupMap.has(catId)) {
        groupMap.set(catId, { catId, catTitle, backendCat, services: [] });
      }
      groupMap.get(catId).services.push(srv);
    });
    return Array.from(groupMap.values());
  }, [currentStaffGuest?.services]);

  return (
    <main className={`container my-5 flex-grow-1 layout-${layoutVariant}`}>
      <div className="row justify-content-center">
        <div className="col-lg-10 col-xl-9">
          <div
            className="card shadow-sm border-0 rounded-4"
            style={{
              borderTop:
                layoutVariant === 'blue-sky' || layoutVariant === 'luxe'
                  ? '5px solid var(--primary-color)'
                  : undefined,
            }}
          >
            <div className="card-body p-4 p-md-5">
              <div className="d-flex justify-content-between align-items-center mb-2">
                <h2 className="fw-bold mb-0" style={{ color: 'var(--primary-color)' }}>
                  Book Your Appointment
                </h2>
                <button
                  type="button"
                  className="btn btn-outline-secondary btn-sm rounded-pill px-3"
                  onClick={resetToBlankWizard}
                >
                  Clear Selection
                </button>
              </div>

              <p className="text-muted mb-4">
                Complete the {activeSteps.length} steps below to reserve your visit at {salonName}
              </p>

              {/* Dynamic Stepper */}
              <div className="d-flex justify-content-between align-items-center mb-4">
                {activeSteps.map((stepKey, idx) => {
                  const isCompleted = idx < currentStepIdx;
                  const isActive = idx === currentStepIdx;
                  return (
                    <div key={stepKey} className="text-center flex-fill">
                      <div
                        className="d-inline-flex align-items-center justify-content-center rounded-circle fw-bold mb-1"
                        style={{
                          width: 42,
                          height: 42,
                          backgroundColor: isCompleted
                            ? '#198754'
                            : isActive
                            ? 'var(--primary-color)'
                            : '#e9ecef',
                          color: isCompleted || isActive ? '#fff' : '#6c757d',
                        }}
                      >
                        {isCompleted ? '✓' : idx + 1}
                      </div>
                      <div
                        className="small fw-semibold"
                        style={{ color: isActive ? 'var(--primary-color)' : '#6c757d' }}
                      >
                        {idx + 1}. {STEP_METADATA[stepKey]?.title || stepKey}
                      </div>
                    </div>
                  );
                })}
              </div>

              {alertMessage && (
                <div className="alert alert-danger" role="alert">
                  {alertMessage}
                </div>
              )}

              {/* ==================== STEP 1: SERVICE (MULTI-SELECT + SOLO/GROUP PROMPT) ==================== */}
              {currentStepKey === 'SERVICE' && (
                <section>
                  <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
                    <div>
                      <h5 className="fw-bold mb-1">
                        Step {currentStepIdx + 1}: Choose Services for Guest {activeGuestIndex + 1}
                      </h5>
                      <small className="text-muted">
                        Select one or more services from the full menu below
                      </small>
                    </div>

                    {guests.length > 1 && (
                      <div className="d-flex align-items-center gap-2">
                        <button
                          type="button"
                          className={`btn btn-sm rounded-pill px-3 ${
                            activeGuestIndex === 0 ? 'text-white fw-bold' : 'btn-outline-secondary'
                          }`}
                          style={
                            activeGuestIndex === 0
                              ? { backgroundColor: 'var(--primary-color)' }
                              : undefined
                          }
                          onClick={() => {
                            setShowSoloOrGroupPrompt(false);
                            setActiveGuestIndex(0);
                          }}
                        >
                          Guest 1 ({guest1Totals.count})
                        </button>
                        <button
                          type="button"
                          className={`btn btn-sm rounded-pill px-3 ${
                            activeGuestIndex === 1 ? 'text-white fw-bold' : 'btn-outline-secondary'
                          }`}
                          style={
                            activeGuestIndex === 1
                              ? { backgroundColor: 'var(--primary-color)' }
                              : undefined
                          }
                          onClick={() => {
                            setShowSoloOrGroupPrompt(false);
                            setActiveGuestIndex(1);
                          }}
                        >
                          Guest 2 ({guest2Totals.count})
                        </button>
                        <button
                          type="button"
                          className="btn btn-sm btn-outline-danger rounded-pill px-2"
                          onClick={handleRemoveSecondGuest}
                          title="Remove Guest 2"
                        >
                          ✕ Remove
                        </button>
                      </div>
                    )}
                  </div>

                  {/* Subtotal Banner */}
                  <div className="card bg-light border-0 rounded-4 p-3 mb-4">
                    <div className="row g-3 align-items-center">
                      <div className={guests.length > 1 ? 'col-md-4' : 'col-md-12'}>
                        <div className="small text-muted fw-semibold">Guest 1 Subtotal</div>
                        <div className="fw-bold fs-5" style={{ color: 'var(--primary-color)' }}>
                          ${guest1Totals.subtotal.toFixed(2)}{' '}
                          <span className="small text-muted fw-normal">
                            ({guest1Totals.count} services • {guest1Totals.durationMinutes} mins)
                          </span>
                        </div>
                      </div>

                      {guests.length > 1 && (
                        <>
                          <div className="col-md-4 border-start">
                            <div className="small text-muted fw-semibold">Guest 2 Subtotal</div>
                            <div className="fw-bold fs-5" style={{ color: 'var(--primary-color)' }}>
                              ${guest2Totals.subtotal.toFixed(2)}{' '}
                              <span className="small text-muted fw-normal">
                                ({guest2Totals.count} services • {guest2Totals.durationMinutes} mins)
                              </span>
                            </div>
                          </div>
                          <div className="col-md-4 border-start">
                            <div className="small text-muted fw-semibold">Combined Group Total</div>
                            <div className="fw-bold fs-4 text-dark">
                              ${combinedTotals.grandTotal.toFixed(2)}
                            </div>
                            <div className="small text-muted">
                              {combinedTotals.totalServicesCount} total services •{' '}
                              {combinedTotals.totalDurationMinutes} mins
                            </div>
                          </div>
                        </>
                      )}
                    </div>
                  </div>

                  {/* Solo vs. Group Prompt */}
                  {showSoloOrGroupPrompt ? (
                    <div
                      className="card border-2 rounded-4 p-4 mb-4 text-center shadow-sm"
                      style={{ borderColor: 'var(--primary-color)' }}
                    >
                      <h5 className="fw-bold text-dark mb-2">
                        Are you booking just for yourself or adding a guest?
                      </h5>
                      <p className="text-muted small mb-4">
                        Guest 1 has {guest1Totals.count} service
                        {guest1Totals.count === 1 ? '' : 's'} selected ($
                        {guest1Totals.subtotal.toFixed(2)}).
                      </p>
                      <div className="d-flex flex-wrap justify-content-center gap-3">
                        <button
                          type="button"
                          className="btn text-white fw-bold px-4 py-3 rounded-3"
                          style={{ backgroundColor: 'var(--primary-color)' }}
                          onClick={handleChooseJustMe}
                        >
                          Just me (Going by myself) →
                        </button>
                        <button
                          type="button"
                          className="btn btn-outline-dark fw-bold px-4 py-3 rounded-3"
                          onClick={handleChooseAddGuest}
                        >
                          Add another person (+1 Guest)
                        </button>
                      </div>
                      <div className="mt-3">
                        <button
                          type="button"
                          className="btn btn-link btn-sm text-muted"
                          onClick={() => setShowSoloOrGroupPrompt(false)}
                        >
                          ← Keep editing Guest 1 services
                        </button>
                      </div>
                    </div>
                  ) : (
                    <>
                      <div className="d-flex flex-column gap-3 mb-4">
                        {categorizedServices.map((cat) => {
                          const isOpen = openCategoryId === cat.id;
                          const selectedInCat = activeGuest.services.filter(
                            (s) => getCategoryForService(s) === cat.id
                          ).length;

                          return (
                            <div key={cat.id} className="card border rounded-4 shadow-sm">
                              <div
                                className="card-header bg-white border-0 p-3 d-flex justify-content-between align-items-center"
                                style={{ cursor: 'pointer' }}
                                onClick={() => setOpenCategoryId(isOpen ? null : cat.id)}
                              >
                                <div className="d-flex align-items-center gap-2">
                                  <h6 className="fw-bold mb-0">{cat.title}</h6>
                                  <small className="text-muted">({cat.items.length} services)</small>
                                  {selectedInCat > 0 && (
                                    <span
                                      className="badge rounded-pill text-white"
                                      style={{ backgroundColor: 'var(--primary-color)' }}
                                    >
                                      {selectedInCat} selected for Guest {activeGuestIndex + 1}
                                    </span>
                                  )}
                                </div>
                                <span className="fw-bold" style={{ color: 'var(--primary-color)' }}>
                                  {isOpen ? '▲' : '▼'}
                                </span>
                              </div>

                              {isOpen && (
                                <div className="card-body pt-0">
                                  <div className="list-group list-group-flush">
                                    {cat.items.map((srv) => {
                                      const isSelected = activeGuest.services.some(
                                        (s) => s.id === srv.id
                                      );
                                      return (
                                        <button
                                          key={srv.id}
                                          type="button"
                                          className="list-group-item list-group-item-action d-flex justify-content-between align-items-center py-3"
                                          style={{
                                            backgroundColor: isSelected
                                              ? 'var(--primary-color-soft)'
                                              : 'transparent',
                                            borderLeft: isSelected
                                              ? '4px solid var(--primary-color)'
                                              : 'none',
                                          }}
                                          onClick={() => toggleServiceForActiveGuest(srv)}
                                        >
                                          <div className="d-flex align-items-center gap-3 text-start">
                                            <input
                                              type="checkbox"
                                              className="form-check-input mt-0"
                                              checked={isSelected}
                                              readOnly
                                            />
                                            <div>
                                              <div className="fw-bold text-dark">{srv.name}</div>
                                              <small className="text-muted">
                                                ${Number(srv.price || 0).toFixed(2)} •{' '}
                                                {srv.durationMinutes || 45} mins
                                              </small>
                                            </div>
                                          </div>
                                          {isSelected && (
                                            <span
                                              className="badge rounded-pill"
                                              style={{ backgroundColor: 'var(--primary-color)' }}
                                            >
                                              Selected
                                            </span>
                                          )}
                                        </button>
                                      );
                                    })}
                                  </div>
                                </div>
                              )}
                            </div>
                          );
                        })}
                      </div>

                      {activeGuestTotals.count > 0 && (
                        <div
                          className="position-fixed bottom-0 start-50 translate-middle-x mb-4 px-4 py-3 bg-white rounded-pill shadow-lg d-flex align-items-center gap-3"
                          style={{ zIndex: 1050, border: '1px solid var(--primary-color)' }}
                        >
                          <div>
                            <div className="fw-bold small text-dark">
                              Guest {activeGuestIndex + 1}: {activeGuestTotals.count} service
                              {activeGuestTotals.count === 1 ? '' : 's'}
                            </div>
                            <div
                              className="small fw-semibold"
                              style={{ color: 'var(--primary-color)' }}
                            >
                              Subtotal: ${activeGuestTotals.subtotal.toFixed(2)} •{' '}
                              {activeGuestTotals.durationMinutes} mins
                              {guests.length > 1 &&
                                ` | Group Total: $${combinedTotals.grandTotal.toFixed(2)}`}
                            </div>
                          </div>
                          <button
                            type="button"
                            className="btn rounded-pill px-4 py-2 fw-bold text-white"
                            style={{ backgroundColor: 'var(--primary-color)' }}
                            onClick={handleFinishServiceSelectionForActiveGuest}
                          >
                            {activeGuestIndex === 0 && guests.length === 1
                              ? 'Continue →'
                              : `${nextStepLabel} →`}
                          </button>
                        </div>
                      )}
                    </>
                  )}
                </section>
              )}

              {/* ==================== STEP 2: STAFF & ASSIGNMENT MODES ==================== */}
              {currentStepKey === 'STAFF' && (
                <section>
                  <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
                    <div>
                      <h5 className="fw-bold mb-1">
                        {guests.length > 1
                          ? `Step ${currentStepIdx + 1}: Choose Technician for Guest ${staffGuestIndex + 1} of ${guests.length}`
                          : `Step ${currentStepIdx + 1}: ${STEP_METADATA.STAFF.title}`}
                      </h5>
                      <p className="text-muted small mb-0">
                        Select how you would like technicians assigned for your visit
                      </p>
                    </div>
                    <span
                      className="badge rounded-pill px-3 py-2 text-white"
                      style={{ backgroundColor: 'var(--primary-color)' }}
                    >
                      Guest {staffGuestIndex + 1}: {currentStaffGuest?.services.length || 0} Service
                      {currentStaffGuest?.services.length === 1 ? '' : 's'}
                    </span>
                  </div>

                  {loadingStaff ? (
                    <div className="text-center py-4 text-muted">Loading available staff...</div>
                  ) : currentStaffGuest?.services.length <= 1 ? (
                    /* 1 Service Selection: Regular staff cards with (Anyone Available) at top */
                    <div className="row g-3 mb-4">
                      {/* Option: Anyone Available */}
                      <div className="col-md-6">
                        <div
                          className="card p-3 h-100 d-flex flex-row align-items-center justify-content-between rounded-3 shadow-sm"
                          style={{
                            cursor: 'pointer',
                            borderColor: currentGuestStaff?.isAnyoneAvailable ? 'var(--primary-color)' : '#dee2e6',
                            borderWidth: currentGuestStaff?.isAnyoneAvailable ? '2px' : '1px',
                            backgroundColor: currentGuestStaff?.isAnyoneAvailable ? 'var(--primary-color-soft)' : '#fff',
                          }}
                          onClick={() => handleSelectSingleStaff(null)}
                        >
                          <div className="d-flex align-items-center gap-3">
                            <div
                              className="rounded-circle d-flex align-items-center justify-content-center text-white"
                              style={{ width: 44, height: 44, backgroundColor: 'var(--primary-color)' }}
                            >
                              ⚡
                            </div>
                            <div>
                              <h6 className="fw-bold text-dark mb-0">Anyone Available</h6>
                              <small className="text-muted">Fastest booking • Any qualified specialist</small>
                            </div>
                          </div>
                          {currentGuestStaff?.isAnyoneAvailable && (
                            <span style={{ color: 'var(--primary-color)', fontWeight: 700 }}>✓</span>
                          )}
                        </div>
                      </div>

                      {/* Qualified staff list */}
                      {staffList
                        .filter((tech) => {
                          const srv = currentStaffGuest?.services[0];
                          const backendCat = srv ? mapToBackendCategory(getCategoryForService(srv)) : 'MANICURE';
                          return isTechQualifiedForBackendCategory(tech, backendCat);
                        })
                        .map((tech) => {
                          const isSelected = !currentGuestStaff?.isAnyoneAvailable && currentGuestStaff?.id === tech.id;
                          return (
                            <div key={tech.id} className="col-md-6">
                              <div
                                className="card p-3 h-100 d-flex flex-row align-items-center justify-content-between rounded-3 shadow-sm"
                                style={{
                                  cursor: 'pointer',
                                  borderColor: isSelected ? 'var(--primary-color)' : '#dee2e6',
                                  borderWidth: isSelected ? '2px' : '1px',
                                  backgroundColor: isSelected ? 'var(--primary-color-soft)' : '#fff',
                                }}
                                onClick={() => handleSelectSingleStaff(tech)}
                              >
                                <div className="d-flex align-items-center gap-3">
                                  <div
                                    className="rounded-circle d-flex align-items-center justify-content-center text-secondary bg-light border"
                                    style={{ width: 44, height: 44 }}
                                  >
                                    👤
                                  </div>
                                  <div>
                                    <h6 className="fw-bold text-dark mb-0">{tech.name || tech.username}</h6>
                                    <small className="text-muted">{getStaffPronouns(tech)}</small>
                                  </div>
                                </div>
                                {isSelected && (
                                  <span style={{ color: 'var(--primary-color)', fontWeight: 700 }}>✓</span>
                                )}
                              </div>
                            </div>
                          );
                        })}
                    </div>
                  ) : (
                    /* 2+ Services Selection: Independent per-service staff cards filtered strictly by each service's category */
                    <div className="d-flex flex-column gap-4 mb-4">
                      {(currentStaffGuest?.services || []).map((srv, srvIdx) => {
                        const catId = getCategoryForService(srv);
                        const backendCat = mapToBackendCategory(catId);
                        const catConfig = SERVICE_CATEGORIES.find((c) => c.id === catId);
                        const catTitle = catConfig ? catConfig.title : 'Treatment';
                        const qualifiedTechs = staffList.filter((tech) =>
                          isTechQualifiedForBackendCategory(tech, backendCat, srv)
                        );
                        const assignedVal = currentStaffGuest?.serviceStaffAssignments?.[srv.id];
                        const isAnyoneAssigned = assignedVal === null;

                        return (
                          <div key={srv.id} className="card border rounded-4 p-4 shadow-sm bg-white">
                            <div className="d-flex flex-wrap justify-content-between align-items-center border-bottom pb-3 mb-3">
                              <div>
                                <span
                                  className="badge rounded-pill px-2 py-1 small mb-1 text-white"
                                  style={{ backgroundColor: 'var(--primary-color)' }}
                                >
                                  Service {srvIdx + 1} of {currentStaffGuest.services.length} • {catTitle}
                                </span>
                                <h5 className="fw-bold text-dark mb-1 mt-1">
                                  <span className="me-2" style={{ color: 'var(--primary-color)' }}>✦</span>
                                  {srv.name}
                                </h5>
                                <div className="small text-muted">
                                  ${Number(srv.price || 0).toFixed(2)} • {srv.durationMinutes || 45} mins
                                </div>
                              </div>
                              <span className="badge bg-light text-secondary border px-3 py-2 rounded-pill small">
                                {qualifiedTechs.length} Specialist{qualifiedTechs.length === 1 ? '' : 's'} Available
                              </span>
                            </div>

                            <div className="row g-3">
                              {/* Anyone Available for this specific service */}
                              <div className="col-md-6">
                                <div
                                  className="card p-3 h-100 d-flex flex-row align-items-center justify-content-between rounded-3 shadow-sm"
                                  style={{
                                    cursor: 'pointer',
                                    borderColor: isAnyoneAssigned ? 'var(--primary-color)' : '#dee2e6',
                                    borderWidth: isAnyoneAssigned ? '2px' : '1px',
                                    backgroundColor: isAnyoneAssigned ? 'var(--primary-color-soft)' : '#fff',
                                  }}
                                  onClick={() => handleSelectServiceStaff(srv.id, null)}
                                >
                                  <div className="d-flex align-items-center gap-3">
                                    <div
                                      className="rounded-circle d-flex align-items-center justify-content-center text-white"
                                      style={{ width: 44, height: 44, backgroundColor: 'var(--primary-color)' }}
                                    >
                                      ⚡
                                    </div>
                                    <div>
                                      <h6 className="fw-bold text-dark mb-0">Anyone Available</h6>
                                      <small className="text-muted">Auto-assign qualified specialist</small>
                                    </div>
                                  </div>
                                  {isAnyoneAssigned && (
                                    <span style={{ color: 'var(--primary-color)', fontWeight: 700 }}>✓</span>
                                  )}
                                </div>
                              </div>

                              {/* Qualified staff for this specific service */}
                              {qualifiedTechs.map((tech) => {
                                const isAssigned = assignedVal === tech.id;

                                return (
                                  <div key={tech.id} className="col-md-6">
                                    <div
                                      className="card p-3 h-100 d-flex flex-row align-items-center justify-content-between rounded-3 shadow-sm"
                                      style={{
                                        cursor: 'pointer',
                                        borderColor: isAssigned ? 'var(--primary-color)' : '#dee2e6',
                                        borderWidth: isAssigned ? '2px' : '1px',
                                        backgroundColor: isAssigned ? 'var(--primary-color-soft)' : '#fff',
                                      }}
                                      onClick={() => handleSelectServiceStaff(srv.id, tech)}
                                    >
                                      <div className="d-flex align-items-center gap-3">
                                        <div
                                          className="rounded-circle d-flex align-items-center justify-content-center text-secondary bg-light border"
                                          style={{ width: 44, height: 44 }}
                                        >
                                          👤
                                        </div>
                                        <div>
                                          <h6 className="fw-bold text-dark mb-0">{tech.name || tech.username}</h6>
                                          <small className="text-muted">{getStaffPronouns(tech)}</small>
                                        </div>
                                      </div>
                                      {isAssigned && (
                                        <span style={{ color: 'var(--primary-color)', fontWeight: 700 }}>✓</span>
                                      )}
                                    </div>
                                  </div>
                                );
                              })}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}

                  <div className="d-flex justify-content-between">
                    <button
                      type="button"
                      className="btn btn-outline-secondary px-4 py-2"
                      onClick={handleStep2Back}
                    >
                      ← {staffGuestIndex > 0 ? 'Back to Guest 1 Staff' : 'Back'}
                    </button>
                    <button
                      type="button"
                      className="btn text-white fw-semibold px-4 py-2 ms-auto"
                      style={{ backgroundColor: 'var(--primary-color)' }}
                      disabled={!currentGuestStaff}
                      onClick={handleStep2Next}
                    >
                      {staffGuestIndex + 1 < guests.length
                        ? 'Next: Guest 2 Staff →'
                        : `Next: ${nextStepLabel} →`}
                    </button>
                  </div>

                  {/* Sticky Floating Action Button */}
                  {currentGuestStaff && (
                    <div
                      className="position-fixed bottom-0 start-50 translate-middle-x mb-4 px-4 py-3 bg-white rounded-pill shadow-lg d-flex align-items-center gap-3"
                      style={{ zIndex: 1050, border: '1px solid var(--primary-color)' }}
                    >
                      <div>
                        <div className="fw-bold small text-dark">
                          {currentGuestStaff.name || 'Mode Selected'}
                        </div>
                        <div className="small fw-semibold" style={{ color: 'var(--primary-color)' }}>
                          Ready for Guest {staffGuestIndex + 1}
                        </div>
                      </div>
                      <button
                        type="button"
                        className="btn rounded-pill px-4 py-2 fw-bold text-white"
                        style={{ backgroundColor: 'var(--primary-color)' }}
                        onClick={handleStep2Next}
                      >
                        {staffGuestIndex + 1 < guests.length
                          ? 'Next: Guest 2 Staff →'
                          : 'Next: Details & Confirm →'}
                      </button>
                    </div>
                  )}
                </section>
              )}

              {/* ==================== OPTIONAL STEP: STATION ==================== */}
              {currentStepKey === 'STATION' && wizard?.enableStationSelection && (
                <section>
                  <h5 className="fw-bold mb-1">
                    Step {currentStepIdx + 1}: {STEP_METADATA.STATION.title}
                  </h5>
                  <p className="text-muted small mb-3">{STEP_METADATA.STATION.subtitle}</p>

                  <div className="row g-3 mb-4">
                    {['Any Available Station', ...(wizard.availableStations || [])].map(
                      (stationName) => {
                        const isSelected = selectedStation === stationName;
                        return (
                          <div key={stationName} className="col-md-6">
                            <div
                              className="card p-3 h-100 d-flex flex-row justify-content-between align-items-center"
                              style={{
                                cursor: 'pointer',
                                borderColor: isSelected ? 'var(--primary-color)' : '#dee2e6',
                                backgroundColor: isSelected ? 'var(--primary-color-soft)' : '#fff',
                              }}
                              onClick={() => setSelectedStation(stationName)}
                            >
                              <span className="fw-bold text-dark">{stationName}</span>
                              {isSelected && (
                                <span style={{ color: 'var(--primary-color)', fontWeight: 700 }}>
                                  ✓
                                </span>
                              )}
                            </div>
                          </div>
                        );
                      }
                    )}
                  </div>

                  <div className="d-flex justify-content-between">
                    <button
                      type="button"
                      className="btn btn-outline-secondary px-4 py-2"
                      onClick={handlePrevStep}
                    >
                      ← Back
                    </button>
                    <button
                      type="button"
                      className="btn text-white fw-semibold px-4 py-2"
                      style={{ backgroundColor: 'var(--primary-color)' }}
                      onClick={handleNextStep}
                    >
                      Next: {nextStepLabel} →
                    </button>
                  </div>
                </section>
              )}

              {/* ==================== FINAL STEP: SEQUENTIAL GUEST DETAILS & CONFIRM ==================== */}
              {currentStepKey === 'DETAILS' && (
                <form onSubmit={handleGuestDetailsAction}>
                  <div className="d-flex justify-content-between align-items-center mb-3">
                    <h5 className="fw-bold mb-0">
                      Step {currentStepIdx + 1}: Guest {detailsGuestIndex + 1} of {guests.length}{' '}
                      Details
                    </h5>
                    {guests.length > 1 && (
                      <span
                        className="badge rounded-pill px-3 py-2 text-white"
                        style={{ backgroundColor: 'var(--primary-color)' }}
                      >
                        Combined Group Total: ${combinedTotals.grandTotal.toFixed(2)}
                      </span>
                    )}
                  </div>

                  {/* Current Guest Selected Services & Individual Subtotal Card */}
                  {(() => {
                    const currentGuest = guests[detailsGuestIndex] || guests[0];
                    const currentTotals = calculateGuestTotals(currentGuest);
                    return (
                      <div className="card bg-light border-0 rounded-3 p-3 mb-4">
                        <div className="d-flex justify-content-between align-items-center border-bottom pb-2 mb-2">
                          <strong style={{ color: 'var(--primary-color)' }}>
                            Guest {detailsGuestIndex + 1} Selected Services
                          </strong>
                          <span className="fw-bold text-dark">
                            Guest {detailsGuestIndex + 1} Total: ${currentTotals.subtotal.toFixed(2)}{' '}
                            ({currentTotals.durationMinutes} mins)
                          </span>
                        </div>
                        <ul className="mb-2 ps-3 small">
                          {currentGuest.services.map((srv) => (
                            <li key={srv.id}>
                              <strong>{srv.name}</strong> — ${Number(srv.price || 0).toFixed(2)} (
                              {srv.durationMinutes || 45} mins)
                            </li>
                          ))}
                        </ul>
                        <div className="row g-2 pt-2 border-top small">
                          <div className="col-md-4">
                            <span className="text-muted">Technician Assignment: </span>
                            <strong>{currentGuest.selectedStaff?.name || selectedStaff?.name || 'Anyone Available'}</strong>
                          </div>
                          <div className="col-md-4">
                            <span className="text-muted">Time Window: </span>
                            <strong style={{ color: 'var(--primary-color)' }}>
                              {appointmentSchedule.appointmentTime
                                ? `${appointmentSchedule.appointmentDate} • ${formatTime24ToRangeLabel(
                                    appointmentSchedule.appointmentTime,
                                    currentTotals.durationMinutes || 45
                                  )}`
                                : 'Select slot below'}
                            </strong>
                          </div>
                          {wizard?.enableStationSelection && activeSteps.includes('STATION') && (
                            <div className="col-md-4">
                              <span className="text-muted">Station / Room: </span>
                              <strong>{selectedStation}</strong>
                            </div>
                          )}
                        </div>
                      </div>
                    );
                  })()}

                  <div className="row g-3 mb-3">
                    <div className="col-md-6">
                      <label className="form-label fw-semibold">
                        Guest {detailsGuestIndex + 1} Full Name *
                      </label>
                      <input
                        type="text"
                        className="form-control"
                        required
                        value={guests[detailsGuestIndex]?.customerName || ''}
                        onChange={(e) =>
                          updateGuestDetailField(detailsGuestIndex, 'customerName', e.target.value)
                        }
                      />
                    </div>
                    <div className="col-md-6">
                      <label className="form-label fw-semibold">
                        Guest {detailsGuestIndex + 1} Phone Number *
                      </label>
                      <input
                        type="tel"
                        className="form-control"
                        required
                        value={guests[detailsGuestIndex]?.customerPhone || ''}
                        onChange={(e) =>
                          updateGuestDetailField(detailsGuestIndex, 'customerPhone', e.target.value)
                        }
                      />
                    </div>
                  </div>

                  <div className="mb-3">
                    <label className="form-label fw-semibold">
                      Guest {detailsGuestIndex + 1} Email Address *
                    </label>
                    <input
                      type="email"
                      className="form-control"
                      required
                      value={guests[detailsGuestIndex]?.customerEmail || ''}
                      onChange={(e) =>
                        updateGuestDetailField(detailsGuestIndex, 'customerEmail', e.target.value)
                      }
                    />
                  </div>

                  <div className="row g-3 mb-3">
                    <div className="col-md-5">
                      <label className="form-label fw-semibold">Appointment Date *</label>
                      <input
                        type="date"
                        className="form-control"
                        required
                        min={getLocalTodayDateStr()}
                        value={appointmentSchedule.appointmentDate}
                        onChange={(e) => handleDateChange(e.target.value)}
                      />
                      {isDateClosed && (
                        <div className="text-danger small mt-1 fw-semibold">
                          ⚠️ {closedReason}
                        </div>
                      )}
                    </div>
                    <div className="col-md-7 d-flex align-items-end">
                      <div className="w-100 p-2 px-3 rounded-3 border bg-light d-flex flex-wrap justify-content-between align-items-center gap-2">
                        <div>
                          <div className="small text-muted fw-semibold">Selected Time Window</div>
                          <div className="fw-bold small" style={{ color: 'var(--primary-color)' }}>
                            {appointmentSchedule.appointmentTime
                              ? `${formatTime24ToRangeLabel(
                                  appointmentSchedule.appointmentTime,
                                  Math.max(guest1Totals.durationMinutes || 0, guest2Totals.durationMinutes || 0) || 45
                                )} (${Math.max(guest1Totals.durationMinutes || 0, guest2Totals.durationMinutes || 0) || 45} mins)`
                              : 'Click an available time card below'}
                          </div>
                        </div>
                        <div className="form-check form-switch mb-0">
                          <input
                            className="form-check-input"
                            type="checkbox"
                            id="reactHideUnavailableToggle"
                            checked={hideUnavailableSlots}
                            onChange={(e) => setHideUnavailableSlots(e.target.checked)}
                          />
                          <label className="form-check-label small text-muted" htmlFor="reactHideUnavailableToggle">
                            Hide Booked
                          </label>
                        </div>
                      </div>
                    </div>
                    <div className="col-12">
                      <label className="form-label fw-semibold">
                        Select Time Slot Card (Start – Est. End Time) *
                      </label>
                      {loadingSlots ? (
                        <div className="text-muted small py-3 text-center border rounded-3 bg-light">
                          Synchronizing technician schedule &amp; available time windows...
                        </div>
                      ) : isDateClosed ? (
                        <div className="text-danger small py-3 text-center border rounded-3 bg-light fw-semibold">
                          {closedReason || 'Salon is closed on this date.'}
                        </div>
                      ) : (
                        <div
                          style={{
                            display: 'grid',
                            gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))',
                            gap: '0.6rem',
                            maxHeight: 310,
                            overflowY: 'auto',
                            padding: '0.35rem',
                          }}
                        >
                          {availableSlots
                            .filter((slot) => !hideUnavailableSlots || slot.available)
                            .map((slot) => {
                              const isSelected =
                                slot.available && appointmentSchedule.appointmentTime === slot.value;
                              const rangeLabel =
                                slot.timeRangeLabel ||
                                formatTime24ToRangeLabel(
                                  slot.value,
                                  Math.max(guest1Totals.durationMinutes || 0, guest2Totals.durationMinutes || 0) || 45
                                );
                              return (
                                <div
                                  key={slot.value}
                                  onClick={() => {
                                    if (!slot.available) return;
                                    setAppointmentSchedule({
                                      ...appointmentSchedule,
                                      appointmentTime: slot.value,
                                    });
                                  }}
                                  style={{
                                    borderRadius: 12,
                                    padding: '0.6rem 0.7rem',
                                    textAlign: 'center',
                                    cursor: slot.available ? 'pointer' : 'not-allowed',
                                    border: isSelected
                                      ? '2px solid var(--primary-color)'
                                      : slot.available
                                      ? '1.5px solid #e2e8f0'
                                      : '1.5px dashed #dee2e6',
                                    backgroundColor: isSelected
                                      ? 'var(--primary-color-soft, #fff0f5)'
                                      : slot.available
                                      ? '#fff'
                                      : '#f8f9fa',
                                    opacity: slot.available ? 1 : 0.58,
                                  }}
                                >
                                  <div className="fw-bold small text-dark">{slot.label}</div>
                                  <div
                                    style={{ fontSize: '0.73rem' }}
                                    className={isSelected ? 'fw-semibold' : 'text-muted'}
                                  >
                                    {rangeLabel}
                                  </div>
                                  <span
                                    className={`badge rounded-pill mt-1 ${
                                      slot.available ? 'bg-success-subtle text-success' : 'bg-danger-subtle text-danger'
                                    }`}
                                    style={{ fontSize: '0.62rem' }}
                                  >
                                    {slot.available ? 'Open' : slot.reason === 'Passed' ? 'Passed' : 'Booked'}
                                  </span>
                                </div>
                              );
                            })}
                        </div>
                      )}
                      <div className="small text-muted mt-1">
                        Includes service duration + 15-minute sanitization &amp; turnover buffer.
                      </div>
                    </div>
                  </div>

                  {wizard?.showAppointmentNotes && (
                    <div className="mb-4">
                      <label className="form-label fw-semibold">
                        Guest {detailsGuestIndex + 1} Notes / Special Requests (Optional)
                      </label>
                      <textarea
                        className="form-control"
                        rows={3}
                        value={guests[detailsGuestIndex]?.notes || ''}
                        onChange={(e) =>
                          updateGuestDetailField(detailsGuestIndex, 'notes', e.target.value)
                        }
                      />
                    </div>
                  )}

                  <div className="d-flex justify-content-between">
                    <button
                      type="button"
                      className="btn btn-outline-secondary px-4 py-2"
                      onClick={handlePrevStep}
                    >
                      ← {detailsGuestIndex > 0 ? 'Back to Guest 1 Details' : 'Back'}
                    </button>
                    <button
                      type="submit"
                      className="btn text-white fw-bold px-5 py-2"
                      style={{ backgroundColor: 'var(--primary-color)' }}
                      disabled={submitting || isDateClosed}
                    >
                      {submitting
                        ? 'Submitting...'
                        : detailsGuestIndex + 1 < guests.length
                        ? `Next: Guest ${detailsGuestIndex + 2} Details →`
                        : guests.length > 1
                        ? `Confirm Group Booking ($${combinedTotals.grandTotal.toFixed(2)})`
                        : `Confirm Booking ($${guest1Totals.subtotal.toFixed(2)})`}
                    </button>
                  </div>
                </form>
              )}
            </div>
          </div>
        </div>
      </div>
    </main>
  );
}
