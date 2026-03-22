// VolumeProfile.js - Volume profile lines (POC/VAH/VAL) for daily and consolidation zones
import { chartConfig } from "../chartConfig.js";

export class VolumeProfile {
  constructor(series, volumeProfileData = {}) {
    this._series = series;
    this._volumeProfileData = volumeProfileData;
    this._attached = null;
  }

  update(newData) {
    this._volumeProfileData = newData || {};
    if (this._attached && this._attached.requestUpdate) {
      this._attached.requestUpdate();
    }
  }

  destroy() {
    // Cleanup if needed
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

  updateAllViews() {
    return {
      paneViews: [this],
    };
  }

  paneViews() {
    return [this];
  }

  renderer() {
    return new VolumeProfileRenderer(this._volumeProfileData);
  }
}

class VolumeProfileRenderer {
  constructor(volumeProfileData) {
    this._data = volumeProfileData;
  }

  draw(target) {
    if (!this._data) return;

    target.useBitmapCoordinateSpace((scope) => {
      const ctx = scope.context;
      if (!ctx) return;

      const timeScale = scope.chart.timeScale();
      const priceScale = scope.series.priceScale();
      const { horizontalPixelRatio, verticalPixelRatio } = scope;
      const visibleRange = timeScale.getVisibleRange();

      const dailyProfiles = Array.isArray(this._data)
        ? this._data
        : this._data.daily || [];
      const consolidationProfiles = Array.isArray(this._data)
        ? []
        : this._data.consolidations || [];
      const consolidationRanges = Array.isArray(this._data)
        ? []
        : this._data.consolidationRanges || [];

      const drawLineAtY = (x1Media, x2Media, yMedia) => {
        if (x1Media == null || x2Media == null || yMedia == null) return;
        ctx.beginPath();
        ctx.moveTo(x1Media * horizontalPixelRatio, yMedia * verticalPixelRatio);
        ctx.lineTo(x2Media * horizontalPixelRatio, yMedia * verticalPixelRatio);
        ctx.stroke();
      };

      const normalizeTime = (value) => {
        if (value == null) return null;
        const num = Number(value);
        if (!Number.isFinite(num)) return null;
        return num > 1e12 ? Math.floor(num / 1000) : num;
      };

      const drawProfileGroup = (profiles, getRange, styles) => {
        if (!profiles.length) return;

        const styleEntries = [
          { key: "pointOfControl", style: styles.poc },
          { key: "valueAreaHigh", style: styles.vah },
          { key: "valueAreaLow", style: styles.val },
        ];

        for (const entry of styleEntries) {
          ctx.save();
          ctx.strokeStyle = entry.style.color;
          ctx.lineWidth = (entry.style.width || 1) * verticalPixelRatio;
          if (entry.style.dash && ctx.setLineDash) {
            ctx.setLineDash(entry.style.dash.map((v) => v * horizontalPixelRatio));
          } else {
            ctx.setLineDash([]);
          }

          for (let i = 0; i < profiles.length; i++) {
            const profile = profiles[i];
            const range = getRange(profile, i);
            if (!range) continue;
            const startTime = normalizeTime(range.startTime);
            const endTime = normalizeTime(range.endTime ?? range.startTime);
            if (startTime == null || endTime == null) continue;
            if (
              visibleRange &&
              typeof visibleRange.from === "number" &&
              typeof visibleRange.to === "number" &&
              (endTime < visibleRange.from || startTime > visibleRange.to)
            ) {
              continue;
            }
            const x1Media = timeScale.timeToCoordinate(startTime);
            const x2Media = timeScale.timeToCoordinate(endTime);
            const yValue = Number(profile[entry.key]);
            const yMedia = priceScale.priceToCoordinate(
              Number.isFinite(yValue) ? yValue : null
            );
            drawLineAtY(x1Media, x2Media, yMedia);
          }

          ctx.restore();
        }
      };

      drawProfileGroup(
        dailyProfiles,
        (profile) => ({
          startTime: profile.startTime,
          endTime: profile.endTime,
        }),
        chartConfig.volumeProfile.daily
      );

      drawProfileGroup(
        consolidationProfiles,
        (_, i) => {
          const range = consolidationRanges[i];
          if (!range) return null;
          const startTime =
            typeof range.startTimeMs === "number"
              ? range.startTimeMs / 1000
              : range.startTime;
          const endTime =
            typeof range.endTimeMs === "number"
              ? range.endTimeMs / 1000
              : range.endTime;
          return { startTime, endTime };
        },
        chartConfig.volumeProfile.consolidation
      );
    });
  }
}

// Helper function to attach the plugin to a series
export function addVolumeProfilePluginToSeries(series, volumeProfileData = {}) {
  const volumeProfile = new VolumeProfile(series, volumeProfileData);
  series.attachPrimitive(volumeProfile);
  return volumeProfile;
}
