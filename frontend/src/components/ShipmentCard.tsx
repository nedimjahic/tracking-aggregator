import type { TrackingResult } from "../api/tracking";

interface ShipmentCardProps {
  result: TrackingResult;
}

const STATUS_LABELS: Record<string, string> = {
  PENDING: "Pending",
  PICKED_UP: "Picked Up",
  IN_TRANSIT: "In Transit",
  OUT_FOR_DELIVERY: "Out for Delivery",
  DELIVERED: "Delivered",
  FAILED_ATTEMPT: "Failed Attempt",
  RETURNED: "Returned",
  UNKNOWN: "Unknown",
};

const STATUS_COLORS: Record<string, string> = {
  PENDING: "#6b7280",
  PICKED_UP: "#3b82f6",
  IN_TRANSIT: "#f59e0b",
  OUT_FOR_DELIVERY: "#8b5cf6",
  DELIVERED: "#10b981",
  FAILED_ATTEMPT: "#ef4444",
  RETURNED: "#ef4444",
  UNKNOWN: "#6b7280",
};

export function ShipmentCard({ result }: ShipmentCardProps) {
  return (
    <div className="shipment-card">
      <div className="shipment-header">
        <span className="courier-badge">{result.courier}</span>
        <span
          className="status-badge"
          style={{ backgroundColor: STATUS_COLORS[result.status] || "#6b7280" }}
        >
          {STATUS_LABELS[result.status] || result.status}
        </span>
      </div>
      <div className="shipment-details">
        <div className="detail">
          <span className="label">Tracking Number</span>
          <span className="value">{result.trackingNumber}</span>
        </div>
        {result.estimatedDelivery && (
          <div className="detail">
            <span className="label">Estimated Delivery</span>
            <span className="value">{result.estimatedDelivery}</span>
          </div>
        )}
      </div>
    </div>
  );
}
