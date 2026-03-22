// VwapConsolidations.js
// Displays VWAP lines with bands for each consolidation period
// expects items like:
// { vwap: [{ time, value, upperBand1, upperBand2, lowerBand1, lowerBand2 }] }

class VwapConsolidationsPriceAxisView {
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
    const rightmostTime = timeScale.coordinateToTime(timeScale.logicalToCoordinate(rightmostLogicalIndex));
    
    if (!rightmostTime) return;

    // Find which consolidation contains this time and get the VWAP values at that time
    for (const consolidation of _items) {
      if (!consolidation.vwap || consolidation.vwap.length === 0) continue;
      
      // Check if this time falls within this consolidation's range
      if (rightmostTime >= consolidation.startTime && rightmostTime <= consolidation.endTime) {
        // Find the VWAP point at or before this time
        let vwapPoint = null;
        for (let i = consolidation.vwap.length - 1; i >= 0; i--) {
          if (consolidation.vwap[i].time <= rightmostTime) {
            vwapPoint = consolidation.vwap[i];
            break;
          }
        }
        
        if (!vwapPoint) continue;

        // Add axis label for main VWAP line
        if (vwapPoint.value !== undefined) {
          this._labels.push({
            price: vwapPoint.value,
            color: _options.vwapColor
          });
        }

        // Add labels for bands if enabled
        if (_options.showBands) {
          if (vwapPoint.upperBand1 !== undefined) {
            this._labels.push({
              price: vwapPoint.upperBand1,
              color: _options.upperBand1Color
            });
          }
          if (vwapPoint.upperBand2 !== undefined) {
            this._labels.push({
              price: vwapPoint.upperBand2,
              color: _options.upperBand2Color
            });
          }
          if (vwapPoint.lowerBand1 !== undefined) {
            this._labels.push({
              price: vwapPoint.lowerBand1,
              color: _options.lowerBand1Color
            });
          }
          if (vwapPoint.lowerBand2 !== undefined) {
            this._labels.push({
              price: vwapPoint.lowerBand2,
              color: _options.lowerBand2Color
            });
          }
        }
        
        break; // Found the consolidation, no need to continue
      }
    }
  }

  priceAxisViews() {
    return this._labels.map(label => new VwapPriceAxisLabel(label.price, label.color, this._primitive._attached.series));
  }
}

class VwapPriceAxisLabel {
  constructor(price, color, series) {
    this._price = price;
    this._color = color;
    this._series = series;
  }

  coordinate() {
    return this._series.priceToCoordinate(this._price);
  }

  text() {
    return this._price.toFixed(2);
  }

  textColor() {
    return '#ffffff';
  }

  backColor() {
    return this._color;
  }
}

class VwapConsolidationsPaneView {
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

          // Draw each consolidation's VWAP and bands
          for (const consolidation of _items) {
            if (!consolidation.vwap || consolidation.vwap.length === 0)
              continue;

            // Draw bands first (so they're behind the VWAP line)
            if (_options.showBands) {
              // Upper Band 2
              drawLine(
                ctx,
                consolidation.vwap,
                "upperBand2",
                _options.upperBand2Color,
                _options.upperBand2Width,
                timeScale,
                series,
                horizontalPixelRatio,
                verticalPixelRatio
              );

              // Upper Band 1
              drawLine(
                ctx,
                consolidation.vwap,
                "upperBand1",
                _options.upperBand1Color,
                _options.upperBand1Width,
                timeScale,
                series,
                horizontalPixelRatio,
                verticalPixelRatio
              );

              // Lower Band 1
              drawLine(
                ctx,
                consolidation.vwap,
                "lowerBand1",
                _options.lowerBand1Color,
                _options.lowerBand1Width,
                timeScale,
                series,
                horizontalPixelRatio,
                verticalPixelRatio
              );

              // Lower Band 2
              drawLine(
                ctx,
                consolidation.vwap,
                "lowerBand2",
                _options.lowerBand2Color,
                _options.lowerBand2Width,
                timeScale,
                series,
                horizontalPixelRatio,
                verticalPixelRatio
              );
            }

            // Draw VWAP line (main line)
            drawLine(
              ctx,
              consolidation.vwap,
              "value",
              _options.vwapColor,
              _options.vwapWidth,
              timeScale,
              series,
              horizontalPixelRatio,
              verticalPixelRatio
            );
          }
        });
      },
    };
  }
}

// Helper function to draw a line
function drawLine(
  ctx,
  data,
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

  for (const point of data) {
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

export class VwapConsolidations {
  constructor(items, options = {}) {
    this._items = items || [];
    this._options = {
      vwapColor: "rgba(33, 150, 243, 0.9)", // Blue for main VWAP
      vwapWidth: 3,
      showBands: true,
      upperBand1Color: "rgba(0, 139, 139, 0.9)", // Orange
      upperBand1Width: 2,
      upperBand2Color: "rgba(160, 120, 90, 0.9)", // Red
      upperBand2Width: 2,
      lowerBand1Color: "rgba(0, 139, 139, 0.9)", // Orange
      lowerBand1Width: 2,
      lowerBand2Color: "rgba(160, 120, 90, 0.9)", // Red
      lowerBand2Width: 2,
      ...options,
    };
    this._paneViews = [new VwapConsolidationsPaneView(this)];
    this._attached = null;
  }

  attached(param) {
    console.log("[VwapConsolidations] attached", param);
    this._attached = param;
    if (this._attached.requestUpdate) {
      this._attached.requestUpdate();
    }
  }

  detached() {
    console.log("[VwapConsolidations] detached");
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
      this._priceAxisView = new VwapConsolidationsPriceAxisView(this);
    }
    this._priceAxisView.update();
    return this._priceAxisView.priceAxisViews();
  }

  setItems(items) {
    this._items = items || [];
    console.log("[VwapConsolidations] setItems", this._items.length);

    if (this._attached && this._attached.requestUpdate) {
      this._attached.requestUpdate();
    }
  }

  setOptions(options) {
    this._options = { ...this._options, ...options };
    console.log("[VwapConsolidations] setOptions", this._options);

    if (this._attached && this._attached.requestUpdate) {
      this._attached.requestUpdate();
    }
  }
}

// Helper to wire into a series
export function addVwapConsolidationsPluginToSeries(
  series,
  items,
  options = {}
) {
  const primitive = new VwapConsolidations(items, options);
  console.log("[VwapConsolidations] attaching primitive");
  series.attachPrimitive(primitive);

  return {
    update(newItems) {
      primitive.setItems(newItems);
    },
    setOptions(newOptions) {
      primitive.setOptions(newOptions);
    },
    destroy() {
      console.log("[VwapConsolidations] detaching primitive");
      series.detachPrimitive(primitive);
    },
  };
}
