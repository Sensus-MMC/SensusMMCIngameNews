import { PNG } from 'pngjs';
import fs from 'node:fs';
import path from 'node:path';

const source = path.resolve(process.argv[2] || 'C:/Users/admin/Downloads/gui_newstablet.png');
const target = path.resolve(process.argv[3] || '../src/main/resources/assets/sensusmmc/textures/gui/gui_newstablet.png');
const image = PNG.sync.read(fs.readFileSync(source));
let minX = image.width;
let minY = image.height;
let maxX = -1;
let maxY = -1;
for (let y = 0; y < image.height; y += 1) {
  for (let x = 0; x < image.width; x += 1) {
    const alpha = image.data[(y * image.width + x) * 4 + 3];
    if (alpha === 0) continue;
    if (x < minX) minX = x;
    if (y < minY) minY = y;
    if (x > maxX) maxX = x;
    if (y > maxY) maxY = y;
  }
}
if (maxX < 0) throw new Error('image has no opaque pixels');
const width = maxX - minX + 1;
const height = maxY - minY + 1;
const cropped = new PNG({ width, height });
PNG.bitblt(image, cropped, minX, minY, width, height, 0, 0);
fs.mkdirSync(path.dirname(target), { recursive: true });
fs.writeFileSync(target, PNG.sync.write(cropped));
process.stdout.write(JSON.stringify({ source, target, sourceWidth: image.width, sourceHeight: image.height, cropX: minX, cropY: minY, width, height }) + '\n');
