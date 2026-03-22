// CumulativeDelta.js
// Displays cumulative delta as a histogram/bar chart overlay
// expects items like:
// { time: number (unix seconds), value: number, color?: string }

class CumulativeDeltaPaneView {
  constructor(primitive) {
    this._primitive = primitive;
  }

  // Draw above grid and series, but below crosshair
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

          const { horizontalPixelRatio, verticalPixelRatio } = scope;

          // Get visible time range to calculate bar width
          const visibleRange = timeScale.getVisibleLogicalRange();
          if (!visibleRange) return;

          // Find max volume for scaling (use buyVolume and sellVolume if available)
          let maxVolume = 0;
          for (const item of _items) {
            if (item.buyVolume !== undefined && item.sellVolume !== undefined) {
              maxVolume = Math.max(maxVolume, item.buyVolume, item.sellVolume);
            } else {
              // Fallback to delta values if volume data not available
              maxVolume = Math.max(maxVolume, Math.abs(item.value));
            }
          }

          if (maxVolume === 0) return;

          // Calculate histogram area (default bottom 30% of chart like TradingView)
          const histogramHeightRatio = _options.heightRatio || 0.3;
          const histogramAreaHeight =
            scope.bitmapSize.height * histogramHeightRatio;

          const displayMode = _options.displayMode || "split";
          
          // Different baselines for different display modes
          let baseline;
          let maxBarHeight;
          
          if (displayMode === "dominant") {
            // Dominant mode: bars start from bottom
            baseline = scope.bitmapSize.height - 5; // Bottom of chart with small margin
            maxBarHeight = histogramAreaHeight - 10; // Full histogram height
          } else {
            // Split mode: bars start from middle
            baseline = scope.bitmapSize.height - histogramAreaHeight / 2;
            maxBarHeight = histogramAreaHeight / 2 - 5; // Half the histogram area each direction
          }

          // Calculate approximate bar width based on visible bars
          const barSpacing = _options.barSpacing || 0.95; // Minimal gap like TradingView

          // Sort items by time to calculate bar width
          const sortedItems = [..._items].sort((a, b) => a.time - b.time);

          for (let i = 0; i < sortedItems.length; i++) {
            const item = sortedItems[i];

            // Get time coordinate
            const xMedia = timeScale.timeToCoordinate(item.time);
            if (xMedia == null) continue;

            const x = xMedia * horizontalPixelRatio;

            // Calculate bar width based on time distance to next bar
            let barWidth;
            if (i < sortedItems.length - 1) {
              const nextXMedia = timeScale.timeToCoordinate(
                sortedItems[i + 1].time
              );
              if (nextXMedia != null) {
                const gap =
                  Math.abs(nextXMedia - xMedia) * horizontalPixelRatio;
                barWidth = gap * barSpacing;
              } else {
                barWidth = 5 * horizontalPixelRatio; // fallback
              }
            } else {
              // Last bar - use same width as previous
              if (i > 0) {
                const prevXMedia = timeScale.timeToCoordinate(
                  sortedItems[i - 1].time
                );
                if (prevXMedia != null) {
                  const gap =
                    Math.abs(xMedia - prevXMedia) * horizontalPixelRatio;
                  barWidth = gap * barSpacing;
                } else {
                  barWidth = 5 * horizontalPixelRatio;
                }
              } else {
                barWidth = 5 * horizontalPixelRatio;
              }
            }

            // Ensure minimum bar width
            barWidth = Math.max(barWidth, 1);

            // Check if we have buy/sell volume data
            if (item.buyVolume !== undefined && item.sellVolume !== undefined) {
              if (displayMode === "dominant") {
                // TradingView style: single bar from bottom, colored by dominant volume
                const isDominantBuy = item.buyVolume > item.sellVolume;
                const dominantVolume = Math.max(item.buyVolume, item.sellVolume);
                // Ensure minimum bar height for visibility
                const minHeight = _options.minBarHeight || 2;

                let barHeight = (dominantVolume / maxVolume) * maxBarHeight + minHeight;
                //barHeight = Math.max(barHeight, minHeight);

                ctx.save();
                // Both colors extend UP from bottom, just different colors
                ctx.fillStyle = isDominantBuy
                  ? (_options.positiveColor || "rgba(34, 197, 94, 0.7)")
                  : (_options.negativeColor || "rgba(239, 68, 68, 0.7)");
                
                ctx.fillRect(
                  x - barWidth / 2,
                  baseline - barHeight,
                  barWidth,
                  barHeight
                );
                ctx.restore();
              } else {
                 // Ensure minimum bar height for visibility
                const minHeight = _options.minBarHeight || 2;

                // Split mode: show both buy and sell volumes
                let buyHeight = (item.buyVolume / maxVolume) * maxBarHeight + minHeight;
                let sellHeight = (item.sellVolume / maxVolume) * maxBarHeight + minHeight;
                
                buyHeight = buyHeight > 0 ? Math.max(buyHeight, minHeight) : 0;
                sellHeight = sellHeight > 0 ? Math.max(sellHeight, minHeight) : 0;

                // Draw buy volume (green) - extends UP from baseline
                if (buyHeight > 0) {
                  ctx.save();
                  ctx.fillStyle =
                    _options.positiveColor || "rgba(34, 197, 94, 0.7)";
                  ctx.fillRect(
                    x - barWidth / 2,
                    baseline - buyHeight,
                    barWidth,
                    buyHeight
                  );
                  ctx.restore();
                }

                // Draw sell volume (red) - extends DOWN from baseline
                if (sellHeight > 0) {
                  ctx.save();
                  ctx.fillStyle =
                    _options.negativeColor || "rgba(239, 68, 68, 0.7)";
                  ctx.fillRect(x - barWidth / 2, baseline, barWidth, sellHeight);
                  ctx.restore();
                }
              }
            } else {
              // Fallback: use delta value only
              let barHeight =
                (Math.abs(item.value) / maxVolume) * maxBarHeight;
              
              // Ensure minimum bar height for visibility
              const minHeight = _options.minBarHeight || 2;
              barHeight = Math.max(barHeight, minHeight);

              ctx.save();
              if (item.value >= 0) {
                // Positive delta - green bar going UP from baseline
                ctx.fillStyle =
                  _options.positiveColor || "rgba(34, 197, 94, 0.7)";
                ctx.fillRect(
                  x - barWidth / 2,
                  baseline - barHeight,
                  barWidth,
                  barHeight
                );
              } else {
                // Negative delta - red bar going DOWN from baseline
                ctx.fillStyle =
                  _options.negativeColor || "rgba(239, 68, 68, 0.7)";
                ctx.fillRect(x - barWidth / 2, baseline, barWidth, barHeight);
              }
              ctx.restore();
            }
          }

