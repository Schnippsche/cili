import { AppBar, Avatar, Box, IconButton, Menu, MenuItem, Toolbar, Tooltip, Typography } from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import DarkModeOutlinedIcon from '@mui/icons-material/DarkModeOutlined';
import LightModeOutlinedIcon from '@mui/icons-material/LightModeOutlined';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import { useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { useVersion } from '../../hooks/useVersion';
import { toggleTheme } from '../../store/themeSlice';
import type { RootState } from '../../store/store';
import ChangePasswordDialog from '../auth/ChangePasswordDialog';

export default function TopBar({ onMenuClick }: Readonly<{ onMenuClick: () => void }>) {
  const { user, logout } = useAuth();
  const { data: versionInfo } = useVersion();
  const [anchor, setAnchor] = useState<null | HTMLElement>(null);
  const [changePasswordOpen, setChangePasswordOpen] = useState(false);
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const mode = useSelector((s: RootState) => s.theme.mode);

  return (
    <AppBar position="fixed" sx={{ zIndex: (t) => t.zIndex.drawer + 1 }}>
      <Toolbar>
        <Tooltip title="Navigation">
          <IconButton color="inherit" edge="start" onClick={onMenuClick} sx={{ mr: 2 }} aria-label="menu">
            <MenuIcon />
          </IconButton>
        </Tooltip>
        <Box sx={{ flexGrow: 1, display: 'flex', alignItems: 'baseline', gap: 1 }}>
          <Typography variant="h6">CILI</Typography>
          {versionInfo?.version && (
            <Typography variant="caption" sx={{ opacity: 0.7 }}>v{versionInfo.version}</Typography>
          )}
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Tooltip title="Hilfe / Handbuch">
            <IconButton color="inherit" onClick={() => navigate('/help')} aria-label="Hilfe">
              <HelpOutlineIcon />
            </IconButton>
          </Tooltip>
          <Tooltip title={mode === 'light' ? 'Dark Mode' : 'Light Mode'}>
            <IconButton color="inherit" onClick={() => dispatch(toggleTheme())} aria-label="toggle theme">
              {mode === 'light' ? <DarkModeOutlinedIcon /> : <LightModeOutlinedIcon />}
            </IconButton>
          </Tooltip>
          <Typography variant="body2" sx={{ display: { xs: 'none', sm: 'block' } }}>{user?.username}</Typography>
          <Tooltip title="Benutzerprofil">
            <IconButton onClick={(e) => setAnchor(e.currentTarget)} size="small">
              <Avatar sx={{ width: 32, height: 32, bgcolor: 'secondary.main' }}>
                {(user?.username ?? 'U')[0].toUpperCase()}
              </Avatar>
            </IconButton>
          </Tooltip>
        </Box>
        <Menu anchorEl={anchor} open={Boolean(anchor)} onClose={() => setAnchor(null)}>
          <MenuItem onClick={() => { setAnchor(null); setChangePasswordOpen(true); }}>Passwort ändern</MenuItem>
          <MenuItem onClick={() => { setAnchor(null); void logout(); }}>Abmelden</MenuItem>
        </Menu>
      </Toolbar>
      <ChangePasswordDialog open={changePasswordOpen} onClose={() => setChangePasswordOpen(false)} />
    </AppBar>
  );
}
