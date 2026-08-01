import {useMediaQuery} from '@mui/material';

export function useIsMobile(): boolean {
  return useMediaQuery('(max-width:899.95px)');
}