          // Draw baseline/zero line
          if (_options.showBaseline !== false) {
            ctx.save();
            ctx.strokeStyle =
              _options.baselineColor || "rgba(128, 128, 128, 0.5)";
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.moveTo(0, baseline);
            ctx.lineTo(scope.bitmapSize.width, baseline);
            ctx.stroke();
            ctx.restore();
          }
        });
      },
    };
  }
}

export class CumulativeDelta {
  constructor(items, options = {}) {
    this._items = items || [];
    this._options = {
      displayMode: "dominant", // "split" = show both buy/sell bars, "dominant" = single bar by larger volume (TradingView style)
      heightRatio: 0.3, // Use 30% of chart height for histogram area (TradingView style)
      minBarHeight: 1, // Minimum height in pixels for bars to ensure visibility
      positiveColor: "rgba(34, 197, 94, 0.6)", // Vivid green for buy volume
      negativeColor: "rgba(239, 68, 68, 0.6)", // Vivid red for sell volume
      barSpacing: 0.95, // Minimal gap between bars (TradingView style)
      showBaseline: false, // Hide baseline for cleaner look like TradingView
      baselineColor: "rgba(128, 128, 128, 0.3)",
      ...options,
    };
    this._paneViews = [new CumulativeDeltaPaneView(this)];
    this._attached = null; // { chart, series, requestUpdate }
  }

  // Lifecycle hook – called by LC when attached
  attached(param) {
    console.log("[CumulativeDelta] attached", param);
    this._attached = param;
    if (this._attached.requestUpdate) {
      this._attached.requestUpdate();
    }
  }

  detached() {
    console.log("[CumulativeDelta] detached");
    this._attached = null;
  }

  // IMPORTANT: LC calls this before drawing; keep it, even if "empty"
  updateAllViews() {
    // We don't need to recalc anything separate here, but method must exist
    // so the library knows the primitive is "valid"
  }

  paneViews() {
    // Must return the same array instance if the set of views didn't change
    return this._paneViews;
  }

  setItems(items) {
    this._items = items || [];
    console.log("[CumulativeDelta] setItems", this._items.length);

    if (this._attached && this._attached.requestUpdate) {
      this._attached.requestUpdate();
    }
  }

  setOptions(options) {
    this._options = { ...this._options, ...options };
    console.log("[CumulativeDelta] setOptions", this._options);

    if (this._attached && this._attached.requestUpdate) {
      this._attached.requestUpdate();
    }
  }
}

// Helper to wire into a series
export function addCumulativeDeltaPluginToSeries(series, items, options = {}) {
  const primitive = new CumulativeDelta(items, options);
  console.log("[CumulativeDelta] attaching primitive");
  series.attachPrimitive(primitive);

  return {
    update(newItems) {
      primitive.setItems(newItems);
    },
    setOptions(newOptions) {
      primitive.setOptions(newOptions);
    },
    destroy() {
      console.log("[CumulativeDelta] detaching primitive");
      series.detachPrimitive(primitive);
    },
  };
}
