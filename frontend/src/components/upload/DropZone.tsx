import {Box, Typography} from '@mui/material';
import UploadFileOutlinedIcon from '@mui/icons-material/UploadFileOutlined';
import {type DragEvent, useCallback, useState} from 'react';
import {useUpload} from '../../hooks/useUpload';
import UploadProgress from './UploadProgress';

export default function DropZone({folderId}: Readonly<{ folderId: number }>) {
  const {uploads, uploadFiles, clearCompleted} = useUpload(folderId);
  const [dragging, setDragging] = useState(false);

  const handleDrop = useCallback((e: DragEvent) => {
    e.preventDefault();
    setDragging(false);
    uploadFiles(Array.from(e.dataTransfer.files));
  }, [uploadFiles]);

  return (
      <Box>
        <Box onDragOver={e => {
          e.preventDefault();
          setDragging(true);
        }}
             onDragLeave={() => setDragging(false)} onDrop={handleDrop} component="label"
             sx={{
               display: 'flex',
               flexDirection: 'column',
               alignItems: 'center',
               justifyContent: 'center',
               border: '2px dashed',
               borderRadius: 2,
               p: 4,
               cursor: 'pointer',
               borderColor: dragging ? 'primary.main' : 'divider',
               bgcolor: dragging ? 'action.hover' : 'background.default'
             }}>
          <input type="file" multiple hidden onChange={e => {
            uploadFiles(Array.from(e.target.files ?? []));
            e.target.value = '';
          }}/>
          <UploadFileOutlinedIcon sx={{fontSize: 40, color: 'text.secondary', mb: 1}}/>
          <Typography variant="body2" color="text.secondary">Dateien hier ablegen oder klicken zum
            Hochladen</Typography>
          <Typography variant="caption" color="text.disabled" sx={{mt: 0.5}}>Untertitel und
            Transkript werden automatisch im Hintergrund erstellt — das kann einige Minuten
            dauern</Typography>
        </Box>
        {uploads.length > 0 && <UploadProgress uploads={uploads} onClear={clearCompleted}/>}
      </Box>
  );
}
