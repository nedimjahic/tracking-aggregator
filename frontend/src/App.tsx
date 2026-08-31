import { useState } from "react";
import { TrackingInput } from "./components/TrackingInput";
import { TrackingResults } from "./components/TrackingResults";
import { trackShipment } from "./api/tracking";
import type { TrackingResult, TrackingError } from "./api/tracking";
import "./App.css";

function App() {
  const [result, setResult] = useState<TrackingResult | null>(null);
  const [error, setError] = useState<TrackingError | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleTrack(trackingNumber: string) {
    setLoading(true);
    setError(null);
    setResult(null);

    try {
      const response = await trackShipment(trackingNumber);
      if (response.status === 200) {
        setResult(response.data as TrackingResult);
      } else {
        setError(response.data as TrackingError);
      }
    } catch {
      setError({
        error: "COURIER_NOT_DETECTED",
        message: "Failed to connect to the server. Is the backend running?",
        trackingNumber,
      });
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="app">
      <header className="app-header">
        <h1>Tracking Aggregator</h1>
        <p>Enter a tracking number to find your shipment across all major couriers</p>
      </header>
      <main>
        <TrackingInput onTrack={handleTrack} loading={loading} />
        <TrackingResults result={result} error={error} loading={loading} />
      </main>
    </div>
  );
}

export default App;
