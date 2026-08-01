import {Drawer, Toolbar, Typography} from '@mui/material';
import type {ResourceDto} from '../../types/api';
import ShareSection from './ShareSection';

interface Props {
  resource: ResourceDto;
  open: boolean;
  onClose: () => void;
}

export default function SharePanel({resource, open, onClose}: Readonly<Props>) {
  return (
      <Drawer anchor="right" open={open} onClose={onClose}
              slotProps={{paper: {sx: {width: 400, p: 3, overflowY: 'auto'}}}}>
        <Toolbar/>
        <Typography variant="h6" gutterBottom>Freigabe</Typography>
        <Typography variant="caption" color="text.secondary" sx={{mb: 2, display: 'block'}}>
          {resource.metadata?.title ?? resource.originalName}
        </Typography>
        <ShareSection resourceId={resource.id}/>
      </Drawer>
  );
}
