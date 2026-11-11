import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useTenant } from '../context/TenantContext';

const STEP_METADATA = {
  SERVICE: { title: 'Select Service', subtitle: 'Choose a treatment category and service' },
  STAFF: { title: 'Select Staff', subtitle: 'Choose an available technician for your visit' },
  STATION: { title: 'Station / Room', subtitle: 'Select your preferred seating or private suite' },
  DETAILS: { title: 'Details & Confirm', subtitle: 'Enter your contact details and preferred time' },
};

const SERVICE_CATEGORIES = [
  { id: 'manicures', title: 'Manicures', keywords: ['manicure'] },
  { id: 'pedicures', title: 'Pedicures & Combos', keywords: ['pedicure', 'combo'] },
  { id: 'kid-services', title: 'Kid Services (10 & Under)', keywords: ['kid', 'kids'] },
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
  { id: 'waxing', title: 'Waxing & Combos', keywords: ['wax', 'waxing'] },
  { id: 'facial', title: 'Facial Treatments (Babor®)', keywords: ['facial', 'babor'] },
  { id: 'specialty-services', title: 'Specialty Services', keywords: ['tinting', 'tint'] },
  { id: 'gift-packages', title: 'Gift Packages', keywords: ['rejuvenation', 'executive', 'package'] },
];

