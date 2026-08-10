import {render, screen, waitFor} from '@testing-library/react';
import GpuMetricsPanel from './GpuMetricsPanel';
import * as gpuApi from '../../api/gpu';

vi.mock('../../api/gpu');

describe('GpuMetricsPanel', () => {
  it('zeigt Hinweis, wenn keine aktuellen Daten vorhanden sind', async () => {
    vi.mocked(gpuApi.fetchCurrentGpuMetric).mockResolvedValue(null);
    vi.mocked(gpuApi.fetchGpuMetricHistory).mockResolvedValue([]);

    render(<GpuMetricsPanel/>);

    await waitFor(() => {
      expect(screen.getByText(/Keine GPU-Daten verfügbar/i)).toBeInTheDocument();
    });
  });

  it('zeigt aktuelle Temperatur, wenn ein Sample vorhanden ist', async () => {
    vi.mocked(gpuApi.fetchCurrentGpuMetric).mockResolvedValue({
      id: 1, sampledAt: new Date().toISOString(), temperatureC: 62, powerDrawW: 145.3,
      memoryUsedMb: 8123, memoryTotalMb: 24576, utilizationGpuPct: 34,
      fanSpeedPct: 45, clockSmMhz: 1830, clockMemoryMhz: 9501,
    });
    vi.mocked(gpuApi.fetchGpuMetricHistory).mockResolvedValue([]);

    render(<GpuMetricsPanel/>);

    await waitFor(() => {
      expect(screen.getByText('62')).toBeInTheDocument();
    });
  });
});
