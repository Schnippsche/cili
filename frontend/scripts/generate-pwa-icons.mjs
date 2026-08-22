// One-off generator: rasterizes public/favicon.svg into the PNG icons the
// PWA manifest needs. Re-run manually after changing favicon.svg.
import sharp from 'sharp';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const publicDir = path.join(__dirname, '..', 'public');
const svg = readFileSync(path.join(publicDir, 'favicon.svg'));

const BLUE = '#2563EB';

async function plainIcon(size, outName) {
  await sharp(svg, { density: 384 })
    .resize(size, size)
    .png()
    .toFile(path.join(publicDir, outName));
  console.log('wrote', outName);
}

// Maskable icons need the artwork confined to the ~80% "safe zone" circle,
// with the background color filling the rest of the canvas edge-to-edge —
// otherwise Android crops the triangle when applying its own mask shape.
async function maskableIcon(size, outName) {
  const artSize = Math.round(size * 0.6);
  const art = await sharp(svg, { density: 384 }).resize(artSize, artSize).png().toBuffer();
  await sharp({
    create: {
      width: size,
      height: size,
      channels: 4,
      background: BLUE,
    },
  })
    .composite([{ input: art, gravity: 'center' }])
    .png()
    .toFile(path.join(publicDir, outName));
  console.log('wrote', outName);
}

await plainIcon(192, 'pwa-192x192.png');
await plainIcon(512, 'pwa-512x512.png');
await plainIcon(180, 'apple-touch-icon.png');
await maskableIcon(512, 'pwa-maskable-512x512.png');
