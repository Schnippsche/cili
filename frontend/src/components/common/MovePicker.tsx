import {useState} from 'react';
import {
  Button,
  CircularProgress,
  Collapse,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
} from '@mui/material';
import FolderIcon from '@mui/icons-material/Folder';
import FolderOpenIcon from '@mui/icons-material/FolderOpen';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import {useFolderChildren} from '../../hooks/useFolders';

interface FolderNodeProps {
  folderId: number;
  name: string;
  selectedId: number | null;
  excludeId?: number;
  onSelect: (id: number) => void;
  depth: number;
}

function FolderNode({
                      folderId,
                      name,
                      selectedId,
                      excludeId,
                      onSelect,
                      depth
                    }: Readonly<FolderNodeProps>) {
  const [expanded, setExpanded] = useState(false);
  const {data: children = [], isLoading} = useFolderChildren(expanded ? folderId : undefined);
  const isSelected = selectedId === folderId;
  const isExcluded = excludeId === folderId;

  return (
      <>
        <ListItemButton
            selected={isSelected}
            disabled={isExcluded}
            sx={{pl: 2 + depth * 2}}
            onClick={() => !isExcluded && onSelect(folderId)}
        >
          {/* pointerEvents: auto überschreibt das pointer-events:none des disabled-Parents,
            damit der Expand-Pfeil auch bei ausgegrauten Ordnern klickbar bleibt */}
          <ListItemIcon
              sx={{minWidth: 28, pointerEvents: 'auto', cursor: 'pointer'}}
              onClick={e => {
                e.stopPropagation();
                setExpanded(v => !v);
              }}
          >
            {isLoading ? (
                <CircularProgress size={16}/>
            ) : expanded ? (
                <ExpandMoreIcon fontSize="small"/>
            ) : (
                <ChevronRightIcon fontSize="small"/>
            )}
          </ListItemIcon>
          <ListItemIcon sx={{minWidth: 32}}>
            {isSelected ? <FolderOpenIcon fontSize="small" color="primary"/> :
                <FolderIcon fontSize="small"/>}
          </ListItemIcon>
          <ListItemText primary={name} primaryTypographyProps={{variant: 'body2'}}/>
        </ListItemButton>
        <Collapse in={expanded} unmountOnExit>
          <List disablePadding>
            {children.map(c => (
                <FolderNode
                    key={c.id}
                    folderId={c.id}
                    name={c.name}
                    selectedId={selectedId}
                    excludeId={excludeId}
                    onSelect={onSelect}
                    depth={depth + 1}
                />
            ))}
          </List>
        </Collapse>
      </>
  );
}

interface Props {
  open: boolean;
  title: string;
  excludeId?: number;
  onClose: () => void;
  onConfirm: (targetFolderId: number) => void | Promise<void>;
}

export default function MovePicker({open, title, excludeId, onClose, onConfirm}: Readonly<Props>) {
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [pending, setPending] = useState(false);
  const {data: rootFolders = []} = useFolderChildren(undefined);

  const handleConfirm = async () => {
    if (selectedId == null) return;
    setPending(true);
    try {
      await onConfirm(selectedId);
      setSelectedId(null);
    } finally {
      setPending(false);
    }
  };

  const handleClose = () => {
    setSelectedId(null);
    onClose();
  };

  return (
      <Dialog open={open} onClose={handleClose} maxWidth="xs" fullWidth>
        <DialogTitle>{title}</DialogTitle>
        <DialogContent dividers sx={{p: 0, maxHeight: 400, overflowY: 'auto'}}>
          <List disablePadding>
            {rootFolders.map(f => (
                <FolderNode
                    key={f.id}
                    folderId={f.id}
                    name={f.name}
                    selectedId={selectedId}
                    excludeId={excludeId}
                    onSelect={setSelectedId}
                    depth={0}
                />
            ))}
          </List>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose}>Abbrechen</Button>
          <Button variant="contained" disabled={selectedId == null || pending}
                  onClick={handleConfirm}>
            Verschieben
          </Button>
        </DialogActions>
      </Dialog>
  );
}
