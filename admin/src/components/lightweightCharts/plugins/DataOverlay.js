// DataOverlay.js - Custom overlay to display hovered bar data
import { chartConfig } from "../chartConfig.js";

export class DataOverlay {
  constructor(chart, series, options = {}) {
    this._chart = chart;
    this._series = series;
    this._container = null;
    this._visible = false;

    // Data sources
    this._candleData = []; // Store candlestick data for manual lookup
    this._volumeData = []; // Store volume data separately
    this._cumulativeDeltaData = [];
    this._vwapConsolidationsData = [];
    this._vwapDailyData = [];

    this._createContainer();
    this._subscribeToCrosshair();
  }

  _createContainer() {
    const chartContainer = this._chart.chartElement();
    const config = chartConfig.dataOverlay;

    // Ensure chart container has relative positioning
    const existingPosition = window.getComputedStyle(chartContainer).position;
    if (existingPosition === "static") {
      chartContainer.style.position = "relative";
    }

    this._container = document.createElement("div");
    this._container.style.cssText = `
      position: absolute;
      top: ${config.position.top};
      left: ${config.position.left};
      padding: ${config.padding};
      background: ${config.background};
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
      font-size: ${config.fontSize};
      color: ${config.textColor};
      pointer-events: none;
      z-index: 40;
      display: none;
      min-width: 200px;
    `;

    chartContainer.appendChild(this._container);
  }

  _subscribeToCrosshair() {
    this._chart.subscribeCrosshairMove((param) => {
      if (!param.time || !param.seriesData || !param.seriesData.size) {
        // No crosshair - show rightmost visible bar
        this._showRightmostBar();
        return;
      }

      const data = param.seriesData.get(this._series);
      if (!data) {
        this._showRightmostBar();
        return;
      }

      this._show(param.time, data);
    });
  }

  _showRightmostBar() {
    const timeScale = this._chart.timeScale();
    const visibleLogicalRange = timeScale.getVisibleLogicalRange();
    
    if (!visibleLogicalRange) {
      this._hide();
      return;
    }
    
    // Get the time at the rightmost visible bar
    const rightmostLogicalIndex = Math.floor(visibleLogicalRange.to);
    const rightmostTime = timeScale.coordinateToTime(timeScale.logicalToCoordinate(rightmostLogicalIndex));
    
    if (!rightmostTime) {
      this._hide();
      return;
    }

    // Find the candle data at this time
    const data = this._findCandleDataByTime(rightmostTime);
    if (!data) {
      this._hide();
      return;
    }

    this._show(rightmostTime, data);
  }

