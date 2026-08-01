import {
  Box,
  Divider,
  Drawer,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Toolbar,
  Tooltip,
  Typography
} from '@mui/material';
import HomeOutlinedIcon from '@mui/icons-material/HomeOutlined';
import RateReviewOutlinedIcon from '@mui/icons-material/RateReviewOutlined';
import SearchOutlinedIcon from '@mui/icons-material/SearchOutlined';
import PeopleOutlinedIcon from '@mui/icons-material/PeopleOutlined';
import GroupsOutlinedIcon from '@mui/icons-material/GroupsOutlined';
import DeleteOutlineOutlinedIcon from '@mui/icons-material/DeleteOutlineOutlined';
import WorkHistoryOutlinedIcon from '@mui/icons-material/WorkHistoryOutlined';
import ArticleOutlinedIcon from '@mui/icons-material/ArticleOutlined';
import DriveFolderUploadOutlinedIcon from '@mui/icons-material/DriveFolderUploadOutlined';
import CollectionsBookmarkIcon from '@mui/icons-material/CollectionsBookmark';
import {type ElementType} from 'react';
import {NavLink} from 'react-router-dom';
import {useSelector} from 'react-redux';
import type {RootState} from '../../store/store';
import {useTestimonialsPermissions} from '../../hooks/useAcl';

const WIDTH = 220;
const MINI = 56;

interface Props {
  open: boolean;
  isMobile: boolean;
  onClose: () => void;
}

export default function Sidebar({open, isMobile, onClose}: Readonly<Props>) {
  const user = useSelector((s: RootState) => s.auth.user);
  const {data: testimonialsPerms} = useTestimonialsPermissions();
  const canViewTestimonials =
      user?.role === 'ADMIN' ||
      (testimonialsPerms?.permissions.includes('READ') ?? false);

  const paperWidth = isMobile || open ? WIDTH : MINI;
  const navClick = isMobile ? {onClick: onClose} : {};

  return (
      <Drawer
          variant={isMobile ? 'temporary' : 'permanent'}
          open={open}
          onClose={onClose}
          sx={{
            width: isMobile ? 0 : paperWidth,
            flexShrink: 0,
            transition: 'width 0.2s',
            '& .MuiDrawer-paper': {
              width: paperWidth,
              boxSizing: 'border-box',
              overflowX: 'hidden',
              transition: 'width 0.2s',
            },
          }}
      >
        <Toolbar/>
        <Box sx={{overflow: 'hidden'}}>
          <List dense>
            <Tooltip title={open ? '' : 'Startseite'} placement="right">
              <ListItemButton component={NavLink as ElementType} to="/" {...navClick}>
                <ListItemIcon sx={{minWidth: 40}}><HomeOutlinedIcon/></ListItemIcon>
                {open && <ListItemText primary="Startseite"/>}
              </ListItemButton>
            </Tooltip>
            {canViewTestimonials && (
                <Tooltip title={open ? '' : 'Erfahrungsberichte'} placement="right">
                  <ListItemButton component={NavLink as ElementType}
                                  to="/testimonials" {...navClick}>
                    <ListItemIcon sx={{minWidth: 40}}><RateReviewOutlinedIcon/></ListItemIcon>
                    {open && <ListItemText primary="Erfahrungsberichte"/>}
                  </ListItemButton>
                </Tooltip>
            )}
            <Tooltip title={open ? '' : 'Globale Suche'} placement="right">
              <ListItemButton component={NavLink as ElementType} to="/search" {...navClick}>
                <ListItemIcon sx={{minWidth: 40}}><SearchOutlinedIcon/></ListItemIcon>
                {open && <ListItemText primary="Globale Suche"/>}
              </ListItemButton>
            </Tooltip>
            <Tooltip title={open ? '' : 'Meine Sammlungen'} placement="right">
              <ListItemButton component={NavLink as ElementType} to="/collections" {...navClick}>
                <ListItemIcon sx={{minWidth: 40}}><CollectionsBookmarkIcon/></ListItemIcon>
                {open && <ListItemText primary="Meine Sammlungen"/>}
              </ListItemButton>
            </Tooltip>
            {user?.role === 'ADMIN' && (
                <>
                  <Divider sx={{my: 1}}/>
                  {open && (
                      <Typography variant="caption" color="text.secondary"
                                  sx={{px: 2, py: 0.5, display: 'block'}}>
                        Administration
                      </Typography>
                  )}
                  <Tooltip title={open ? '' : 'Benutzer'} placement="right">
                    <ListItemButton component={NavLink as ElementType}
                                    to="/admin/users" {...navClick}>
                      <ListItemIcon sx={{minWidth: 40}}><PeopleOutlinedIcon/></ListItemIcon>
                      {open && <ListItemText primary="Benutzer"/>}
                    </ListItemButton>
                  </Tooltip>
                  <Tooltip title={open ? '' : 'Gruppen & Rechte'} placement="right">
                    <ListItemButton component={NavLink as ElementType}
                                    to="/admin/groups" {...navClick}>
                      <ListItemIcon sx={{minWidth: 40}}><GroupsOutlinedIcon/></ListItemIcon>
                      {open && <ListItemText primary="Gruppen & Rechte"/>}
                    </ListItemButton>
                  </Tooltip>
                  <Tooltip title={open ? '' : 'Jobs'} placement="right">
                    <ListItemButton component={NavLink as ElementType}
                                    to="/admin/jobs" {...navClick}>
                      <ListItemIcon sx={{minWidth: 40}}><WorkHistoryOutlinedIcon/></ListItemIcon>
                      {open && <ListItemText primary="Jobs"/>}
                    </ListItemButton>
                  </Tooltip>
                  <Tooltip title={open ? '' : 'Server-Log'} placement="right">
                    <ListItemButton component={NavLink as ElementType}
                                    to="/admin/logs" {...navClick}>
                      <ListItemIcon sx={{minWidth: 40}}><ArticleOutlinedIcon/></ListItemIcon>
                      {open && <ListItemText primary="Server-Log"/>}
                    </ListItemButton>
                  </Tooltip>
                  <Tooltip title={open ? '' : 'Bulk-Ordner-Import'} placement="right">
                    <ListItemButton component={NavLink as ElementType}
                                    to="/admin/bulk-import" {...navClick}>
                      <ListItemIcon
                          sx={{minWidth: 40}}><DriveFolderUploadOutlinedIcon/></ListItemIcon>
                      {open && <ListItemText primary="Bulk-Ordner-Import"/>}
                    </ListItemButton>
                  </Tooltip>
                  <Tooltip title={open ? '' : 'Papierkorb'} placement="right">
                    <ListItemButton component={NavLink as ElementType} to="/trash" {...navClick}>
                      <ListItemIcon sx={{minWidth: 40}}><DeleteOutlineOutlinedIcon/></ListItemIcon>
                      {open && <ListItemText primary="Papierkorb"/>}
                    </ListItemButton>
                  </Tooltip>
                </>
            )}
          </List>
        </Box>
      </Drawer>
  );
}
