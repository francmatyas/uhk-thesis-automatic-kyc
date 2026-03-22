// InfoOverlay.js - Simple static info overlay for displaying fixed data
import { chartConfig } from "../chartConfig.js";

export class InfoOverlay {
  constructor(chart, options = {}) {
    this._chart = chart;
    this._container = null;
    this._data = {
      consolidationCount: 0,
    };

    this._createContainer();
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
      right: 102px;
      padding: ${config.padding};
      background: ${config.background};
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
      font-size: ${config.fontSize};
      color: ${config.textColor};
      pointer-events: none;
      z-index: 40;
    `;

    chartContainer.appendChild(this._container);
    this._render();
  }

  _render() {
    const config = chartConfig.dataOverlay;
    const consolidationConfig = chartConfig.consolidationAreas;
    let rows = [];

    // Consolidation areas count with colored box
    if (this._data.consolidationCount !== undefined && this._data.consolidationCount > 0) {
      rows.push(
        `<div style="display: flex; align-items: center; gap: 6px;">
          <div style="width: 14px; height: 14px; background: ${consolidationConfig.fillColor}; border: 1px solid ${consolidationConfig.borderColor}; border-radius: 2px;"></div>
          <span style="color: #C8C8C8;">${this._data.consolidationCount}</span>
        </div>`
      );
    }

    this._container.innerHTML = rows.join("<br/>");
  }

  // Public method to update consolidation count
  updateConsolidationCount(count) {
    this._data.consolidationCount = count || 0;
    this._render();
  }

  // Public method to update any info data
  updateInfo(data) {
    this._data = { ...this._data, ...data };
    this._render();
  }

  destroy() {
    if (this._container && this._container.parentNode) {
      this._container.parentNode.removeChild(this._container);
    }
  }
}

// Factory function to create and attach the overlay
export function addInfoOverlayToChart(chart) {
  const overlay = new InfoOverlay(chart);
  return overlay;
}