  _show(time, candleData) {
    this._visible = true;
    this._container.style.display = "block";

    const config = chartConfig.dataOverlay;
    let rows = [];

    const ohlcColor =
      candleData.close >= candleData.open ? "#22c55e" : "#ef4444";

    // Row 1: OHLC + Volume
    const ohlcParts = [];
    ohlcParts.push(
      `<span style="color: ${
        config.labelColor
      };">O:</span> <span style="color: ${ohlcColor};">${candleData.open?.toLocaleString(
        undefined,
        { maximumFractionDigits: 2 }
      )}</span>`
    );
    ohlcParts.push(
      `<span style="color: ${
        config.labelColor
      };">H:</span> <span style="color: ${ohlcColor};">${candleData.high?.toLocaleString(
        undefined,
        { maximumFractionDigits: 2 }
      )}</span>`
    );
    ohlcParts.push(
      `<span style="color: ${
        config.labelColor
      };">L:</span> <span style="color: ${ohlcColor};">${candleData.low?.toLocaleString(
        undefined,
        { maximumFractionDigits: 2 }
      )}</span>`
    );
    ohlcParts.push(
      `<span style="color: ${
        config.labelColor
      };">C:</span> <span style="color: ${ohlcColor};">${candleData.close?.toLocaleString(
        undefined,
        { maximumFractionDigits: 2 }
      )}</span>`
    );
    rows.push(ohlcParts.join(" &nbsp; "));

    // Volume Data - Look up by time
    const volumeData = this._findDataByTime(this._volumeData, time);
    if (volumeData && volumeData.tradeCount !== undefined) {
      const volumeParts = [];
      const volumeColor =
        (volumeData.buyTradeCount || 0) > (volumeData.sellTradeCount || 0)
          ? config.volume.positiveColor
          : config.volume.negativeColor;
      const formattedVol =
        volumeData.tradeCount >= 1000
          ? `${(volumeData.tradeCount / 1000).toLocaleString(undefined, {
              maximumFractionDigits: 2,
            })}K`
          : volumeData.tradeCount.toLocaleString(undefined, {
              maximumFractionDigits: 0,
            });
      volumeParts.push(
        `<span style="color: ${config.labelColor};">Vol:</span> <span style="color: ${volumeColor};">${formattedVol}</span>`
      );
      rows.push(volumeParts.join(" &nbsp; "));
    }

    // Row 2: VWAP Consolidations
    const vwapConsData = this._findVwapConsolidationByTime(time);
    if (vwapConsData) {
      let vwapConsParts = [];
      vwapConsParts.push(
        `<span style="color: ${
          config.labelColor
        };">Consolidation VWAP:</span> <span style="color: ${
          config.vwap.mainColor
        };">${
          vwapConsData.main?.toLocaleString(undefined, {
            maximumFractionDigits: 2,
          }) || "N/A"
        }</span>`
      );
      if (vwapConsData.upperBand1) {
        vwapConsParts.push(
          `<span style="color: ${
            config.labelColor
          };">↑1:</span> <span style="color: ${
            config.vwap.upperBand1Color
          };">${vwapConsData.upperBand1.toLocaleString(undefined, {
            maximumFractionDigits: 2,
          })}</span>`
        );
      }
      if (vwapConsData.upperBand2) {
        vwapConsParts.push(
          `<span style="color: ${
            config.labelColor
          };">↑2:</span> <span style="color: ${
            config.vwap.upperBand2Color
          };">${vwapConsData.upperBand2.toLocaleString(undefined, {
            maximumFractionDigits: 2,
          })}</span>`
        );
      }
      if (vwapConsData.lowerBand1) {
        vwapConsParts.push(
          `<span style="color: ${
            config.labelColor
          };">↓1:</span> <span style="color: ${
            config.vwap.lowerBand1Color
          };">${vwapConsData.lowerBand1.toLocaleString(undefined, {
            maximumFractionDigits: 2,
          })}</span>`
        );
      }
      if (vwapConsData.lowerBand2) {
        vwapConsParts.push(
          `<span style="color: ${
            config.labelColor
          };">↓2:</span> <span style="color: ${
            config.vwap.lowerBand2Color
          };">${vwapConsData.lowerBand2.toLocaleString(undefined, {
            maximumFractionDigits: 2,
          })}</span>`
        );
      }
      rows.push(vwapConsParts.join(" &nbsp; "));
    }

    // Row 3: VWAP Daily
    const vwapDailyData = this._findVwapDailyByTime(time);
    if (vwapDailyData) {
      let vwapDailyParts = [];
      vwapDailyParts.push(
        `<span style="color: ${
          config.labelColor
        };">Daily VWAP:</span> <span style="color: ${
          config.vwap.dailyColor
        };">${
          vwapDailyData.main?.toLocaleString(undefined, {
            maximumFractionDigits: 2,
          }) || "N/A"
        }</span>`
      );
      if (vwapDailyData.upperBand1) {
        vwapDailyParts.push(
          `<span style="color: ${
            config.labelColor
          };">↑1:</span> <span style="color: ${
            config.vwap.upperBand1Color
          };">${vwapDailyData.upperBand1.toLocaleString(undefined, {
            maximumFractionDigits: 2,
          })}</span>`
        );
      }
      if (vwapDailyData.lowerBand1) {
        vwapDailyParts.push(
          `<span style="color: ${
            config.labelColor
          };">↓1:</span> <span style="color: ${
            config.vwap.lowerBand1Color
          };">${vwapDailyData.lowerBand1.toLocaleString(undefined, {
            maximumFractionDigits: 2,
          })}</span>`
        );
      }
      if (vwapDailyData.upperBand2) {
        vwapDailyParts.push(
          `<span style="color: ${
            config.labelColor
          };">↑2:</span> <span style="color: ${
            config.vwap.upperBand2Color
          };">${vwapDailyData.upperBand2.toLocaleString(undefined, {
            maximumFractionDigits: 2,
          })}</span>`
        );
      }
      if (vwapDailyData.lowerBand2) {
        vwapDailyParts.push(
          `<span style="color: ${
            config.labelColor
          };">↓2:</span> <span style="color: ${
            config.vwap.lowerBand2Color
          };">${vwapDailyData.lowerBand2.toLocaleString(undefined, {
            maximumFractionDigits: 2,
          })}</span>`
        );
      }
      rows.push(vwapDailyParts.join(" &nbsp; "));
    }

    this._container.innerHTML = rows.join("<br/>");
  }