function buildStoreTimeSlots() {
  const slots = [];
  for (let hour = 10; hour <= 19; hour++) {
    const maxMinute = hour === 19 ? 0 : 45;
    for (let min = 0; min <= maxMinute; min += 15) {
      const hh = String(hour).padStart(2, '0');
      const mm = String(min).padStart(2, '0');
      const period = hour >= 12 ? 'PM' : 'AM';
      const hour12 = hour > 12 ? hour - 12 : hour === 0 ? 12 : hour;
      slots.push({ value: `${hh}:${mm}:00`, label: `${hour12}:${mm} ${period}` });
    }
  }
  return slots;
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

  // Wizard state: initialized blank by default
  const [selectedService, setSelectedService] = useState(null);
  const [isPreselectedLocked, setIsPreselectedLocked] = useState(false);
  const [selectedStaff, setSelectedStaff] = useState(null);
  const [selectedStation, setSelectedStation] = useState('Any Available Station');

  const [customerForm, setCustomerForm] = useState(() => ({
    customerName: '',
    customerPhone: '',
    customerEmail: '',
    appointmentDate: new Date().toISOString().split('T')[0],
    appointmentTime: '',
    notes: '',
  }));

  const [alertMessage, setAlertMessage] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [confirmedBooking, setConfirmedBooking] = useState(null);

  const timeSlots = useMemo(() => buildStoreTimeSlots(), []);
  const currentStepKey = activeSteps[currentStepIdx] || 'SERVICE';

  // Complete reset to clean/blank wizard session
  const resetToBlankWizard = useCallback(() => {
    setSelectedService(null);
    setIsPreselectedLocked(false);
    setSelectedStaff(null);
    setSelectedStation('Any Available Station');
    setCustomerForm({
      customerName: '',
      customerPhone: '',
      customerEmail: '',
      appointmentDate: new Date().toISOString().split('T')[0],
      appointmentTime: '',
      notes: '',
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

  // Fetch all services on mount
  useEffect(() => {
    fetch('/api/services')
      .then((res) => (res.ok ? res.json() : []))
      .then((data) => {
        const list = Array.isArray(data) ? data : [];
        setServices(list);

        // Preselection handling: if arrived from service.html?serviceId=XYZ
        if (preselectedServiceId) {
          const matched = list.find(
            (s) =>
              String(s.id) === String(preselectedServiceId) ||
              (s.name && s.name.toLowerCase() === preselectedServiceId.toLowerCase())
          );
          if (matched) {
            setSelectedService(matched);
            setIsPreselectedLocked(true);
          }
        }
      })
      .catch(() => setAlertMessage('Unable to load salon services.'));
  }, [preselectedServiceId]);

  // Dynamically fetch qualified staff when entering STAFF step
  useEffect(() => {
    if (currentStepKey !== 'STAFF') return;
    setLoadingStaff(true);
    const query = selectedService?.id
      ? `?serviceId=${encodeURIComponent(selectedService.id)}`
      : '';
    fetch(`/api/nail-techs${query}`)
      .then((res) => (res.ok ? res.json() : []))
      .then((data) => {
        const list = Array.isArray(data) ? data : [];
        setStaffList(list);
        if (
          selectedStaff &&
          !selectedStaff.isAnyoneAvailable &&
          !list.some((t) => t.id === selectedStaff.id)
        ) {
          setSelectedStaff(null);
        }
      })
      .catch(() => setAlertMessage('Unable to load available technicians.'))
      .finally(() => setLoadingStaff(false));
  }, [currentStepKey, selectedService]);

  // Categorize services, filtering down if locked to pre-selected service
  const categorizedServices = useMemo(() => {
    const sourceServices = isPreselectedLocked && selectedService
      ? [selectedService]
      : services;

    const enabledLower = (wizard?.enabledCategories || []).map((c) => c.toLowerCase().trim());
    const visibleCategories =
      enabledLower.length > 0 && !isPreselectedLocked
        ? SERVICE_CATEGORIES.filter(
            (cat) =>
              enabledLower.includes(cat.id.toLowerCase()) ||
              enabledLower.includes(cat.title.toLowerCase())
          )
        : SERVICE_CATEGORIES;

    return visibleCategories
      .map((cat) => {
        const items = sourceServices.filter((srv) => {
          const nameLower = (srv.name || '').toLowerCase();
          return cat.keywords.some((kw) => nameLower.includes(kw));
        });
        return { ...cat, items };
      })
      .filter((cat) => cat.items.length > 0);
  }, [services, selectedService, isPreselectedLocked, wizard?.enabledCategories]);

  const validateBeforeNext = (stepKey) => {
    if (stepKey === 'SERVICE' && !selectedService) {
      setAlertMessage('Please select a service before continuing.');
      return false;
    }
    if (stepKey === 'STAFF' && !selectedStaff) {
      setAlertMessage('Please choose a technician (or "Anyone Available") before continuing.');
      return false;
    }
    return true;
  };

  const handleNextStep = () => {
    setAlertMessage(null);
    if (!validateBeforeNext(currentStepKey)) return;
    if (currentStepIdx + 1 < activeSteps.length) {
      setCurrentStepIdx((prev) => prev + 1);
    }
  };

  const handlePrevStep = () => {
    setAlertMessage(null);
    if (currentStepIdx > 0) {
      setCurrentStepIdx((prev) => prev - 1);
    }
  };

  const handleSubmitBooking = async (e) => {
    e.preventDefault();
    setAlertMessage(null);

    if (!selectedService) {
      setAlertMessage('Please select a service.');
      return;
    }
    if (!selectedStaff) {
      setAlertMessage('Please select a technician.');
      return;
    }
    if (
      !customerForm.customerName.trim() ||
      !customerForm.customerPhone.trim() ||
      !customerForm.customerEmail.trim() ||
      !customerForm.appointmentDate ||
      !customerForm.appointmentTime
    ) {
      setAlertMessage('Please complete all required customer and appointment time fields.');
      return;
    }

    setSubmitting(true);
    const payload = {
      customerName: customerForm.customerName.trim(),
      customerPhone: customerForm.customerPhone.trim(),
      customerEmail: customerForm.customerEmail.trim(),
      notes: wizard?.showAppointmentNotes ? customerForm.notes.trim() : '',
      stationPreference:
        wizard?.enableStationSelection && activeSteps.includes('STATION')
          ? selectedStation
          : null,
      service: { id: selectedService.id },
      nailTech: selectedStaff.id ? { id: selectedStaff.id } : null,
      appointmentTime: `${customerForm.appointmentDate}T${customerForm.appointmentTime}`,
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
      setConfirmedBooking({ ...saved, submittedPayload: payload });
    } catch (err) {
      setAlertMessage(err.message || 'Network error while submitting your booking.');
    } finally {
      setSubmitting(false);
    }
  };

  if (confirmedBooking) {
    return (
      <div className="container my-5">
        <div className="card border-0 shadow-sm rounded-4 p-4 p-md-5 text-center mx-auto" style={{ maxWidth: 640 }}>
          <h3 className="fw-bold mb-2" style={{ color: 'var(--primary-color)' }}>
            Appointment Confirmed!
          </h3>
          <p className="text-muted mb-4">
            Thank you for booking with {salonName}. Reference #{confirmedBooking.id || 'NEW'}
          </p>
          <div className="bg-light rounded-3 p-3 text-start mb-4">
            <div className="d-flex justify-content-between py-1 border-bottom">
              <span className="text-muted">Service</span>
              <strong>{selectedService?.name} (${Number(selectedService?.price || 0).toFixed(2)})</strong>
            </div>
            <div className="d-flex justify-content-between py-1 border-bottom">
              <span className="text-muted">Technician</span>
              <strong>{selectedStaff?.name}</strong>
            </div>
            {confirmedBooking.submittedPayload?.stationPreference && (
              <div className="d-flex justify-content-between py-1 border-bottom">
                <span className="text-muted">Station / Room</span>
                <strong>{confirmedBooking.submittedPayload.stationPreference}</strong>
              </div>
            )}
            <div className="d-flex justify-content-between py-1">
              <span className="text-muted">Date & Time</span>
              <strong>{confirmedBooking.submittedPayload?.appointmentTime}</strong>
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

  return (
    <main className={`container my-5 flex-grow-1 layout-${layoutVariant}`}>
      <div className="row justify-content-center">
        <div className="col-lg-10 col-xl-9">
          <div
            className="card shadow-sm border-0 rounded-4"
            style={{
              borderTop: layoutVariant === 'blue-sky' || layoutVariant === 'luxe'
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

              {/* Locked pre-selection banner if arrived from service.html */}
              {isPreselectedLocked && selectedService && (
                <div className="alert alert-info d-flex justify-content-between align-items-center rounded-3 mb-4">
                  <div>
                    <i className="bi bi-lock-fill me-2" />
                    Booking pre-selected service: <strong>{selectedService.name}</strong> (${Number(selectedService.price).toFixed(2)})
                  </div>
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-primary"
                    onClick={() => {
                      setIsPreselectedLocked(false);
                      startBlankBooking();
                    }}
                  >
                    Change Service
                  </button>
                </div>
              )}

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

              {/* ==================== STEP: SERVICE ==================== */}
              {currentStepKey === 'SERVICE' && (
                <section>
                  <h5 className="fw-bold mb-3">
                    Step {currentStepIdx + 1}: {STEP_METADATA.SERVICE.title}
                  </h5>

                  <div className="d-flex flex-column gap-3 mb-4">
                    {categorizedServices.map((cat) => {
                      const isOpen = isPreselectedLocked || openCategoryId === cat.id;
                      return (
                        <div key={cat.id} className="card border rounded-4 shadow-sm">
                          <div
                            className="card-header bg-white border-0 p-3 d-flex justify-content-between align-items-center"
                            style={{ cursor: isPreselectedLocked ? 'default' : 'pointer' }}
                            onClick={() => {
                              if (!isPreselectedLocked) {
                                setOpenCategoryId(openCategoryId === cat.id ? null : cat.id);
                              }
                            }}
                          >
                            <div>
                              <h6 className="fw-bold mb-0">{cat.title}</h6>
                              <small className="text-muted">{cat.items.length} services</small>
                            </div>
                            {!isPreselectedLocked && (
                              <span className="fw-bold" style={{ color: 'var(--primary-color)' }}>
                                {isOpen ? '▲' : '▼'}
                              </span>
                            )}
                          </div>

                          {isOpen && (
                            <div className="card-body pt-0">
                              <div className="list-group list-group-flush">
                                {cat.items.map((srv) => {
                                  const isSelected = selectedService?.id === srv.id;
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
                                      onClick={() => {
                                        setSelectedService(srv);
                                      }}
                                    >
                                      <div>
                                        <div className="fw-bold text-dark">{srv.name}</div>
                                        <small className="text-muted">
                                          ${Number(srv.price || 0).toFixed(2)} •{' '}
                                          {srv.durationMinutes || 45} mins
                                        </small>
                                      </div>
                                      {isSelected && (
                                        <span
                                          className="badge rounded-pill"
                                          style={{ backgroundColor: 'var(--primary-color)' }}
                                        >
                                          {isPreselectedLocked ? 'Pre-Selected' : 'Selected'}
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

                  {selectedService && (
                    <div
                      className="position-fixed bottom-0 start-50 translate-middle-x mb-4 px-4 py-3 bg-white rounded-pill shadow-lg d-flex align-items-center gap-3"
                      style={{ zIndex: 1050, border: '1px solid var(--primary-color)' }}
                    >
                      <div>
                        <div className="fw-bold small text-dark">{selectedService.name}</div>
                        <div className="small fw-semibold" style={{ color: 'var(--primary-color)' }}>
                          ${Number(selectedService.price).toFixed(2)} •{' '}
                          {selectedService.durationMinutes || 45} mins
                        </div>
                      </div>
                      <button
                        type="button"
                        className="btn rounded-pill px-4 py-2 fw-bold text-white"
                        style={{ backgroundColor: 'var(--primary-color)' }}
                        onClick={handleNextStep}
                      >
                        {nextStepLabel} →
                      </button>
                    </div>
                  )}
                </section>
              )}

              {/* ==================== STEP: STAFF ==================== */}
              {currentStepKey === 'STAFF' && (
                <section>
                  <h5 className="fw-bold mb-1">
                    Step {currentStepIdx + 1}: {STEP_METADATA.STAFF.title}
                  </h5>
                  <p className="text-muted small mb-3">
                    Select an available technician for your appointment
                  </p>

                  {loadingStaff ? (
                    <div className="text-center py-4 text-muted">Loading available staff...</div>
                  ) : (
                    <div className="row g-3 mb-4">
                      <div className="col-md-6">
                        <div
                          className="card p-3 h-100"
                          style={{
                            cursor: 'pointer',
                            borderColor: selectedStaff?.isAnyoneAvailable
                              ? 'var(--primary-color)'
                              : '#dee2e6',
                            backgroundColor: selectedStaff?.isAnyoneAvailable
                              ? 'var(--primary-color-soft)'
                              : '#fff',
                          }}
                          onClick={() =>
                            setSelectedStaff({
                              id: null,
                              name: 'Anyone Available',
                              isAnyoneAvailable: true,
                            })
                          }
                        >
                          <div className="fw-bold text-dark">Anyone Available</div>
                          <small className="text-muted">No preference</small>
                        </div>
                      </div>

                      {staffList.map((tech) => {
                        const displayName = tech.name || tech.username || 'Nail Technician';
                        const isSelected = selectedStaff?.id === tech.id;
                        return (
                          <div key={tech.id} className="col-md-6">
                            <div
                              className="card p-3 h-100 d-flex flex-row align-items-center justify-content-between"
                              style={{
                                cursor: 'pointer',
                                borderColor: isSelected ? 'var(--primary-color)' : '#dee2e6',
                                backgroundColor: isSelected ? 'var(--primary-color-soft)' : '#fff',
                              }}
                              onClick={() =>
                                setSelectedStaff({
                                  id: tech.id,
                                  name: displayName,
                                  isAnyoneAvailable: false,
                                })
                              }
                            >
                              <span className="fw-bold text-dark">{displayName}</span>
                              {isSelected && (
                                <span style={{ color: 'var(--primary-color)', fontWeight: 700 }}>
                                  ✓
                                </span>
                              )}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}

                  <div className="d-flex justify-content-between">
                    {currentStepIdx > 0 && (
                      <button
                        type="button"
                        className="btn btn-outline-secondary px-4 py-2"
                        onClick={handlePrevStep}
                      >
                        ← Back
                      </button>
                    )}
                    <button
                      type="button"
                      className="btn text-white fw-semibold px-4 py-2 ms-auto"
                      style={{ backgroundColor: 'var(--primary-color)' }}
                      disabled={!selectedStaff}
                      onClick={handleNextStep}
                    >
                      Next: {nextStepLabel} →
                    </button>
                  </div>
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

              {/* ==================== FINAL STEP: DETAILS & CONFIRM ==================== */}
              {currentStepKey === 'DETAILS' && (
                <form onSubmit={handleSubmitBooking}>
                  <h5 className="fw-bold mb-3">
                    Step {currentStepIdx + 1}: {STEP_METADATA.DETAILS.title}
                  </h5>

                  <div className="card bg-light border-0 rounded-3 p-3 mb-4">
                    <div className="row g-2">
                      <div className="col-md-4">
                        <small className="text-muted d-block">Service</small>
                        <strong>{selectedService?.name || '—'}</strong>
                      </div>
                      <div className="col-md-4">
                        <small className="text-muted d-block">Technician</small>
                        <strong>{selectedStaff?.name || '—'}</strong>
                      </div>
                      {wizard?.enableStationSelection && activeSteps.includes('STATION') && (
                        <div className="col-md-4">
                          <small className="text-muted d-block">Station / Room</small>
                          <strong>{selectedStation}</strong>
                        </div>
                      )}
                    </div>
                  </div>

                  <div className="row g-3 mb-3">
                    <div className="col-md-6">
                      <label className="form-label fw-semibold">Full Name *</label>
                      <input
                        type="text"
                        className="form-control"
                        required
                        value={customerForm.customerName}
                        onChange={(e) =>
                          setCustomerForm({ ...customerForm, customerName: e.target.value })
                        }
                      />
                    </div>
                    <div className="col-md-6">
                      <label className="form-label fw-semibold">Phone Number *</label>
                      <input
                        type="tel"
                        className="form-control"
                        required
                        value={customerForm.customerPhone}
                        onChange={(e) =>
                          setCustomerForm({ ...customerForm, customerPhone: e.target.value })
                        }
                      />
                    </div>
                  </div>

                  <div className="mb-3">
                    <label className="form-label fw-semibold">Email Address *</label>
                    <input
                      type="email"
                      className="form-control"
                      required
                      value={customerForm.customerEmail}
                      onChange={(e) =>
                        setCustomerForm({ ...customerForm, customerEmail: e.target.value })
                      }
                    />
                  </div>

                  <div className="row g-3 mb-3">
                    <div className="col-md-6">
                      <label className="form-label fw-semibold">Appointment Date *</label>
                      <input
                        type="date"
                        className="form-control"
                        required
                        min={new Date().toISOString().split('T')[0]}
                        value={customerForm.appointmentDate}
                        onChange={(e) =>
                          setCustomerForm({ ...customerForm, appointmentDate: e.target.value })
                        }
                      />
                    </div>
                    <div className="col-md-6">
                      <label className="form-label fw-semibold">
                        Time Slot (10:00 AM – 7:00 PM) *
                      </label>
                      <select
                        className="form-select"
                        required
                        value={customerForm.appointmentTime}
                        onChange={(e) =>
                          setCustomerForm({ ...customerForm, appointmentTime: e.target.value })
                        }
                      >
                        <option value="">-- Choose a Time --</option>
                        {timeSlots.map((slot) => (
                          <option key={slot.value} value={slot.value}>
                            {slot.label}
                          </option>
                        ))}
                      </select>
                    </div>
                  </div>

                  {wizard?.showAppointmentNotes && (
                    <div className="mb-4">
                      <label className="form-label fw-semibold">
                        Appointment Notes / Special Requests (Optional)
                      </label>
                      <textarea
                        className="form-control"
                        rows={3}
                        value={customerForm.notes}
                        onChange={(e) =>
                          setCustomerForm({ ...customerForm, notes: e.target.value })
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
                      ← Back
                    </button>
                    <button
                      type="submit"
                      className="btn text-white fw-bold px-5 py-2"
                      style={{ backgroundColor: 'var(--primary-color)' }}
                      disabled={submitting}
                    >
                      {submitting ? 'Submitting...' : 'Confirm Booking'}
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
