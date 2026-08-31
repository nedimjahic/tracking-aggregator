import { useState } from "react";

interface TrackingInputProps {
  onTrack: (trackingNumber: string) => void;
  loading: boolean;
}

export function TrackingInput({ onTrack, loading }: TrackingInputProps) {
  const [value, setValue] = useState("");

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = value.trim();
    if (trimmed) {
      onTrack(trimmed);
    }
  }

  return (
    <form className="tracking-input" onSubmit={handleSubmit}>
      <input
        type="text"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="Enter tracking number (e.g. 1Z999AA10123456784)"
        disabled={loading}
      />
      <button type="submit" disabled={loading || !value.trim()}>
        {loading ? "Tracking..." : "Track"}
      </button>
    </form>
  );
}
