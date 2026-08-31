# Tracking Aggregator

A shipment tracking aggregator that auto-detects the courier from a tracking number and displays shipment status and tracking history. Built with Quarkus
(backend) and React (frontend).

## Architecture

```
tracking-aggregator/
├── api/                  # OpenAPI spec (single source of truth)
│   └── openapi.yml
├── backend/              # Quarkus (Java 25, Maven)
├── frontend/             # React + TypeScript (Vite)
└── docker-compose.yml    # PostgreSQL
```

### API-First Workflow

The API contract is defined in `api/openapi.yml`. Both backend and frontend code is generated from this spec:

- **Backend**: `openapi-generator-maven-plugin` generates JAX-RS interfaces and model DTOs into `target/generated-sources/`. The hand-written code implements
  these interfaces.
- **Frontend**: [Orval](https://orval.dev/) generates TypeScript types and a fetch-based API client into `src/api/`.

When the spec changes:

1. Edit `api/openapi.yml`
2. Backend — `mvn compile` regenerates Java interfaces; compiler errors show what to update
3. Frontend — `npm run generate-api` regenerates TypeScript types; type errors show what to update

### Backend

- **TrackingResourceImpl** — REST endpoint implementing the generated `DefaultApi` interface
- **TrackingService** — orchestrates courier detection and tracking, logs queries to the database
- **CourierDetectorService** — identifies the courier from tracking number format using regex patterns
- **CourierClient** — interface implemented by each courier (DHL, GLS, DPD, UPS, FedEx, USPS). All clients currently return stub data; each can be swapped for
  real API calls independently.

### Frontend

- **TrackingInput** — search form for entering a tracking number
- **ShipmentCard** — displays courier, status badge, and estimated delivery
- **TrackingTimeline** — vertical timeline of tracking events

### Database

PostgreSQL stores tracking queries for caching and analytics. Hibernate ORM with Panache manages the schema automatically in dev mode.

## Supported Couriers

| Courier | Example Tracking Number  |
|---------|--------------------------|
| UPS     | `1Z999AA10123456784`     |
| FedEx   | `123456789012`           |
| USPS    | `9400111899223100001234` |
| DHL     | `1234567890`             |
| GLS     | `12345678901`            |
| DPD     | `01234567890123`         |

## Running Locally

### Prerequisites

- Java 25
- Maven 3.8+
- Node.js 20+
- Docker

### 1. Start PostgreSQL

```sh
docker-compose up -d
```

### 2. Start the backend

```sh
cd backend
mvn quarkus:dev
```

The API will be available at `http://localhost:8080`. Swagger UI is at `http://localhost:8080/q/swagger-ui`.

### 3. Start the frontend

```sh
cd frontend
npm install
npm run generate-api
npm run dev
```

The UI will be available at `http://localhost:5173`. API requests are proxied to the backend automatically.

### 4. Try it out

Enter one of the example tracking numbers from the table above into the search field. The app will detect the courier and display mock tracking data.
