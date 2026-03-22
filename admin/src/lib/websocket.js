const RAW_API_URL =
  import.meta.env.VITE_WS_URL ??
  import.meta.env.VITE_API_URL ??
  (typeof window !== "undefined"
    ? `${window.location.protocol}//${window.location.host}`
    : "http://localhost:4000");

function buildWsUrl(base, path = "/ws") {
  const url = new URL(base);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  url.pathname = `${url.pathname.replace(/\/+$/, "")}${path}`;
  return url.toString();
}

export class SimulationWebSocket {
  constructor() {
    this.ws = null;
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 5;
    this.reconnectDelay = 1000;
    this.connectionPromise = null;
  }

  connect(simulationId, onMessage, onError, onClose) {
    // if already connecting/open, return existing promise or resolve immediately
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      return Promise.resolve();
    }
    
    if (this.ws && this.ws.readyState === WebSocket.CONNECTING) {
      return this.connectionPromise;
    }

    this.connectionPromise = new Promise((resolve, reject) => {
      let wsUrl;
      try {
        wsUrl = buildWsUrl(RAW_API_URL, "/ws");
        console.log("Connecting to WebSocket:", wsUrl);
      } catch (err) {
        console.error("Invalid base URL for WebSocket:", RAW_API_URL);
        onError?.(err);
        reject(err);
        return;
      }

      try {
        this.ws = new WebSocket(wsUrl);

        this.ws.onopen = () => {
          this.reconnectAttempts = 0;
          this.ws.send(JSON.stringify({ simulationId }));
          resolve(); // Resolve the promise when connection is established
        };

        this.ws.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data);
            if (data && data.error) {
              onError?.(data.error);
            } else {
              onMessage?.(data);
            }
          } catch (err) {
            console.error("WS parse error:", err);
            onError?.(err);
          }
        };

        this.ws.onclose = (event) => {
          this.connectionPromise = null; // Reset connection promise
          onClose?.(event);

          const abnormal = !event.wasClean && event.code !== 1000;
          if (abnormal && this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts += 1;
            const delay = Math.min(
              this.reconnectDelay * 2 ** (this.reconnectAttempts - 1),
              10000
            );
            setTimeout(() => {
              this.connect(simulationId, onMessage, onError, onClose);
            }, delay);
          }
        };

        this.ws.onerror = (error) => {
          this.connectionPromise = null; // Reset connection promise
          onError?.(error);
          reject(error);
        };
      } catch (error) {
        console.error("Failed to create WebSocket:", error);
        onError?.(error);
        reject(error);
      }
    });

    return this.connectionPromise;
  }

  disconnect() {
    this.connectionPromise = null; // Reset connection promise
    if (this.ws) {
      try {
        this.ws.close(1000, "Client disconnecting");
      } finally {
        this.ws = null;
      }
    }
  }

  isConnected() {
    return !!this.ws && this.ws.readyState === WebSocket.OPEN;
  }
}
