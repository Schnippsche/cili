import { Box, IconButton, Modal, Tooltip } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';

interface Props { src: string; alt: string; open: boolean; onClose: () => void; }

export default function ImageViewer({ src, alt, open, onClose }: Readonly<Props>) {
  return (
    <Modal open={open} onClose={onClose}>
      <Box sx={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%,-50%)', outline: 'none' }}>
        <Tooltip title="Schließen">
          <IconButton onClick={onClose} sx={{ position: 'absolute', top: -40, right: 0, color: 'white' }}>
            <CloseIcon />
          </IconButton>
        </Tooltip>
        {/* image-orientation: from-image lässt den Browser EXIF-Orientierung anwenden */}
        <img src={src} alt={alt} style={{ maxWidth: '90vw', maxHeight: '90vh', display: 'block', imageOrientation: 'from-image' }} />
      </Box>
    </Modal>
  );
}
