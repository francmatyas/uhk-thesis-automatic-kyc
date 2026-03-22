// LightweightChart.jsx
import React, { useEffect, useRef, useState, useCallback, useMemo } from "react";
import {
  createChart,
  CandlestickSeries,
  HistogramSeries,
} from "lightweight-charts";
import { addConsolidationAreasPluginToSeries } from "@/components/lightweightCharts/plugins/ConsolidationAreas";
import { addVolumePluginToSeries } from "@/components/lightweightCharts/plugins/Volume";
import { addVwapConsolidationsPluginToSeries } from "@/components/lightweightCharts/plugins/VwapConsolidations";
import { addVwapDailyPluginToSeries } from "@/components/lightweightCharts/plugins/VwapDaily";
import { addVolumeProfilePluginToSeries } from "@/components/lightweightCharts/plugins/VolumeProfile";
import { addDataOverlayToChart } from "@/components/lightweightCharts/plugins/DataOverlay";
import { addInfoOverlayToChart } from "@/components/lightweightCharts/plugins/InfoOverlay";
import { chartConfig } from "@/components/lightweightCharts/chartConfig";
import { cn } from "@/lib/utils";

export const LightweightChart = ({
  data = [],
  className = "",
  consolidations = [],
  cumulativeDelta = [], // NEW: for separate cumulative delta chart
  vwapConsolidations = [],
  vwapDaily = [],
  volumeProfile = [],
  volumeProfileDaily = null,
  volumeProfileConsolidations = null,
}) => {
  const resolvedVolumeProfile = useMemo(() => {
    const daily = Array.isArray(volumeProfileDaily)
      ? volumeProfileDaily
      : Array.isArray(volumeProfile)
      ? volumeProfile
      : volumeProfile?.daily || [];

    const consolidationsData = Array.isArray(volumeProfileConsolidations)
      ? volumeProfileConsolidations
      : Array.isArray(volumeProfile)
      ? []
      : volumeProfile?.consolidations || [];

    return {
      daily,
      consolidations: consolidationsData,
      consolidationRanges: consolidations,
    };
  }, [volumeProfileDaily, volumeProfileConsolidations, volumeProfile, consolidations]);

  const timeZone = "America/New_York";
  const timeFormatter = useCallback(
    (time) => {
      const formatter = new Intl.DateTimeFormat("cs-CZ", {
        timeZone,
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        hour12: false,
      });

      if (typeof time === "number") {
        return formatter.format(new Date(time * 1000));
      }

      if (time && typeof time === "object" && "year" in time) {
        return formatter.format(
          new Date(Date.UTC(time.year, time.month - 1, time.day))
        );
      }

      return "";
    },
    [timeZone]
  );

  // Price chart refs
  const priceChartContainerRef = useRef(null);
  const priceChartRef = useRef(null);
  const priceSeriesRef = useRef(null);
  const consolidationAreasRef = useRef(null);
  const volumeRef = useRef(null);
  const vwapConsolidationsRef = useRef(null);
  const vwapDailyRef = useRef(null);
  const volumeProfileRef = useRef(null);
  const dataOverlayRef = useRef(null);
  const infoOverlayRef = useRef(null);

  // Cumulative Delta chart refs
  const deltaChartContainerRef = useRef(null);
  const deltaChartRef = useRef(null);
  const deltaSeriesRef = useRef(null);
  const deltaOverlayRef = useRef(null);

  // Chart height ratio state
  const [priceChartHeight, setPriceChartHeight] = useState(65); // percentage
  const containerRef = useRef(null);
  const isDraggingRef = useRef(false);

  // Handle separator drag
  const handleMouseDown = useCallback((e) => {
    isDraggingRef.current = true;
    e.preventDefault();
  }, []);

  const handleMouseMove = useCallback((e) => {
    if (!isDraggingRef.current || !containerRef.current) return;

    const container = containerRef.current;
    const rect = container.getBoundingClientRect();
    const offsetY = e.clientY - rect.top;
    const percentage = (offsetY / rect.height) * 100;

    // Constrain between 20% and 80%
    const constrainedPercentage = Math.min(Math.max(percentage, 20), 80);
    setPriceChartHeight(constrainedPercentage);
  }, []);

  const handleMouseUp = useCallback(() => {
    isDraggingRef.current = false;
  }, []);

  // Add mouse event listeners
  useEffect(() => {
    document.addEventListener("mousemove", handleMouseMove);
    document.addEventListener("mouseup", handleMouseUp);

    return () => {
      document.removeEventListener("mousemove", handleMouseMove);
      document.removeEventListener("mouseup", handleMouseUp);
    };
  }, [handleMouseMove, handleMouseUp]);

  // init price chart + series + primitives
  useEffect(() => {
    if (!priceChartContainerRef.current || !deltaChartContainerRef.current)
      return;

    // ========== PRICE CHART ==========
    const priceChart = createChart(priceChartContainerRef.current, {
      autoSize: true,
      layout: {
        background: { color: chartConfig.chart.background },
        textColor: chartConfig.chart.textColor,
      },
      grid: {
        vertLines: { color: chartConfig.chart.grid.vertLines },
        horzLines: { color: chartConfig.chart.grid.horzLines },
      },
      timeScale: {
        borderColor: chartConfig.chart.timeScale.borderColor,
        timeVisible: false, // Hide time on price chart
        visible: false, // Hide the entire time scale
        tickMarkFormatter: timeFormatter,
        tickMarkSpacing: 80,
      },
      rightPriceScale: {
        borderColor: chartConfig.chart.priceScale.borderColor,
        minimumWidth: 100, // Fixed width to align with delta chart
      },
      localization: {
        timeFormatter,
        priceFormatter: (price) => {
          return price.toLocaleString("en-US", {
            minimumFractionDigits: 1,
            maximumFractionDigits: 2,
          });
        },
      },
    });

    const priceSeries = priceChart.addSeries(CandlestickSeries, {
      upColor: chartConfig.candlestick.upColor,
      downColor: chartConfig.candlestick.downColor,
      borderVisible: chartConfig.candlestick.borderVisible,
      wickUpColor: chartConfig.candlestick.wickUpColor,
      wickDownColor: chartConfig.candlestick.wickDownColor,
    });

    priceChartRef.current = priceChart;
    priceSeriesRef.current = priceSeries;

    // Set initial price candles
    const priceData = data.map((item) => ({
      time: item.time,
      open: Number(item.open),
      high: Number(item.high),
      low: Number(item.low),
      close: Number(item.close),
      tradeCount: item.tradeCount,
      buyTradeCount: item.buyTradeCount,
      sellTradeCount: item.sellTradeCount,
    }));
    priceSeries.setData(priceData);

    // Attach consolidation areas
    consolidationAreasRef.current = addConsolidationAreasPluginToSeries(
      priceSeries,
      consolidations,
      {
        fillColor: chartConfig.consolidationAreas.fillColor,
        borderColor: chartConfig.consolidationAreas.borderColor,
        borderWidth: chartConfig.consolidationAreas.borderWidth,
      }
    );

    // Attach volume bars
    volumeRef.current = addVolumePluginToSeries(priceSeries, data, {
      minBarHeight: chartConfig.volume.minBarHeight,
      positiveColor: chartConfig.volume.positiveColor,
      negativeColor: chartConfig.volume.negativeColor,
    });

    // Attach VWAP primitives
    vwapConsolidationsRef.current = addVwapConsolidationsPluginToSeries(
      priceSeries,
      vwapConsolidations,
      { showBands: chartConfig.vwapConsolidations.showBands }
    );

    vwapDailyRef.current = addVwapDailyPluginToSeries(priceSeries, vwapDaily, {
      useRainbow: chartConfig.vwapDaily.useRainbow,
      showBands: chartConfig.vwapDaily.showBands,
      showDayLabels: chartConfig.vwapDaily.showDayLabels,
      upperBand1Color: chartConfig.vwapDaily.bands.upperBand1.color,
      lowerBand1Color: chartConfig.vwapDaily.bands.lowerBand1.color,
      upperBand2Color: chartConfig.vwapDaily.bands.upperBand2.color,
      lowerBand2Color: chartConfig.vwapDaily.bands.lowerBand2.color,
      vwapColor: chartConfig.vwapDaily.defaultColor,
    });


    // Attach data overlay
    dataOverlayRef.current = addDataOverlayToChart(priceChart, priceSeries);
    dataOverlayRef.current.updateVolume(data); // Pass the full data with volume fields
    dataOverlayRef.current.updateVwapConsolidations(vwapConsolidations);
    dataOverlayRef.current.updateVwapDaily(vwapDaily);

    // Attach info overlay
    infoOverlayRef.current = addInfoOverlayToChart(priceChart);
    infoOverlayRef.current.updateConsolidationCount(
      consolidations?.length || 0
    );

    priceChart.timeScale().fitContent();

    // ========== CUMULATIVE DELTA CHART ==========
    const deltaChart = createChart(deltaChartContainerRef.current, {
      autoSize: true,
      layout: {
        background: { color: chartConfig.chart.background },
        textColor: chartConfig.chart.textColor,
      },
      grid: {
        vertLines: { color: chartConfig.chart.grid.vertLines },
        horzLines: { color: chartConfig.chart.grid.horzLines },
      },
      timeScale: {
        borderColor: chartConfig.chart.timeScale.borderColor,
        timeVisible: true, // Show date and time on delta chart
        tickMarkFormatter: timeFormatter,
        tickMarkSpacing: 80,
      },
      rightPriceScale: {
        borderColor: chartConfig.chart.priceScale.borderColor,
        minimumWidth: 100, // Fixed width to align with price chart
      },
      localization: {
        timeFormatter,
        priceFormatter: (price) => {
          const absPrice = Math.abs(price);
          const sign = price < 0 ? "-" : "";

          // Format with K suffix for values >= 1000
          if (absPrice >= 1000) {
            return `${sign}${(absPrice / 1000).toLocaleString("en-US", {
              minimumFractionDigits: 1,
              maximumFractionDigits: 2,
            })}K`;
          }

          // Format with thousand separator for smaller values
          return `${sign}${absPrice.toLocaleString("en-US", {
            minimumFractionDigits: 1,
            maximumFractionDigits: 2,
          })}`;
        },
      },
    });

    const deltaSeries = deltaChart.addSeries(CandlestickSeries, {
      upColor: chartConfig.candlestick.upColor,
      downColor: chartConfig.candlestick.downColor,
      borderVisible: chartConfig.candlestick.borderVisible,
      wickUpColor: chartConfig.candlestick.wickUpColor,
      wickDownColor: chartConfig.candlestick.wickDownColor,
    });

    deltaChartRef.current = deltaChart;
    deltaSeriesRef.current = deltaSeries;

    // Set initial cumulative delta data
    const deltaData = cumulativeDelta.map((item) => ({
      time: item.time,
      open: Number(item.open),
      high: Number(item.high),
      low: Number(item.low),
      close: Number(item.close),
    }));
    deltaSeries.setData(deltaData);

    // Attach data overlay for delta chart (OHLC only)
    deltaOverlayRef.current = addDataOverlayToChart(deltaChart, deltaSeries);

    deltaChart.timeScale().fitContent();

    // Sync time scales
    priceChart.timeScale().subscribeVisibleLogicalRangeChange((timeRange) => {
      deltaChart.timeScale().setVisibleLogicalRange(timeRange);
    });

    deltaChart.timeScale().subscribeVisibleLogicalRangeChange((timeRange) => {
      priceChart.timeScale().setVisibleLogicalRange(timeRange);
    });

    // Sync crosshairs and overlays between charts
    priceChart.subscribeCrosshairMove((param) => {
      if (!param.time) {
        deltaChart.clearCrosshairPosition();
        if (deltaOverlayRef.current) {
          deltaOverlayRef.current.hide();
        }
        return;
      }
      deltaChart.setCrosshairPosition(param.logical, param.time, deltaSeries);
      // Update delta overlay
      if (deltaOverlayRef.current) {
        deltaOverlayRef.current.showAtTime(param.time);
      }
    });

    deltaChart.subscribeCrosshairMove((param) => {
      if (!param.time) {
        priceChart.clearCrosshairPosition();
        if (dataOverlayRef.current) {
          // Show rightmost bar instead of hiding
          dataOverlayRef.current.showRightmostBar();
        }
        return;
      }
      priceChart.setCrosshairPosition(param.logical, param.time, priceSeries);
      // Update price overlay
      if (dataOverlayRef.current) {
        dataOverlayRef.current.showAtTime(param.time);
      }
    });

    return () => {
      if (consolidationAreasRef.current) {
        consolidationAreasRef.current.destroy();
      }
      if (volumeRef.current) {
        volumeRef.current.destroy();
      }
      if (vwapConsolidationsRef.current) {
        vwapConsolidationsRef.current.destroy();
      }
      if (vwapDailyRef.current) {
        vwapDailyRef.current.destroy();
      }
      if (volumeProfileRef.current) {
        volumeProfileRef.current.destroy();
      }
      if (dataOverlayRef.current) {
        dataOverlayRef.current.destroy();
      }
      if (infoOverlayRef.current) {
        infoOverlayRef.current.destroy();
      }
      if (deltaOverlayRef.current) {
        deltaOverlayRef.current.destroy();
      }
      priceChart.remove();
      deltaChart.remove();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [timeFormatter]); // only once

  // update price candles when `data` changes
  useEffect(() => {
    if (!priceSeriesRef.current) return;
    const priceData = data.map((item) => ({
      time: item.time,
      open: Number(item.open),
      high: Number(item.high),
      low: Number(item.low),
      close: Number(item.close),
    }));
    priceSeriesRef.current.setData(priceData);

    // Update candle and volume data in overlay
    if (dataOverlayRef.current) {
      dataOverlayRef.current.updateCandleData(priceData);
      dataOverlayRef.current.updateVolume(data);
    }

    // Update volume bars
    if (volumeRef.current) {
      volumeRef.current.update(data);
    }
  }, [data]);

  // update consolidations
  useEffect(() => {
    if (!consolidationAreasRef.current) return;
    consolidationAreasRef.current.update(consolidations);

    // Update info overlay with consolidation count
    if (infoOverlayRef.current) {
      infoOverlayRef.current.updateConsolidationCount(
        consolidations?.length || 0
      );
    }
  }, [consolidations]);

  // update cumulative delta chart
  useEffect(() => {
    if (!deltaSeriesRef.current) return;
    const deltaData = cumulativeDelta.map((item) => ({
      time: item.time,
      open: Number(item.open),
      high: Number(item.high),
      low: Number(item.low),
      close: Number(item.close),
    }));
    deltaSeriesRef.current.setData(deltaData);

    // Update delta overlay with candle data
    if (deltaOverlayRef.current) {
      deltaOverlayRef.current.updateCandleData(deltaData);
    }
  }, [cumulativeDelta]);

  // update vwap consolidations
  useEffect(() => {
    if (!vwapConsolidationsRef.current) return;
    vwapConsolidationsRef.current.update(vwapConsolidations);
    if (dataOverlayRef.current) {
      dataOverlayRef.current.updateVwapConsolidations(vwapConsolidations);
    }
  }, [vwapConsolidations]);

  // update vwap daily
  useEffect(() => {
    if (!vwapDailyRef.current) return;
    vwapDailyRef.current.update(vwapDaily);
    if (dataOverlayRef.current) {
      dataOverlayRef.current.updateVwapDaily(vwapDaily);
    }
  }, [vwapDaily]);

  // update volume profile
  useEffect(() => {
    if (!priceSeriesRef.current) return;

    const hasDaily = resolvedVolumeProfile?.daily?.length > 0;
    const hasConsolidations =
      resolvedVolumeProfile?.consolidations?.length > 0 &&
      resolvedVolumeProfile?.consolidationRanges?.length > 0;
    const shouldAttach = hasDaily || hasConsolidations;

    if (!shouldAttach) {
      if (volumeProfileRef.current) {
        volumeProfileRef.current.destroy();
        volumeProfileRef.current = null;
      }
      return;
    }

    if (!volumeProfileRef.current) {
      volumeProfileRef.current = addVolumeProfilePluginToSeries(
        priceSeriesRef.current,
        resolvedVolumeProfile
      );
      return;
    }

    volumeProfileRef.current.update(resolvedVolumeProfile);
  }, [resolvedVolumeProfile]);

  return (
    <div ref={containerRef} className={cn("flex flex-col gap-0", className)}>
      {/* Price Chart */}
      <div
        ref={priceChartContainerRef}
        className="marker-tickers-chart-price"
        style={{ height: `${priceChartHeight}%` }}
      />

      {/* Draggable Separator */}
      <div
        onMouseDown={handleMouseDown}
        className="relative h-1 bg-border hover:bg-primary/50 cursor-ns-resize transition-colors group"
        style={{ flexShrink: 0 }}
      >
        <div className="absolute inset-x-0 top-1/2 -translate-y-1/2 h-3 flex items-center justify-center">
          <div className="w-12 h-1 rounded-full bg-muted-foreground/20 group-hover:bg-primary/70 transition-colors" />
        </div>
      </div>

      {/* Cumulative Delta Chart */}
      <div
        ref={deltaChartContainerRef}
        className="marker-tickers-chart-delta"
        style={{ height: `${100 - priceChartHeight}%` }}
      />
    </div>
  );
};
