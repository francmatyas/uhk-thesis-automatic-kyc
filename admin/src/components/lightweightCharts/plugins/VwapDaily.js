// VwapDaily.js
// Displays daily VWAP lines, one line per day
// expects items like:
// { date: "2024-01-01", startTime: 1704067200, endTime: 1704153599, vwap: [{ time, value }] }

class VwapDailyPriceAxisView {
  constructor(primitive) {
    this._primitive = primitive;
    this._labels = [];
  }

  update() {
    const { _items, _attached, _options } = this._primitive;

    this._labels = [];

    if (!_attached || !_items || _items.length === 0) {
      return;
    }

    const timeScale = _attached.chart.timeScale();
    const series = _attached.series;

    // Get the rightmost visible logical index
    const visibleLogicalRange = timeScale.getVisibleLogicalRange();
    if (!visibleLogicalRange) return;

    // Get the time at the rightmost visible bar
    const rightmostLogicalIndex = Math.floor(visibleLogicalRange.to);
    const rightmostTime = timeScale.coordinateToTime(
      timeScale.logicalToCoordinate(rightmostLogicalIndex)
    );

    if (!rightmostTime) return;

    // Find which day contains this time and get the VWAP values at that time
    for (const day of _items) {
      if (!day.vwap || day.vwap.length === 0) continue;

      // Check if this time falls within this day's range
      if (rightmostTime >= day.startTime && rightmostTime <= day.endTime) {
        // Find the VWAP point at or before this time
        let vwapPoint = null;
        for (let i = day.vwap.length - 1; i >= 0; i--) {
          if (day.vwap[i].time <= rightmostTime) {
            vwapPoint = day.vwap[i];
            break;
          }
        }

        if (!vwapPoint) continue;

        // Determine color for this day
        const baseColor = _options.useRainbow
          ? getRainbowColor(_items.indexOf(day), _items.length)
          : _options.vwapColor;

        // Add axis label for main VWAP line
        if (vwapPoint.value !== undefined) {
          this._labels.push({
            price: vwapPoint.value,
            color: baseColor,
          });
        }

        // Add labels for bands if enabled
        if (_options.showBands) {
          const bandColors = _options.useRainbow
            ? getDerivedBandColors(baseColor)
            : {
                upperBand1: _options.upperBand1Color,
                lowerBand1: _options.lowerBand1Color,
                upperBand2: _options.upperBand2Color,
                lowerBand2: _options.lowerBand2Color,
              };

          if (vwapPoint.upperBand1 !== undefined) {
            this._labels.push({
              price: vwapPoint.upperBand1,
              color: bandColors.upperBand1,
            });
          }
          if (vwapPoint.lowerBand1 !== undefined) {
            this._labels.push({
              price: vwapPoint.lowerBand1,
              color: bandColors.lowerBand1,
            });
          }
          if (vwapPoint.upperBand2 !== undefined) {
            this._labels.push({
              price: vwapPoint.upperBand2,
              color: bandColors.upperBand2,
            });
          }
          if (vwapPoint.lowerBand2 !== undefined) {
            this._labels.push({
              price: vwapPoint.lowerBand2,
              color: bandColors.lowerBand2,
            });
          }
        }

        break; // Found the day, no need to continue
      }
    }
  }

  priceAxisViews() {
    return this._labels.map(
      (label) =>
        new PriceAxisLabel(
          label.price,
          label.color,
          this._primitive._attached.series
        )
    );
  }
}

class PriceAxisLabel {
  constructor(price, color, series) {
    this._price = price;
    this._color = color;
    this._series = series;
  }

  coordinate() {
    return this._series.priceToCoordinate(this._price);
  }

  text() {
    return this._price.toLocaleString("en-US", {
      minimumFractionDigits: 1,
      maximumFractionDigits: 1,
    });
  }

  textColor() {
    return "#ffffff";
  }

  backColor() {
    return this._color;
  }
}

class VwapDailyPaneView {
  constructor(primitive) {
    this._primitive = primitive;
  }

