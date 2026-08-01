import {useEffect, useRef} from 'react';
import {Box} from '@mui/material';
import videojs from 'video.js';
import type Player from 'video.js/dist/types/player';
import 'video.js/dist/video-js.css';

videojs.addLanguage('de', {
  "Play": "Abspielen", "Pause": "Pause", "Replay": "Wiederholen",
  "Current Time": "Aktuelle Zeit", "Duration": "Dauer", "Remaining Time": "Verbleibende Zeit",
  "Fullscreen": "Vollbild", "Exit Fullscreen": "Vollbild beenden",
  "Mute": "Ton aus", "Unmute": "Ton an", "Volume Level": "Lautstärke",
  "Playback Rate": "Wiedergabegeschwindigkeit",
  "Subtitles": "Untertitel", "subtitles off": "Untertitel aus",
  "Captions": "Untertitel", "captions off": "Untertitel aus",
  "captions settings": "Untertiteleinstellungen", "subtitles settings": "Untertiteleinstellungen",
  ", selected": ", ausgewählt", "off": "aus",
  "Audio Track": "Audiospur", "Picture-in-Picture": "Bild-in-Bild",
  "Exit Picture-in-Picture": "Bild-in-Bild beenden",
  "Close": "Schließen", "Done": "Fertig", "Reset": "Zurücksetzen",
  "Play Video": "Video abspielen", "LIVE": "LIVE",
  "No compatible source was found for this media.": "Kein kompatibles Format für dieses Medium gefunden.",
});

interface Props {
  src: string;
  mimeType?: string;
  audioOnly?: boolean;
  subtitles?: Array<{ src: string; label: string; language: string }>;
  initialTime?: number;
  onReady?: (seekTo: (t: number) => void, getCurrentTime: () => number) => void;
  onPlayerReady?: (player: Player) => void;
}

export default function VideoPlayer({
                                      src,
                                      mimeType = 'video/mp4',
                                      audioOnly = false,
                                      subtitles = [],
                                      initialTime,
                                      onReady,
                                      onPlayerReady
                                    }: Readonly<Props>) {
  const containerRef = useRef<HTMLDivElement>(null);
  const playerRef = useRef<Player | null>(null);

  useEffect(() => {
    if (!containerRef.current) return;
    const el = document.createElement('video-js');
    el.classList.add('vjs-big-play-centered');
    containerRef.current.appendChild(el);
    const player = videojs(el, {
      controls: true, fill: true, language: 'de',
      sources: [{src, type: mimeType}],
      tracks: subtitles.map(s => ({
        src: s.src,
        kind: 'subtitles' as const,
        label: s.label,
        srclang: s.language
      })),
    });
    playerRef.current = player;
    player.ready(() => {
      if (subtitles.length > 0) {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const trackList = player.textTracks() as any as TextTrack[];
        const firstTrack = Array.from(trackList).find(
            t => t.kind === 'subtitles' || t.kind === 'captions'
        );
        if (firstTrack) firstTrack.mode = 'showing';
      }
      if (initialTime != null && initialTime > 0) {
        player.one('loadedmetadata', () => {
          player.currentTime(initialTime);
        });
      }
      onPlayerReady?.(player);
      onReady?.(
          (t) => {
            playerRef.current?.currentTime(t);
          },
          () => playerRef.current?.currentTime() ?? 0,
      );
    });
    return () => {
      player.dispose();
      playerRef.current = null;
    };
  }, [src, subtitles]);

  const audioHeight = subtitles.length > 0 ? '360px' : '112px';

  return (
      <Box
          ref={containerRef}
          data-vjs-player
          sx={audioOnly ? {
            width: '100%',
            position: 'relative',
            height: audioHeight,
            mb: 1,
            '& .video-js': {width: '100%', height: audioHeight},
            // push subtitle text up from the control bar
            '& .vjs-text-track-display': {bottom: '3em !important'},
            '& .vjs-menu-button-popup .vjs-menu': {
              width: '13em !important',
              left: '-6em !important'
            },
          } : {
            width: '100%',
            height: 0,
            paddingBottom: 'min(56.25%, 75vh)',
            position: 'relative',
            '& .video-js': {position: 'absolute', inset: 0, width: '100%', height: '100%'},
            '& .vjs-text-track-display': {fontSize: '65% !important'},
            '& .vjs-menu-button-popup .vjs-menu': {
              width: '13em !important',
              left: '-6em !important'
            },
          }}
      />
  );
}
