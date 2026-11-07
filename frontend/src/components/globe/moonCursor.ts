/**
 * 달빛 비춰 보기(우클릭)를 누르고 있는 동안의 마우스 포인터. 은빛 초승달에 옅은 달무리를 두른다.
 * 가리키는 지점(핫스폿)은 달의 중심이다 — 빛도 그 지점을 중심으로 비춘다.
 */
const MOON_SVG = `<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32">
<defs>
<radialGradient id="halo"><stop offset="0.45" stop-color="#c8dcff" stop-opacity="0.35"/><stop offset="1" stop-color="#c8dcff" stop-opacity="0"/></radialGradient>
<mask id="crescent"><circle cx="16" cy="16" r="8" fill="#fff"/><circle cx="20" cy="12.5" r="7" fill="#000"/></mask>
</defs>
<circle cx="16" cy="16" r="15" fill="url(#halo)"/>
<circle cx="16" cy="16" r="8" fill="#eef4ff" mask="url(#crescent)" stroke="#1b2a44" stroke-width="0.6"/>
</svg>`

export const MOON_CURSOR = `url("data:image/svg+xml,${encodeURIComponent(MOON_SVG)}") 16 16, crosshair`