  zOrder() {
    return "normal";
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

          // Get visible time range
          const visibleRange = timeScale.getVisibleLogicalRange();
          if (!visibleRange) return;

          // Draw each day's VWAP line with bands
          for (const day of _items) {
            if (!day.vwap || day.vwap.length === 0) continue;

            // Check if this day is in the visible range
            const dayStart = timeScale.timeToCoordinate(day.startTime);
            const dayEnd = timeScale.timeToCoordinate(day.endTime);

            if (dayStart == null && dayEnd == null) continue;

            // Determine color based on day index or use rainbow colors
            const baseColor = _options.useRainbow
              ? getRainbowColor(_items.indexOf(day), _items.length)
              : _options.vwapColor;

            // Draw bands first (so they're behind the VWAP line)
            if (_options.showBands) {
              // Get band colors (either derived from base color or custom)
              const bandColors = _options.useRainbow
                ? getDerivedBandColors(baseColor)
                : {
                    upperBand1: _options.upperBand1Color,
                    upperBand2: _options.upperBand2Color,
                    lowerBand1: _options.lowerBand1Color,
                    lowerBand2: _options.lowerBand2Color,
                  };

              // Upper Band 2
              drawDailyVwapLine(
                ctx,
                day.vwap,
                "upperBand2",
                bandColors.upperBand2,
                _options.upperBand2Width,
                timeScale,
                series,
                horizontalPixelRatio,
                verticalPixelRatio
              );

              // Upper Band 1
              drawDailyVwapLine(
                ctx,
                day.vwap,
                "upperBand1",
                bandColors.upperBand1,
                _options.upperBand1Width,
                timeScale,
                series,
                horizontalPixelRatio,
                verticalPixelRatio
              );

              // Lower Band 1
              drawDailyVwapLine(
                ctx,
                day.vwap,
                "lowerBand1",
                bandColors.lowerBand1,
                _options.lowerBand1Width,
                timeScale,
                series,
                horizontalPixelRatio,
                verticalPixelRatio
              );

              // Lower Band 2
              drawDailyVwapLine(
                ctx,
                day.vwap,
                "lowerBand2",
                bandColors.lowerBand2,
                _options.lowerBand2Width,
                timeScale,
                series,
                horizontalPixelRatio,
                verticalPixelRatio
              );
            }

            // Draw main VWAP line
            drawDailyVwapLine(
              ctx,
              day.vwap,
              "value",
              baseColor,
              _options.vwapWidth,
              timeScale,
              series,
              horizontalPixelRatio,
              verticalPixelRatio
            );

            // Optionally draw day label
            if (_options.showDayLabels && dayStart != null) {
              drawDayLabel(
                ctx,
                day.date,
                dayStart * horizontalPixelRatio,
                scope.bitmapSize.height - 30,
                baseColor,
                _options.labelFontSize
              );
            }
          }
        });
      },
    };
  }
}

// Helper function to draw a daily VWAP line
function drawDailyVwapLine(
  ctx,
  vwapData,
  property,
  color,
  lineWidth,
  timeScale,
  series,
  hPixelRatio,
  vPixelRatio
) {
  ctx.save();
  ctx.strokeStyle = color;
  ctx.lineWidth = lineWidth;
  ctx.beginPath();

  let firstPoint = true;

  for (const point of vwapData) {
    if (point[property] === undefined) continue;

    const xMedia = timeScale.timeToCoordinate(point.time);
    const yMedia = series.priceToCoordinate(point[property]);

    if (xMedia == null || yMedia == null) continue;

    const x = xMedia * hPixelRatio;
    const y = yMedia * vPixelRatio;

    if (firstPoint) {
      ctx.moveTo(x, y);
      firstPoint = false;
    } else {
      ctx.lineTo(x, y);
    }
  }

  ctx.stroke();
  ctx.restore();
}

