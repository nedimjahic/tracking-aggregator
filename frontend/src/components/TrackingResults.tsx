import type { TrackingResult, TrackingError } from "../api/tracking";
import { ShipmentCard } from "./ShipmentCard";
import { TrackingTimeline } from "./TrackingTimeline";

interface TrackingResultsProps {
  result: TrackingResult | null;
  error: TrackingError | null;
  loading: boolean;
}

export function TrackingResults({ result, error, loading }: TrackingResultsProps) {
  if (loading) {
    return <div className="loading">Searching couriers...</div>;
  }

  if (error) {
    return (
      <div className="error-card">
        <h3>Tracking failed</h3>
        <p>{error.message}</p>
        <span className="error-code">{error.error}</span>
      </div>
    );
  }

  if (!result) {
    return null;
  }

  return (
    <div className="tracking-results">
      <ShipmentCard result={result} />
      <TrackingTimeline events={result.events} />
    </div>
  );
}
