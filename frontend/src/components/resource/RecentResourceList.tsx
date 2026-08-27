import {Avatar, Box, List, ListItemButton, ListItemText, Typography} from '@mui/material';
import {useNavigate} from 'react-router-dom';
import type {RecentResourceDto} from '../../types/api';
import {getThumbnailUrl} from '../../api/resources';
import {useAuthenticatedUrl} from '../../hooks/useAuthenticatedUrl';
import {formatRelativeTime} from '../../utils/formatRelativeTime';
import {isImage, MimeIcon, openResource} from './resourceView';

interface Props {
  items: RecentResourceDto[];
}

function RecentRow({item}: Readonly<{ item: RecentResourceDto }>) {
  const navigate = useNavigate();
  const {resource, folderName} = item;

  const wantsThumb = resource.thumbnailStatus === 'DONE' || isImage(resource.mimeType);
  const thumbSrc = wantsThumb
      ? getThumbnailUrl(resource.id, 'small',
          resource.thumbnailStatus === 'DONE' ? resource.storedName : undefined)
      : null;
  const thumbUrl = useAuthenticatedUrl(thumbSrc, isImage(resource.mimeType) ? 3 : 0);

  const displayName = resource.metadata?.title || resource.originalName;

  return (
      <ListItemButton
          divider
          onClick={() => openResource(navigate, resource)}
          sx={{gap: 1.5, py: 1}}
      >
        <Avatar
            variant="rounded"
            src={thumbUrl ?? undefined}
            alt=""
            sx={{width: 40, height: 40, bgcolor: 'action.hover'}}
        >
          <MimeIcon mimeType={resource.mimeType} sx={{fontSize: 22}}/>
        </Avatar>
        <ListItemText
            primary={<Typography variant="body2" noWrap>{displayName}</Typography>}
            secondary={
              <Typography variant="caption" color="text.secondary" noWrap component="span">
                {folderName && <Box component="span">{folderName}</Box>}
                {folderName && <Box component="span" sx={{mx: 0.5}}>·</Box>}
                <Box component="span">{formatRelativeTime(resource.createdAt)}</Box>
              </Typography>
            }
        />
      </ListItemButton>
  );
}

export default function RecentResourceList({items}: Readonly<Props>) {
  if (items.length === 0) {
    return <Typography color="text.secondary" variant="body2">Noch keine Dateien.</Typography>;
  }
  return (
      <Box sx={{border: 1, borderColor: 'divider', borderRadius: 1, overflow: 'hidden'}}>
        <List disablePadding>
          {items.map(it => <RecentRow key={it.resource.id} item={it}/>)}
        </List>
      </Box>
  );
}
