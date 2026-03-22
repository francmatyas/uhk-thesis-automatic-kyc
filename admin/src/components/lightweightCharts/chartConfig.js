// chartConfig.js - Centralized configuration for chart colors and styles

export const chartConfig = {
  // Consolidation Areas
  consolidationAreas: {
    fillColor: "rgba(255, 255, 0, 0.05)", // Yellow with transparency
    borderColor: "rgba(255, 255, 0, 0.7)",
    borderWidth: 1,
  },

  // Cumulative Delta
  volume: {
    positiveColor: "rgba(34, 197, 94, 0.7)", // Green
    negativeColor: "rgba(239, 68, 68, 0.7)", // Red
    minBarHeight: 10,
  },

  // VWAP Consolidations
  vwapConsolidations: {
    mainLine: {
      color: "rgba(255, 235, 59, 0.9)", // Yellow
      width: 2,
    },
    upperBand1: {
      color: "rgba(74, 222, 128, 0.8)", // Green
      width: 1,
    },
    upperBand2: {
      color: "rgba(134, 239, 172, 0.7)", // Light green
      width: 1,
    },
    lowerBand1: {
      color: "rgba(248, 113, 113, 0.8)", // Red
      width: 1,
    },
    lowerBand2: {
      color: "rgba(252, 165, 165, 0.7)", // Light red
      width: 1,
    },
    showBands: true,
  },

  // VWAP Daily
  vwapDaily: {
    useRainbow: false, // If false, uses defaultColor for all days
    defaultColor: "rgba(0, 112, 255, 0.9)", // Purple
    rainbowColors: [
      "rgba(167, 139, 250, 0.9)", // Purple
      "rgba(59, 130, 246, 0.9)", // Blue
      "rgba(34, 197, 94, 0.9)", // Green
      "rgba(234, 179, 8, 0.9)", // Yellow
      "rgba(249, 115, 22, 0.9)", // Orange
      "rgba(239, 68, 68, 0.9)", // Red
      "rgba(236, 72, 153, 0.9)", // Pink
    ],
    mainLine: {
      width: 2,
    },
    bands: {
      upperBand1: {
        color: "rgba(128, 255, 0, 0.9)",
        width: 1,
      },
      lowerBand1: {
        color: "rgba(128, 255, 0, 0.9)",
        width: 1,
      },
      upperBand2: {
        color: "rgba(205, 127, 50, 0.9)",
        width: 1,
      },
      lowerBand2: {
        color: "rgba(205, 127, 50, 0.9)",
        width: 1,
      },
    },
    showBands: true,
    showDayLabels: false,
  },

  // Volume Profile Lines (POC/VAH/VAL)
  volumeProfile: {
    daily: {
      poc: { color: "rgba(255, 235, 59, 0.9)", width: 2 },
      vah: { color: "rgba(59, 130, 246, 0.9)", width: 1 },
      val: { color: "rgba(239, 68, 68, 0.9)", width: 1 },
    },
    consolidation: {
      poc: { color: "rgba(255, 235, 59, 0.7)", width: 2, dash: [5, 4] },
      vah: { color: "rgba(59, 130, 246, 0.7)", width: 1, dash: [5, 4] },
      val: { color: "rgba(239, 68, 68, 0.7)", width: 1, dash: [5, 4] },
    },
  },

  // Data Overlay
  dataOverlay: {
    background: "rgba(26, 26, 26, 0.8)",
    textColor: "#ffffff",
    labelColor: "#888888", // Neutral gray for labels
    fontSize: "12px",
    padding: "4px 6px",
    position: {
      top: "0px",
      left: "0px",
    },
    volume: {
      positiveColor: "#22c55e", // Green (buy dominant)
      negativeColor: "#ef4444", // Red (sell dominant)
    },
    vwap: {
      mainColor: "#ffeb3b", // Yellow
      upperBand1Color: "#80FF00", // Green
      upperBand2Color: "#CD7F32", // Light green
      lowerBand1Color: "#80FF00", // Red
      lowerBand2Color: "#CD7F32", // Light red
      dailyColor: "#0070FF", // Purple
    },
  },

  // Chart Layout
  chart: {
    background: "#1a1a1a",
    textColor: "#ffffff",
    grid: {
      vertLines: "#404040",
      horzLines: "#404040",
    },
    timeScale: {
      borderColor: "#485c7b",
      timeVisible: true,
    },
    priceScale: {
      borderColor: "#485c7b",
    },
  },

  // Candlesticks
  candlestick: {
    upColor: "#26a69a",
    downColor: "#ef5350",
    borderVisible: false,
    wickUpColor: "#26a69a",
    wickDownColor: "#ef5350",
  },
};

// Helper function to get a rainbow color by index
export function getRainbowColor(index) {
  const colors = chartConfig.vwapDaily.rainbowColors;
  return colors[index % colors.length];
}

// Helper function to format volume with K suffix
export function formatVolume(volume) {
  return volume >= 1000 ? `${(volume / 1000).toFixed(2)}K` : volume.toFixed(0);
}
