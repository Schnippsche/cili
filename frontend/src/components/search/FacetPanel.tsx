import {Checkbox, FormControlLabel, FormGroup, Paper, Typography} from '@mui/material';
import type {FacetsResponse} from '../../types/api';

interface Props {
  facets?: FacetsResponse;
  mimeTypeFilter?: string;
  onMimeTypeChange: (v?: string) => void;
}

export default function FacetPanel({facets, mimeTypeFilter, onMimeTypeChange}: Readonly<Props>) {
  if (!facets || facets.mimeTypes.length === 0) return null;
  return (
      <Paper variant="outlined" sx={{p: 2}}>
        <Typography variant="subtitle2" gutterBottom>Nach Typ filtern</Typography>
        <FormGroup>
          {facets.mimeTypes.map(f => (
              <FormControlLabel key={f.value}
                                control={<Checkbox size="small" checked={mimeTypeFilter === f.value}
                                                   onChange={e => onMimeTypeChange(e.target.checked ? f.value : undefined)}/>}
                                label={`${f.value} (${f.count})`}/>
          ))}
        </FormGroup>
      </Paper>
  );
}
