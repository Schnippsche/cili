import {useCallback, useEffect, useState} from 'react';
import {Box, Card, CardContent, ToggleButton, ToggleButtonGroup, Typography} from '@mui/material';
import Grid from '@mui/material/Grid2';
import {CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis} from 'recharts';
import {fetchCurrentGpuMetric, fetchGpuMetricHistory} from '../../api/gpu';
import type {GpuMetricSampleDto} from '../../types/api';

const POLL_INTERVAL_MS = 30_000;
const HOUR_OPTIONS = [1, 6, 24, 168] as const;
type HourOption = typeof HOUR_OPTIONS[number];
const HOUR_LABELS: Record<HourOption, string> = {1: '1h', 6: '6h', 24: '24h', 168: '7d'};

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('de-DE', {hour: '2-digit', minute: '2-digit'});
}

function MetricTile({label, value, unit}: Readonly<{ label: string; value: string; unit: string }>) {
  return (
      <Grid size={{xs: 6, sm: 4, md: 2}}>
        <Card variant="outlined">
          <CardContent>
            <Typography variant="caption" color="text.secondary">{label}</Typography>
            <Typography variant="h6">{value}<Typography component="span" variant="body2"> {unit}</Typography></Typography>
          </CardContent>
        </Card>
      </Grid>
  );
}

export default function GpuMetricsPanel() {
  const [current, setCurrent] = useState<GpuMetricSampleDto | null>(null);
  const [history, setHistory] = useState<GpuMetricSampleDto[]>([]);
  const [hours, setHours] = useState<HourOption>(24);

  const reload = useCallback(() => {
    fetchCurrentGpuMetric().then(setCurrent).catch(() => setCurrent(null));
    fetchGpuMetricHistory(hours).then(setHistory).catch(() => setHistory([]));
  }, [hours]);

  useEffect(() => {
    reload();
    const id = setInterval(reload, POLL_INTERVAL_MS);
    return () => clearInterval(id);
  }, [reload]);

  const chartData = history.map(s => ({
    time: formatTime(s.sampledAt),
    temperatureC: s.temperatureC,
    powerDrawW: s.powerDrawW,
    memoryUsedMb: s.memoryUsedMb,
    memoryTotalMb: s.memoryTotalMb,
    utilizationGpuPct: s.utilizationGpuPct,
    fanSpeedPct: s.fanSpeedPct,
  }));

  return (
      <Box>
        {!current && (
            <Typography color="text.secondary" sx={{mb: 2}}>
              Keine GPU-Daten verfügbar. Läuft nvidia-smi auf diesem Server und ist die Erfassung aktiviert
              (cili.gpu-monitoring.enabled)?
            </Typography>
        )}
        <Grid container spacing={2} sx={{mb: 3}}>
          <MetricTile label="Temperatur" value={current ? String(current.temperatureC) : '–'} unit="°C"/>
          <MetricTile label="Leistung" value={current ? current.powerDrawW.toFixed(1) : '–'} unit="W"/>
          <MetricTile label="VRAM"
                      value={current ? `${current.memoryUsedMb} / ${current.memoryTotalMb}` : '–'} unit="MB"/>
          <MetricTile label="Auslastung" value={current ? String(current.utilizationGpuPct) : '–'} unit="%"/>
          <MetricTile label="Lüfter" value={current?.fanSpeedPct != null ? String(current.fanSpeedPct) : '–'}
                      unit="%"/>
          <MetricTile label="Takt (SM / Mem)"
                      value={current ? `${current.clockSmMhz} / ${current.clockMemoryMhz}` : '–'} unit="MHz"/>
        </Grid>

        <ToggleButtonGroup size="small" value={hours} exclusive
                            onChange={(_, v) => v !== null && setHours(v)} sx={{mb: 2}}>
          {HOUR_OPTIONS.map(h => (
              <ToggleButton key={h} value={h}>{HOUR_LABELS[h]}</ToggleButton>
          ))}
        </ToggleButtonGroup>

        <Typography variant="subtitle1" gutterBottom>Temperatur &amp; Leistung</Typography>
        <ResponsiveContainer width="100%" height={220}>
          <LineChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3"/>
            <XAxis dataKey="time"/>
            <YAxis yAxisId="left" label={{value: '°C', angle: -90, position: 'insideLeft'}}/>
            <YAxis yAxisId="right" orientation="right" label={{value: 'W', angle: 90, position: 'insideRight'}}/>
            <Tooltip/>
            <Legend/>
            <Line yAxisId="left" type="monotone" dataKey="temperatureC" name="Temperatur (°C)" stroke="#e57373" dot={false}/>
            <Line yAxisId="right" type="monotone" dataKey="powerDrawW" name="Leistung (W)" stroke="#64b5f6" dot={false}/>
          </LineChart>
        </ResponsiveContainer>

        <Typography variant="subtitle1" gutterBottom sx={{mt: 3}}>VRAM</Typography>
        <ResponsiveContainer width="100%" height={220}>
          <LineChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3"/>
            <XAxis dataKey="time"/>
            <YAxis label={{value: 'MB', angle: -90, position: 'insideLeft'}}/>
            <Tooltip/>
            <Legend/>
            <Line type="monotone" dataKey="memoryUsedMb" name="Belegt (MB)" stroke="#81c784" dot={false}/>
            <Line type="monotone" dataKey="memoryTotalMb" name="Gesamt (MB)" stroke="#9e9e9e" strokeDasharray="4 4" dot={false}/>
          </LineChart>
        </ResponsiveContainer>

        <Typography variant="subtitle1" gutterBottom sx={{mt: 3}}>Auslastung &amp; Lüfter</Typography>
        <ResponsiveContainer width="100%" height={220}>
          <LineChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3"/>
            <XAxis dataKey="time"/>
            <YAxis domain={[0, 100]} label={{value: '%', angle: -90, position: 'insideLeft'}}/>
            <Tooltip/>
            <Legend/>
            <Line type="monotone" dataKey="utilizationGpuPct" name="Auslastung (%)" stroke="#ba68c8" dot={false}/>
            <Line type="monotone" dataKey="fanSpeedPct" name="Lüfter (%)" stroke="#4db6ac" dot={false}/>
          </LineChart>
        </ResponsiveContainer>
      </Box>
  );
}