  _hide() {
    if (this._visible) {
      this._container.style.display = "none";
      this._visible = false;
    }
  }

  _findDataByTime(dataArray, time) {
    return dataArray.find((item) => item.time === time);
  }

  _findVwapConsolidationByTime(time) {
    for (const consolidation of this._vwapConsolidationsData) {
      if (!consolidation.vwap || !Array.isArray(consolidation.vwap)) continue;

      const vwapPoint = consolidation.vwap.find((v) => v.time === time);
      if (vwapPoint) {
        return {
          main: vwapPoint.value,
          upperBand1: vwapPoint.upperBand1,
          upperBand2: vwapPoint.upperBand2,
          lowerBand1: vwapPoint.lowerBand1,
          lowerBand2: vwapPoint.lowerBand2,
        };
      }
    }
    return null;
  }

  _findVwapDailyByTime(time) {
    for (const daily of this._vwapDailyData) {
      if (!daily.vwap || !Array.isArray(daily.vwap)) continue;

      // Check if time is within this day's range
      if (time >= daily.startTime && time <= daily.endTime) {
        const vwapPoint = daily.vwap.find((v) => v.time === time);
        if (vwapPoint) {
          return {
            date: daily.date,
            main: vwapPoint.value,
            upperBand1: vwapPoint.upperBand1,
            lowerBand1: vwapPoint.lowerBand1,
            upperBand2: vwapPoint.upperBand2,
            lowerBand2: vwapPoint.lowerBand2,
          };
        }
      }
    }
    return null;
  }

  // Public methods to update data sources
  updateVolume(data) {
    this._volumeData = data || [];
  }

  updateCumulativeDelta(data) {
    this._cumulativeDeltaData = data || []; // Keep for backward compatibility
  }

  updateVwapConsolidations(data) {
    this._vwapConsolidationsData = data || [];
  }

  updateVwapDaily(data) {
    this._vwapDailyData = data || [];
  }

  // Public method to manually trigger overlay update from external crosshair sync
  showAtTime(time) {
    if (!time) {
      this._hide();
      return;
    }

    // We need to find the data manually since we don't have param.seriesData
    // For candlestick data, we'll need to store it or access it differently
    // For now, trigger a synthetic crosshair move to get the data
    const timeScale = this._chart.timeScale();
    const coordinate = timeScale.timeToCoordinate(time);

    if (coordinate === null) {
      this._hide();
      return;
    }

    // Unfortunately, we can't easily get seriesData without the crosshair event
    // So we'll store the series data separately when it's updated
    const data = this._findCandleDataByTime(time);
    if (!data) {
      this._hide();
      return;
    }

    this._show(time, data);
  }

  hide() {
    this._hide();
  }

  showRightmostBar() {
    this._showRightmostBar();
  }

  // Helper to find candle data by time
  _findCandleDataByTime(time) {
    // If we have stored candle data, find it
    if (this._candleData && Array.isArray(this._candleData)) {
      return this._findDataByTime(this._candleData, time);
    }
    return null;
  }

  // Store candle data for later lookup
  updateCandleData(data) {
    this._candleData = data || [];
  }

  destroy() {
    if (this._container && this._container.parentNode) {
      this._container.parentNode.removeChild(this._container);
    }
  }
}

// Factory function to create and attach the overlay
export function addDataOverlayToChart(chart, series) {
  const overlay = new DataOverlay(chart, series);
  return overlay;
}
