import {InputAdornment, TextField} from '@mui/material';
import SearchOutlinedIcon from '@mui/icons-material/SearchOutlined';

export default function SearchBar({value, onChange}: Readonly<{
  value: string;
  onChange: (v: string) => void
}>) {
  return (
      <TextField value={value} onChange={e => onChange(e.target.value)} fullWidth autoFocus
                 placeholder="Suchbegriff(e) eingeben…"
                 slotProps={{
                   input: {
                     startAdornment: <InputAdornment
                         position="start"><SearchOutlinedIcon/></InputAdornment>
                   }
                 }}/>
  );
}
