let sequence = 0;
/** Numeric IDs for local mock records; Servlet assigns authoritative IDs in API mode. */
export function createMockId() { sequence = Math.max(Date.now() * 1000, sequence + 1); return sequence; }
