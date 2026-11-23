import React, { useState, useEffect } from 'react';
import './CustomerHistoryDrawer.css';

/**
 * Reusable Customer Appointment History & Audit Log Drawer
 */
export default function CustomerHistoryDrawer({
  isOpen,
  onClose,
  customerId,
  customerName = '',
  customerEmail = '',
  tenantKey = 'default',
  authToken = null,
}) {
  const [loading, setLoading] = useState(true);
  const [historyData, setHistoryData] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!isOpen || !customerId) return;

    let isMounted = true;
    setLoading(true);
    setError(null);

    const token = authToken || localStorage.getItem('accessToken') || localStorage.getItem('token');
    const headers = {
      'X-Tenant-ID': tenantKey,
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    };

    fetch(`/api/admin/customers/${customerId}/history`, { headers })
      .then(async (res) => {
        if (!res.ok) throw new Error(`Failed to load history (${res.status})`);
        return res.json();
      })
      .then((data) => {
        if (isMounted) {
          setHistoryData(data);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (isMounted) {
          setError(err.message || 'Error loading customer history.');
          setLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, [isOpen, customerId, tenantKey, authToken]);

  if (!isOpen) return null;

  const reliability = historyData?.reliability || {};
  const totalBooked = reliability.totalBooked || 0;
  const totalConfirmed = reliability.totalConfirmed || 0;
  const totalCompleted = reliability.totalCompleted || 0;
  const totalCancelled = reliability.totalCancelled || 0;
  const totalNoShows = reliability.totalNoShows || 0;
  const hasHighNoShowRisk = reliability.hasHighNoShowRisk || totalNoShows >= 2;
  const activityLogs = historyData?.activityLogs || [];

  return (
    <div className="customer-drawer-backdrop" onClick={onClose}>
      <div className="customer-drawer-panel shadow-lg" onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="drawer-header p-4 border-bottom bg-light d-flex justify-content-between align-items-center">
          <div>
            <h5 className="fw-bold text-dark mb-1 d-flex align-items-center gap-2">
              <i className="bi bi-person-badge-fill text-pink" />
              {customerName || historyData?.customer?.name || 'Customer Profile'}
            </h5>
            <div className="text-muted small">
              {customerEmail || historyData?.customer?.email || 'No email on record'}
              <span className="badge bg-secondary ms-2 text-uppercase">{tenantKey}</span>
            </div>
          </div>
          <button type="button" className="btn-close" onClick={onClose} aria-label="Close" />
        </div>

        {/* Body */}
        <div className="drawer-body p-4 overflow-y-auto">
          {loading && (
            <div className="text-center py-5">
              <div className="spinner-border text-pink" role="status" />
              <p className="text-muted mt-2 mb-0">Loading customer history & audit logs...</p>
            </div>
          )}

          {error && (
            <div className="alert alert-danger py-3 px-3 rounded-3 small">
              <i className="bi bi-exclamation-triangle-fill me-2" />
              {error}
            </div>
          )}

          {!loading && !error && (
            <>
              {/* High No-Show Warning Alert */}
              {hasHighNoShowRisk && (
                <div className="alert alert-danger d-flex align-items-center gap-2 py-2 px-3 small fw-bold mb-3 rounded-3 shadow-sm">
                  <i className="bi bi-exclamation-octagon-fill fs-5 text-danger flex-shrink-0" />
                  <div>
                    ⚠️ High No-Show Risk ({totalNoShows} No-Shows Recorded)
                    <div className="fw-normal extra-small">
                      Customer has 2+ unexcused missed appointments. Collect a non-refundable deposit before scheduling.
                    </div>
                  </div>
                </div>
              )}

              {/* Reliability Summary Pills */}
              <div className="p-3 bg-light rounded-4 border mb-4">
                <div className="d-flex justify-content-between align-items-center mb-2">
                  <span className="small fw-bold text-secondary text-uppercase" style={{ letterSpacing: '0.05em' }}>
                    Client Reliability Summary
                  </span>
                  {hasHighNoShowRisk ? (
                    <span className="badge bg-danger">HIGH RISK</span>
                  ) : (
                    <span className="badge bg-success">GOOD STANDING</span>
                  )}
                </div>
                <div className="d-flex flex-wrap gap-2">
                  <span className="badge bg-white text-dark border px-3 py-2 fs-6 shadow-sm">
                    Booked: <strong>{totalBooked}</strong>
                  </span>
                  <span className="badge bg-success-subtle text-success border border-success-subtle px-3 py-2 fs-6">
                    Confirmed: <strong>{totalConfirmed}</strong>
                  </span>
                  <span className="badge bg-primary-subtle text-primary border border-primary-subtle px-3 py-2 fs-6">
                    Completed: <strong>{totalCompleted}</strong>
                  </span>
                  <span className="badge bg-danger-subtle text-danger border border-danger-subtle px-3 py-2 fs-6">
                    Cancelled: <strong>{totalCancelled}</strong>
                  </span>
                  <span className={`badge ${totalNoShows >= 2 ? 'bg-danger text-white' : 'badge-no-show'} px-3 py-2 fs-6 shadow-sm`}>
                    No-Show: <strong>{totalNoShows}</strong>
                  </span>
                </div>
              </div>

              {/* Chronological Audit Timeline */}
              <div className="d-flex justify-content-between align-items-center mb-3">
                <h6 className="fw-bold text-dark mb-0">
                  <i className="bi bi-clock-history text-pink me-1" />
                  Chronological Audit Log ({activityLogs.length})
                </h6>
                <span className="text-muted extra-small">Newest to oldest</span>
              </div>

              {activityLogs.length === 0 ? (
                <div className="text-center py-4 bg-light rounded-3 border">
                  <i className="bi bi-calendar-x text-muted fs-3 mb-2 d-block" />
                  <p className="text-muted small mb-0">No appointment activity logs found for this customer.</p>
                </div>
              ) : (
                <div className="timeline-container">
                  {activityLogs.map((log) => {
                    const action = (log.actionType || 'UPDATED').toUpperCase();
                    let dotClass = 'dot-booked';
                    let badgeClass = 'bg-primary';

                    if (action === 'CONFIRMED') {
                      dotClass = 'dot-confirmed';
                      badgeClass = 'bg-success';
                    } else if (action === 'COMPLETED') {
                      dotClass = 'dot-completed';
                      badgeClass = 'bg-info text-dark';
                    } else if (action === 'CANCELLED') {
                      dotClass = 'dot-cancelled';
                      badgeClass = 'bg-danger';
                    } else if (action === 'NO_SHOW') {
                      dotClass = 'dot-no_show';
                      badgeClass = 'badge-no-show';
                    }

                    const eventTimeStr = log.createdAt
                      ? new Date(log.createdAt).toLocaleString('en-US', {
                          month: 'short',
                          day: 'numeric',
                          year: 'numeric',
                          hour: 'numeric',
                          minute: '2-digit',
                        })
                      : 'N/A';

                    const apptTimeStr = log.appointmentTime
                      ? new Date(log.appointmentTime).toLocaleString('en-US', {
                          weekday: 'short',
                          month: 'short',
                          day: 'numeric',
                          hour: 'numeric',
                          minute: '2-digit',
                        })
                      : null;

                    return (
                      <div key={log.id} className="timeline-card">
                        <span className={`timeline-dot ${dotClass}`} />
                        <div className="card border-0 shadow-sm rounded-3 p-3 bg-white">
                          <div className="d-flex justify-content-between align-items-center mb-1">
                            <span className={`badge ${badgeClass} px-2 py-1 fw-bold`}>{action}</span>
                            <span className="text-muted extra-small">{eventTimeStr}</span>
                          </div>

                          {log.serviceName && (
                            <div className="fw-bold text-dark mt-1">
                              <i className="bi bi-scissors text-pink me-1" />
                              {log.serviceName}
                            </div>
                          )}

                          {apptTimeStr && (
                            <div className="text-muted small mt-1">
                              <i className="bi bi-clock me-1" />
                              Appointment: {apptTimeStr}
                            </div>
                          )}

                          {log.technicianName && (
                            <div className="text-muted small">
                              <i className="bi bi-person me-1" />
                              Technician: {log.technicianName}
                            </div>
                          )}

                          {log.notes && (
                            <div className="mt-2 p-2 bg-light rounded small border">
                              <strong>Reason / Notes:</strong> {log.notes}
                            </div>
                          )}

                          <div className="text-muted extra-small mt-2 pt-1 border-top">
                            <i className="bi bi-shield-check me-1" />
                            Logged by: <strong>{log.performedBy || 'SYSTEM'}</strong>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
