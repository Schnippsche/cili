import {Button, Dialog, DialogActions, DialogContent, DialogTitle, Typography} from '@mui/material';
import ShareSection from './ShareSection';

interface Props {
  resourceId: number;
  title: string;
  onClose: () => void;
}

export default function ShareLinkDialog({resourceId, title, onClose}: Readonly<Props>) {
  return (
      <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
        <DialogTitle>Freigabelink</DialogTitle>
        <DialogContent>
          <Typography variant="caption" color="text.secondary" sx={{display: 'block'}}>
            {title}
          </Typography>
          <ShareSection resourceId={resourceId}/>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Schließen</Button>
        </DialogActions>
      </Dialog>
  );
}
