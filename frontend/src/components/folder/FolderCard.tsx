import {Card, CardActionArea, CardContent, IconButton, Tooltip, Typography} from '@mui/material';
import FolderOutlinedIcon from '@mui/icons-material/FolderOutlined';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import React, {type MouseEvent, useRef, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import type {FolderDto} from '../../types/api';

interface Props {
  folder: FolderDto;
  onMenuOpen: (e: MouseEvent, f: FolderDto) => void;
  showMenu?: boolean;
}

export default function FolderCard({folder, onMenuOpen, showMenu = false}: Readonly<Props>) {
  const navigate = useNavigate();
  const textRef = useRef<HTMLSpanElement>(null);
  const [isTruncated, setIsTruncated] = useState(false);

  const checkTruncation = () => {
    const el = textRef.current;
    if (el) setIsTruncated(el.scrollWidth > el.clientWidth);
  };

  return (
      <Card variant="outlined" sx={{position: 'relative', '&:hover': {boxShadow: 2}}}>
        <CardActionArea onClick={() => navigate(`/folders/${folder.id}`)}>
          <CardContent sx={{display: 'flex', flexDirection: 'column', alignItems: 'center', py: 3}}>
            <FolderOutlinedIcon sx={{fontSize: 48, color: 'primary.main', mb: 1}}/>
            <Tooltip title={folder.name} placement="bottom" enterDelay={300}
                     disableHoverListener={!isTruncated}>
              <Typography
                  ref={textRef}
                  variant="body2"
                  align="center"
                  noWrap
                  sx={{maxWidth: 140}}
                  onMouseEnter={checkTruncation}
              >
                {folder.name}
              </Typography>
            </Tooltip>
          </CardContent>
        </CardActionArea>
        {showMenu && (
            <Tooltip title="Optionen">
              <IconButton size="small" onClick={(e) => {
                e.stopPropagation();
                onMenuOpen(e, folder);
              }}
                          sx={{position: 'absolute', top: 4, right: 4}} aria-label="folder options">
                <MoreVertIcon fontSize="small"/>
              </IconButton>
            </Tooltip>
        )}
      </Card>
  );
}
