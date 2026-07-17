import { Box, Toolbar } from '@mui/material';
import { type ReactNode, useEffect, useState } from 'react';
import TopBar from './TopBar';
import Sidebar from './Sidebar';
import { useIsMobile } from '../../hooks/useIsMobile';

export default function AppShell({ children }: Readonly<{ children: ReactNode }>) {
  const isMobile = useIsMobile();

  const [open, setOpen] = useState(() => {
    if (typeof globalThis.window !== 'undefined' && globalThis.matchMedia('(max-width:899.95px)').matches) {
      return false;
    }
    return localStorage.getItem('cili.sidebarOpen') !== 'false';
  });

  useEffect(() => {
    if (isMobile) setOpen(false);
  }, [isMobile]);

  function handleMenuClick() {
    setOpen(v => {
      const next = !v;
      if (!isMobile) {
        localStorage.setItem('cili.sidebarOpen', String(next));
      }
      return next;
    });
  }

  return (
    <Box sx={{ display: 'flex' }}>
      <TopBar onMenuClick={handleMenuClick} />
      <Sidebar open={open} isMobile={isMobile} onClose={() => setOpen(false)} />
      <Box component="main" sx={{ flexGrow: 1, p: { xs: 2, md: 3 }, minHeight: '100vh' }}>
        <Toolbar />
        {children}
      </Box>
    </Box>
  );
}
