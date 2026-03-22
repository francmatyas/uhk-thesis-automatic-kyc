// RectanglesPrimitive.js
// expects items like:
// { startTs: number (ms), endTs: number (ms), rangeHigh: number, rangeLow: number, color?, borderColor? }

class RectanglesPaneView {
  constructor(primitive) {
    this._primitive = primitive;
  }

  // Draw above grid and series, but below crosshair; tweak if you want
  zOrder() {
    return "top";
  }

  renderer() {
    const primitive = this._primitive;

    return {
      draw(target) {
        const { _items, _attached } = primitive;

        if (!_attached || !_items || _items.length === 0) {
          return;
        }

        const { chart, series } = _attached;
        const timeScale = chart.timeScale();

        target.useBitmapCoordinateSpace((scope) => {
          const ctx = scope.context;
          if (!ctx) return;

          const { horizontalPixelRatio, verticalPixelRatio } = scope;

          for (const item of _items) {
            // 1) TIME: must match your series time type
            // If your series uses unix seconds (time: 1710000000),
            // and your item.startTs/endTs are ms, you DO want `/ 1000` here.
            const x1Media = timeScale.timeToCoordinate(item.startTimeMs / 1000);
            const x2Media = timeScale.timeToCoordinate(item.endTimeMs / 1000);

            // 2) PRICE: get media (CSS) coordinates
            const y1Media = series.priceToCoordinate(item.high);
            const y2Media = series.priceToCoordinate(item.low);

            if (
              x1Media == null ||
              x2Media == null ||
              y1Media == null ||
              y2Media == null
            ) {
              continue;
            }

            // 3) Convert media → bitmap
            const x1 = x1Media * horizontalPixelRatio;
            const x2 = x2Media * horizontalPixelRatio;
            const y1 = y1Media * verticalPixelRatio;
            const y2 = y2Media * verticalPixelRatio;

            const left = Math.min(x1, x2);
            const width = Math.abs(x2 - x1);
            const top = Math.min(y1, y2);
            const height = Math.abs(y2 - y1);

            // Use item-specific color if provided, otherwise use options
            const fillColor = item.color || primitive._options.fillColor;
            const borderColor = item.borderColor || primitive._options.borderColor;
            const borderWidth = primitive._options.borderWidth;

            ctx.save();
            ctx.fillStyle = fillColor;
            ctx.fillRect(left, top, width, height);
            ctx.restore();

            ctx.save();
            ctx.strokeStyle = borderColor;
            ctx.lineWidth = borderWidth;
            ctx.strokeRect(left, top, width, height);
            ctx.restore();
          }
        });
      },
    };
  }
}

export class ConsolidationAreas {
  constructor(items, options = {}) {
    this._items = items || [];
    this._options = {
      fillColor: "rgba(255, 255, 0, 0.05)",
      borderColor: "#FFD700",
      borderWidth: 1,
      ...options,
    };
    this._paneViews = [new RectanglesPaneView(this)];
    this._attached = null; // { chart, series, requestUpdate }
  }

  // lifecycle hook – called by LC when attached
  attached(param) {
    console.log("[ConsolidationAreas] attached", param);
    this._attached = param;
    if (this._attached.requestUpdate) {
      this._attached.requestUpdate();
    }
  }

  detached() {
    console.log("[ConsolidationAreas] detached");
    this._attached = null;
  }

  // IMPORTANT: LC calls this before drawing; keep it, even if “empty”
  updateAllViews() {
    // we don't need to recalc anything separate here, but method must exist
    // so the library knows the primitive is "valid"
  }

  paneViews() {
    // must return the same array instance if the set of views didn't change
    return this._paneViews;
  }

  setItems(items) {
    this._items = items || [];
    console.log("[ConsolidationAreas] setItems", this._items.length);

    if (this._attached && this._attached.requestUpdate) {
      this._attached.requestUpdate();
    }
  }

  setOptions(options) {
    this._options = { ...this._options, ...options };
    console.log("[ConsolidationAreas] setOptions", this._options);

    if (this._attached && this._attached.requestUpdate) {
      this._attached.requestUpdate();
    }
  }
}

// Helper to wire into a series
export function addConsolidationAreasPluginToSeries(series, items, options = {}) {
  const primitive = new ConsolidationAreas(items, options);
  console.log("[ConsolidationAreas] attaching primitive");
  series.attachPrimitive(primitive);

  return {
    update(newItems) {
      primitive.setItems(newItems);
    },
    setOptions(newOptions) {
      primitive.setOptions(newOptions);
    },
    destroy() {
      console.log("[Rectangles] detaching primitive");
      series.detachPrimitive(primitive);
    },
  };
}
