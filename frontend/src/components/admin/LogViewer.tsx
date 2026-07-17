import {
  Alert, Box, CircularProgress, IconButton, TextField, Tooltip, Typography,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import * as adminApi from '../../api/admin';

function lineColor(line: string): string {
  if (line.includes(' ERROR ')) return '#f28b82';
  if (line.includes(' WARN  ')) return '#fdd663';
  if (line.includes(' DEBUG ')) return '#9aa0a6';
  return '#e8eaed';
}

export default function LogViewer() {
  const qc = useQueryClient();
  const [search, setSearch] = useState('');

  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin-logs'],
    queryFn: () => adminApi.fetchLogs(500),
  });

  const lines = (data?.lines ?? []).filter(l =>
    search === '' || l.toLowerCase().includes(search.toLowerCase()),
  );

  const lastModified = data?.lastModified
    ? new Date(data.lastModified).toLocaleTimeString('de-DE')
    : null;

  return (
    <Box>
      <Box sx={{ display: 'flex', gap: 2, mb: 2, alignItems: 'center', flexWrap: 'wrap' }}>
        <TextField
          size="small"
          label="Suche"
          value={search}
          onChange={e => setSearch(e.target.value)}
          sx={{ minWidth: 220 }}
        />
        <Typography variant="body2" color="text.secondary" sx={{ flexGrow: 1 }}>
          {data != null
            ? `${lines.length} Zeilen${lastModified ? ` · Stand: ${lastModified}` : ''}`
            : ''}
        </Typography>
        <Tooltip title="Aktualisieren">
          <IconButton
            size="small"
            onClick={() => void qc.invalidateQueries({ queryKey: ['admin-logs'] })}
            disabled={isLoading}
          >
            <RefreshIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      </Box>

      {isLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
          <CircularProgress />
        </Box>
      )}

      {isError && (
        <Alert severity="error">
          Log-Datei konnte nicht geladen werden. Möglicherweise ist kein Datei-Appender aktiv
          (nur im Prod-Profil wird in Dateien geschrieben).
        </Alert>
      )}

      {!isLoading && !isError && lines.length === 0 && data != null && (
        <Alert severity="info">
          {search ? 'Keine Einträge für diese Suche.' : 'Log-Datei ist leer.'}
        </Alert>
      )}

      {!isLoading && !isError && lines.length > 0 && (
        <Box
          component="pre"
          sx={{
            fontFamily: 'monospace',
            fontSize: '0.72rem',
            lineHeight: 1.6,
            bgcolor: 'grey.900',
            color: '#e8eaed',
            borderRadius: 1,
            p: 2,
            overflow: 'auto',
            maxHeight: '70vh',
            m: 0,
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-all',
          }}
        >
          {lines.map((line, i) => (
            <Box
              component="span"
              // eslint-disable-next-line react/no-array-index-key
              key={i}
              sx={{ display: 'block', color: lineColor(line) }}
            >
              {line}
            </Box>
          ))}
        </Box>
      )}
    </Box>
  );
}
