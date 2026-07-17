import { Box, IconButton, Table, TableBody, TableCell, TableHead, TableRow, Tooltip, Typography } from '@mui/material';
import FolderOutlinedIcon from '@mui/icons-material/FolderOutlined';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import { type MouseEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import type { FolderDto } from '../../types/api';

interface Props {
  folders: FolderDto[];
  onMenuOpen: (e: MouseEvent, f: FolderDto) => void;
  showMenu?: boolean;
}

export default function FolderList({ folders, onMenuOpen, showMenu = false }: Readonly<Props>) {
  const navigate = useNavigate();
  return (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>Name</TableCell>
          <TableCell>Geändert</TableCell>
          {showMenu && <TableCell />}
        </TableRow>
      </TableHead>
      <TableBody>
        {folders.map(f => (
          <TableRow key={f.id} hover onClick={() => navigate(`/folders/${f.id}`)} sx={{ cursor: 'pointer' }}>
            <TableCell>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <FolderOutlinedIcon sx={{ color: 'primary.main', fontSize: 20, flexShrink: 0 }} />
                <Typography variant="body2">{f.name}</Typography>
              </Box>
            </TableCell>
            <TableCell>
              <Typography variant="body2" color="text.secondary">
                {new Date(f.updatedAt).toLocaleDateString('de-DE')}
              </Typography>
            </TableCell>
            {showMenu && (
              <TableCell align="right" sx={{ width: 40, pr: 0 }}>
                <Tooltip title="Optionen">
                  <IconButton size="small" onClick={e => { e.stopPropagation(); onMenuOpen(e, f); }}>
                    <MoreVertIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              </TableCell>
            )}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
