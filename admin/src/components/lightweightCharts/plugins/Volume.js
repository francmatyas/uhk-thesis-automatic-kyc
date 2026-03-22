// Volume.js
// Displays volume as a histogram/bar chart overlay on price chart
// Uses tradeCount for bar height, buyTradeCount vs sellTradeCount for color
// Data structure: { time, tradeCount, buyTradeCount, sellTradeCount }

import { chartConfig } from '../chartConfig.js';

class VolumePaneView {
  constructor(primitive) {
    this._primitive = primitive;
  }

  zOrder() {
    return "top";
  }

  renderer() {
    const primitive = this._primitive;

    return {
      draw(target) {
        const { _items, _attached, _options } = primitive;

        if (!_attached || !_items || _items.length === 0) {
          return;
        }

        const { chart, series } = _attached;
        const timeScale = chart.timeScale();

        target.useBitmapCoordinateSpace((scope) => {
          const ctx = scope.context;
          if (!ctx) return;

          const { horizontalPixelRatio } = scope;
          const visibleRange = timeScale.getVisibleLogicalRange();
          if (!visibleRange) return;

          // Find max trade count for scaling
          let maxTradeCount = 0;
          for (const item of _items) {
            if (item.tradeCount !== undefined) {
              maxTradeCount = Math.max(maxTradeCount, item.tradeCount);
            }
          }

          if (maxTradeCount === 0) return;

          // Calculate histogram area (bottom 20% of chart)
          const histogramHeightRatio = _options.heightRatio || 0.2;
          const histogramAreaHeight = scope.bitmapSize.height * histogramHeightRatio;
          const baseline = scope.bitmapSize.height - 5; // Bottom of chart
          const maxBarHeight = histogramAreaHeight - 10;

          const barSpacing = _options.barSpacing || 0.95;
          const sortedItems = [..._items].sort((a, b) => a.time - b.time);

          for (let i = 0; i < sortedItems.length; i++) {
            const item = sortedItems[i];
            if (!item.tradeCount) continue;

            const xMedia = timeScale.timeToCoordinate(item.time);
            if (xMedia == null) continue;

            const x = xMedia * horizontalPixelRatio;

            // Calculate bar width
            let barWidth;
            if (i < sortedItems.length - 1) {
              const nextXMedia = timeScale.timeToCoordinate(sortedItems[i + 1].time);
              if (nextXMedia != null) {
                barWidth = Math.abs(nextXMedia - xMedia) * horizontalPixelRatio * barSpacing;
              } else {
                barWidth = 5 * horizontalPixelRatio;
              }
            } else {
              if (i > 0) {
                const prevXMedia = timeScale.timeToCoordinate(sortedItems[i - 1].time);
                if (prevXMedia != null) {
                  barWidth = Math.abs(xMedia - prevXMedia) * horizontalPixelRatio * barSpacing;
                } else {
                  barWidth = 5 * horizontalPixelRatio;
                }
              } else {
                barWidth = 5 * horizontalPixelRatio;
              }
            }

            barWidth = Math.max(barWidth, 1);

            // Calculate bar height based on trade count
            const minHeight = _options.minBarHeight || 2;
            let barHeight = (item.tradeCount / maxTradeCount) * maxBarHeight + minHeight;

            // Determine color: green if buyTradeCount > sellTradeCount, else red
            const isBuyDominant = (item.buyTradeCount || 0) > (item.sellTradeCount || 0);
            const color = isBuyDominant
              ? (_options.positiveColor || chartConfig.volume.positiveColor)
              : (_options.negativeColor || chartConfig.volume.negativeColor);

            ctx.save();
            ctx.fillStyle = color;
            ctx.fillRect(
              x - barWidth / 2,
              baseline - barHeight,
              barWidth,
              barHeight
            );
            ctx.restore();
          }
        });
      },
    };
  }
}

export class Volume {
  constructor(items, options = {}) {
    this._items = items || [];
    this._options = {
      heightRatio: 0.2,
      minBarHeight: 2,
      positiveColor: chartConfig.volume.positiveColor,
      negativeColor: chartConfig.volume.negativeColor,
      barSpacing: 0.95,
      ...options,
    };
    this._paneViews = [new VolumePaneView(this)];
    this._attached = null;
  }

  attached(param) {
    this._attached = param;
    if (this._attached.requestUpdate) {
      this._attached.requestUpdate();
    }
  }

  detached() {
    this._attached = null;
  }

  updateAllViews() {}

  paneViews() {
    return this._paneViews;
  }

  setItems(items) {
    this._items = items || [];
    if (this._attached && this._attached.requestUpdate) {
      this._attached.requestUpdate();
    }
  }

  setOptions(options) {
    this._options = { ...this._options, ...options };
    if (this._attached && this._attached.requestUpdate) {
      this._attached.requestUpdate();
    }
  }
}

export function addVolumePluginToSeries(series, items, options = {}) {
  const primitive = new Volume(items, options);
  series.attachPrimitive(primitive);

  return {
    update(newItems) {
      primitive.setItems(newItems);
    },
    setOptions(newOptions) {
      primitive.setOptions(newOptions);
    },
    destroy() {
      series.detachPrimitive(primitive);
    },
  };
}
