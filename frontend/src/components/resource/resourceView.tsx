import type {SxProps, Theme} from '@mui/material';
import InsertDriveFileOutlinedIcon from '@mui/icons-material/InsertDriveFileOutlined';
import VideoFileOutlinedIcon from '@mui/icons-material/VideoFileOutlined';
import ImageOutlinedIcon from '@mui/icons-material/ImageOutlined';
import PictureAsPdfOutlinedIcon from '@mui/icons-material/PictureAsPdfOutlined';
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined';
import CodeOutlinedIcon from '@mui/icons-material/CodeOutlined';
import AudioFileOutlinedIcon from '@mui/icons-material/AudioFileOutlined';
import type {NavigateFunction} from 'react-router-dom';
import {getDownloadUrl} from '../../api/resources';

export const isVideo = (m: string) => m.startsWith('video/');
export const isAudio = (m: string) => m.startsWith('audio/');
export const isImage = (m: string) => m.startsWith('image/');
export const isText = (m: string) =>
    m.startsWith('text/') || m === 'application/json' || m === 'application/x-subrip';
export const isPdf = (m: string) => m === 'application/pdf';
export const isDocument = (m: string) =>
    m === 'application/pdf' ||
    m.startsWith('application/vnd.openxmlformats') ||
    m.startsWith('application/vnd.ms-') ||
    m === 'application/msword' ||
    m.startsWith('application/vnd.oasis.opendocument');

/** True wenn die Ressource in-App angezeigt werden kann (Player, PDF-Viewer, Lightbox, Text-Editor). */
export const hasViewer = (m: string) =>
    isVideo(m) || isAudio(m) || isImage(m) || isDocument(m) || isText(m);

export function MimeIcon({mimeType, sx}: Readonly<{ mimeType: string; sx?: SxProps<Theme> }>) {
  const merged = {fontSize: 40, color: 'text.disabled', ...(sx as object)} as SxProps<Theme>;
  if (isVideo(mimeType)) return <VideoFileOutlinedIcon sx={merged}/>;
  if (isAudio(mimeType)) return <AudioFileOutlinedIcon sx={merged}/>;
  if (isImage(mimeType)) return <ImageOutlinedIcon sx={merged}/>;
  if (isPdf(mimeType)) return <PictureAsPdfOutlinedIcon sx={merged}/>;
  if (isDocument(mimeType)) return <DescriptionOutlinedIcon sx={merged}/>;
  if (isText(mimeType)) return <CodeOutlinedIcon sx={merged}/>;
  return <InsertDriveFileOutlinedIcon sx={merged}/>;
}

interface OpenableResource {
  id: number;
  folderId: number | null;
  mimeType: string;
}

/**
 * Öffnet eine Ressource: Medien/PDF/Bild im Viewer des Ordners, Textdateien im Editor,
 * alles andere als Download in einem neuen Tab.
 */
export function openResource(navigate: NavigateFunction, resource: OpenableResource): void {
  const {id, folderId, mimeType} = resource;
  if (folderId != null && isVideo(mimeType)) navigate(`/folders/${folderId}?view=${id}&type=video`);
  else if (folderId != null && isAudio(mimeType)) navigate(`/folders/${folderId}?view=${id}&type=audio`);
  else if (folderId != null && isImage(mimeType)) navigate(`/folders/${folderId}?view=${id}&type=image`);
  else if (folderId != null && isDocument(mimeType)) navigate(`/folders/${folderId}?view=${id}&type=pdf`);
  else if (isText(mimeType)) navigate(`/resources/${id}/edit`);
  else globalThis.open(getDownloadUrl(id), '_blank');
}