// Helper function to draw day label
function drawDayLabel(ctx, date, x, y, color, fontSize) {
  ctx.save();
  ctx.fillStyle = color;
  ctx.font = `${fontSize}px sans-serif`;
  ctx.textAlign = "left";
  ctx.textBaseline = "top";
  ctx.fillText(date, x + 5, y);
  ctx.restore();
}

// Helper function to get rainbow colors
function getRainbowColor(index, total) {
  const hue = (index / Math.max(total, 1)) * 360;
  return `hsla(${hue}, 70%, 60%, 0.8)`;
}

// Helper function to derive band colors from base color for rainbow mode
function getDerivedBandColors(baseColor) {
  // Extract hue from hsla color
  const hueMatch = baseColor.match(/hsla\((\d+),/);
  if (!hueMatch) {
    // Fallback to default colors if not hsla
    return {
      upperBand1: "rgba(76, 175, 80, 0.6)",
      upperBand2: "rgba(76, 175, 80, 0.4)",
      lowerBand1: "rgba(244, 67, 54, 0.6)",
      lowerBand2: "rgba(244, 67, 54, 0.4)",
    };
  }

  const hue = parseInt(hueMatch[1]);

  return {
    upperBand1: `hsla(${hue}, 70%, 60%, 0.5)`,
    upperBand2: `hsla(${hue}, 70%, 60%, 0.3)`,
    lowerBand1: `hsla(${hue}, 70%, 60%, 0.5)`,
    lowerBand2: `hsla(${hue}, 70%, 60%, 0.3)`,
  };
}

export class VwapDaily {
  constructor(items, options = {}) {
    this._items = items || [];
    this._options = {
      vwapColor: "rgba(33, 150, 243, 0.8)", // Blue for daily VWAP
      vwapWidth: 2,
      useRainbow: false, // If true, each day gets a different color
      showDayLabels: false, // Show date labels for each day
      labelFontSize: 12,
      showBands: true,
      upperBand1Color: "rgba(76, 175, 80, 0.6)", // Green
      upperBand1Width: 1,
      upperBand2Color: "rgba(76, 175, 80, 0.4)", // Lighter green
      upperBand2Width: 1,
      lowerBand1Color: "rgba(244, 67, 54, 0.6)", // Red
      lowerBand1Width: 1,
      lowerBand2Color: "rgba(244, 67, 54, 0.4)", // Lighter red
      lowerBand2Width: 1,
      ...options,
    };
    this._paneViews = [new VwapDailyPaneView(this)];
    this._attached = null;
  }

  attached(param) {
    console.log("[VwapDaily] attached", param);
    this._attached = param;
    if (this._attached.requestUpdate) {
      this._attached.requestUpdate();
    }
  }

  detached() {
    console.log("[VwapDaily] detached");
    this._attached = null;
  }

  updateAllViews() {
    // Required by lightweight charts
  }

  paneViews() {
    return this._paneViews;
  }

  priceAxisViews() {
    if (!this._priceAxisView) {
      this._priceAxisView = new VwapDailyPriceAxisView(this);
    }
    this._priceAxisView.update();
    return this._priceAxisView.priceAxisViews();
  }

  setItems(items) {
    this._items = items || [];
    console.log("[VwapDaily] setItems", this._items.length);

    if (this._attached && this._attached.requestUpdate) {
      this._attached.requestUpdate();
    }
  }

  setOptions(options) {
    this._options = { ...this._options, ...options };
    console.log("[VwapDaily] setOptions", this._options);

    if (this._attached && this._attached.requestUpdate) {
      this._attached.requestUpdate();
    }
  }
}

// Helper to wire into a series
export function addVwapDailyPluginToSeries(series, items, options = {}) {
  const primitive = new VwapDaily(items, options);
  console.log("[VwapDaily] attaching primitive");
  series.attachPrimitive(primitive);

  return {
    update(newItems) {
      primitive.setItems(newItems);
    },
    setOptions(newOptions) {
      primitive.setOptions(newOptions);
    },
    destroy() {
      console.log("[VwapDaily] detaching primitive");
      series.detachPrimitive(primitive);
    },
  };
}
