// Offline design preview only. The device's launcher supplies the actual palette
// and mask; this file never changes the production Android vector or color assets.
// Usage: node tools/preview-themed-icon.cjs <path-to-sharp-package>
const fs = require('node:fs');
const path = require('node:path');
const sharp = require(process.argv[2] || 'sharp');
const root = path.resolve(__dirname, '..');
const xml = fs.readFileSync(path.join(root, 'res/drawable/ic_lle_monochrome.xml'), 'utf8');
const out = path.join(root, 'build/icon-generation/material-you');
fs.mkdirSync(out, {recursive: true});

const attributes = tag => Object.fromEntries([...tag.matchAll(/android:(\w+)="([^"]*)"/g)]
    .map(m => [m[1], m[2]]));
const paths = [...xml.matchAll(/<path\s[\s\S]*?\/>/g)].map(m => attributes(m[0]));
function mark(color) {
    return paths.map(p => `<path d="${p.pathData}" fill="${p.fillColor === '#FFFFFFFF' ? color : 'none'}"`
        + (p.strokeColor ? ` stroke="${color}" stroke-width="${p.strokeWidth}" stroke-linecap="${p.strokeLineCap || 'butt'}" stroke-linejoin="${p.strokeLineJoin || 'miter'}"` : '')
        + '/>').join('');
}
const group = attributes(xml.match(/<group[^>]*>/)[0]);
function svg(fg, bg) {
    // Adaptive icons expose the central 72dp of the 108dp drawable at rest.
    return `<svg xmlns="http://www.w3.org/2000/svg" width="432" height="432" viewBox="0 0 432 432">
      <defs><clipPath id="mask"><rect width="432" height="432" rx="112"/></clipPath></defs>
      <g clip-path="url(#mask)"><rect width="432" height="432" fill="${bg}"/>
      <g transform="translate(-108 -108) scale(1.5)"><g transform="translate(0 ${group.translateY}) translate(${group.pivotX} ${group.pivotY}) scale(${group.scaleX} ${group.scaleY}) translate(-${group.pivotX} -${group.pivotY})">${mark(fg)}</g></g></g></svg>`;
}
(async () => {
    const variants = [
        ['light', '#244D46', '#CDEAE0'],
        ['dark', '#A7D1C5', '#173A33'],
        ['lavender', '#4D426D', '#E9DFFF'],
    ];
    const buffers = [];
    for (const [name, fg, bg] of variants) {
        const rendered = await sharp(Buffer.from(svg(fg, bg))).png().toBuffer();
        fs.writeFileSync(path.join(out, `lle-themed-${name}.png`), rendered);
        buffers.push(rendered);
    }
    await sharp({create:{width:1344,height:480,channels:4,background:'#F1F1F1'}})
        .composite(buffers.map((input,i) => ({input,left:12+i*450,top:24})))
        .png().toFile(path.join(out,'lle-themed-preview.png'));
    console.log(out);
})();
