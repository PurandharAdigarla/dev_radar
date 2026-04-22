// Minimal EventSource mock for tests. Exposes .__emit(name, payload) so tests
// can deterministically trigger events.

type Listener = (ev: MessageEvent<string>) => void;

export class MockEventSource {
  static instances: MockEventSource[] = [];

  readonly url: string;
  readyState = 0; // CONNECTING
  onopen: ((ev: Event) => void) | null = null;
  onerror: ((ev: Event) => void) | null = null;
  onmessage: Listener | null = null;
  private listeners = new Map<string, Set<Listener>>();
  closed = false;

  constructor(url: string) {
    this.url = url;
    MockEventSource.instances.push(this);
    queueMicrotask(() => {
      if (!this.closed) {
        this.readyState = 1;
        this.onopen?.(new Event("open"));
      }
    });
  }

  addEventListener(name: string, listener: Listener) {
    let set = this.listeners.get(name);
    if (!set) { set = new Set(); this.listeners.set(name, set); }
    set.add(listener);
  }

  removeEventListener(name: string, listener: Listener) {
    this.listeners.get(name)?.delete(listener);
  }

  close() {
    this.closed = true;
    this.readyState = 2;
  }

  /** Test helper: synthesize a server event. */
  __emit(name: string, data: unknown) {
    const ev = new MessageEvent(name, { data: JSON.stringify(data) });
    this.listeners.get(name)?.forEach((l) => l(ev));
    if (name === "message") this.onmessage?.(ev);
  }

  /** Test helper: synthesize an error event. */
  __error() {
    this.onerror?.(new Event("error"));
  }

  static reset() {
    MockEventSource.instances = [];
  }

  static last(): MockEventSource | undefined {
    return MockEventSource.instances[MockEventSource.instances.length - 1];
  }
}

export function installMockEventSource() {
  MockEventSource.reset();
  (globalThis as unknown as { EventSource: typeof MockEventSource }).EventSource =
    MockEventSource;
}
