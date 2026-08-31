import type { TrackingEvent } from "../api/tracking";

interface TrackingTimelineProps {
  events: TrackingEvent[];
}

function formatDate(iso: string): string {
  const date = new Date(iso);
  return date.toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function TrackingTimeline({ events }: TrackingTimelineProps) {
  if (events.length === 0) {
    return <p className="no-events">No tracking events available</p>;
  }

  return (
    <div className="timeline">
      <h3>Tracking History</h3>
      <ul className="timeline-list">
        {events.map((event, index) => (
          <li key={index} className="timeline-item">
            <div className="timeline-dot" />
            <div className="timeline-content">
              <span className="timeline-date">{formatDate(event.timestamp)}</span>
              <span className="timeline-description">{event.description}</span>
              {event.location && (
                <span className="timeline-location">{event.location}</span>
              )}
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
