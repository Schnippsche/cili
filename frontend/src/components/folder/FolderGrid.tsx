import { Grid2 } from '@mui/material';
import FolderCard from './FolderCard';
import type { FolderDto } from '../../types/api';
import { type MouseEvent } from 'react';

interface Props { folders: FolderDto[]; onMenuOpen: (e: MouseEvent, f: FolderDto) => void; showMenu?: boolean; }

export default function FolderGrid({ folders, onMenuOpen, showMenu = false }: Readonly<Props>) {
  return (
    <Grid2 container spacing={2}>
      {folders.map((f) => (
        <Grid2 key={f.id} size={{ xs: 6, sm: 4, md: 3, lg: 2 }}>
          <FolderCard folder={f} onMenuOpen={onMenuOpen} showMenu={showMenu} />
        </Grid2>
      ))}
    </Grid2>
  );
}
