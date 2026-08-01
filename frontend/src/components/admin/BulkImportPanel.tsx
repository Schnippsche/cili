import {memo} from 'react';
import {Box, List, ListItem, ListItemIcon, ListItemText, Tooltip, Typography} from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import BlockIcon from '@mui/icons-material/Block';
import CircularProgress from '@mui/material/CircularProgress';
import ScheduleIcon from '@mui/icons-material/Schedule';
import type {BulkImportProgressItem} from '../../hooks/useBulkImportUpload';

interface Props {
  items: BulkImportProgressItem[];
}

function statusIcon(status: BulkImportProgressItem['status']) {
  switch (status) {
    case 'DONE':
      return <CheckCircleIcon fontSize="small" color="success"/>;
    case 'FAILED':
      return <ErrorIcon fontSize="small" color="error"/>;
    case 'SKIPPED':
      return <BlockIcon fontSize="small" color="disabled"/>;
    case 'UPLOADING':
      return <CircularProgress size={16}/>;
    default:
      return <ScheduleIcon fontSize="small" color="disabled"/>;
  }
}

interface RowProps {
  item: BulkImportProgressItem;
}

// Memoized so that a status change on one item (which creates a new object
// reference only for that item — see useBulkImportUpload's updateItem) does
// not force a re-render of every other unrelated row in large import lists.
const BulkImportRow = memo(function BulkImportRow({item}: Readonly<RowProps>) {
  return (
      <ListItem>
        <ListItemIcon sx={{minWidth: 32}}>{statusIcon(item.status)}</ListItemIcon>
        <Tooltip title={item.skipReason ?? item.errorMessage ?? ''}
                 disableHoverListener={!item.skipReason && !item.errorMessage}>
          <ListItemText
              primary={item.relativePath}
              secondary={item.skipReason ?? item.errorMessage ?? undefined}
          />
        </Tooltip>
      </ListItem>
  );
});

export default function BulkImportPanel({items}: Readonly<Props>) {
  const done = items.filter(i => i.status === 'DONE').length;
  const skipped = items.filter(i => i.status === 'SKIPPED').length;
  const failed = items.filter(i => i.status === 'FAILED').length;

  return (
      <Box>
        <Typography variant="body2" color="text.secondary" gutterBottom>
          {items.length} Dateien insgesamt
          — {done} fertig, {skipped} übersprungen, {failed} fehlgeschlagen
        </Typography>
        <List dense sx={{maxHeight: 500, overflowY: 'auto'}}>
          {items.map(item => (
              <BulkImportRow key={item.id} item={item}/>
          ))}
        </List>
      </Box>
  );
}
