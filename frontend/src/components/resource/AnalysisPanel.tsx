import { Box, Drawer, Toolbar, Typography } from '@mui/material';
import type { ResourceDto } from '../../types/api';
import AnalysisSection from './AnalysisSection';

interface Props {
  resource: ResourceDto;
  open: boolean;
  onClose: () => void;
}

export default function AnalysisPanel({ resource, open, onClose }: Readonly<Props>) {
  return (
    <Drawer anchor="right" open={open} onClose={onClose}
      slotProps={{ paper: { sx: { width: 400, display: 'flex', flexDirection: 'column', height: '100%' } } }}>
      <Toolbar />
      <Box sx={{ flex: 1, overflowY: 'auto', p: 3 }}>
        <Typography variant="h6" gutterBottom>KI-Zusammenfassung</Typography>
        <Typography variant="caption" color="text.secondary" sx={{ mb: 2, display: 'block' }}>
          {resource.metadata?.title ?? resource.originalName}
        </Typography>
        <AnalysisSection resourceId={resource.id} />
      </Box>
    </Drawer>
  );
}
