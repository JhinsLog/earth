/**
 * 지도 플레어 스프라이트. 색마다 미리 그려 둔다.
 *
 * 예전에는 흰 십자 하나를 SDF로 올려 색만 입혔다. SDF는 한 가지 색·한 가지 모양의 외곽선만 표현해서, 빛이 번지는 정도나
 * 코어와 광선의 색 차이를 담을 수 없었다(화면에서 평평한 노란 십자로 보였다). 그래서 색마다 RGBA 이미지를 그린다.
 * 분류 색은 몇 개뿐이라 이미지도 몇 장이다.
 *
 * 두 장으로 나눈 이유: 본체(빛무리 + 코어 + 짧은 광선)는 늘 같은 밝기로 두고, 긴 광선만 따로 깜빡이게 하면 플레어가 꺼졌다
 * 켜지는 게 아니라 반짝이는 것처럼 보인다(MapGlobe의 반짝임 루프).
 */

/** 이미지 한 변(장치 픽셀). 화면 픽셀 기준 절반 크기로 올린다(pixelRatio 2). */
export const FLARE_SPRITE_SIZE = 192
export const FLARE_SPRITE_PIXEL_RATIO = 2

function canvas2d(): [CanvasRenderingContext2D, number] {
  const canvas = document.createElement('canvas')
  canvas.width = FLARE_SPRITE_SIZE
  canvas.height = FLARE_SPRITE_SIZE
  return [canvas.getContext('2d')!, FLARE_SPRITE_SIZE / 2]
}

function rgba(hex: string, alpha: number): string {
  const n = parseInt(hex.slice(1), 16)
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${alpha})`
}

/**
 * 가운데가 굵고 끝으로 갈수록 가늘어지며 사라지는 광선 하나. 망원경 사진의 회절 광선처럼 보이게 한다.
 * 위쪽(-y)으로 그린 뒤 회전해 쓴다.
 */
function ray(ctx: CanvasRenderingContext2D, length: number, width: number, inner: string, outer: string) {
  const gradient = ctx.createLinearGradient(0, 0, 0, -length)
  gradient.addColorStop(0, inner)
  gradient.addColorStop(0.35, outer)
  gradient.addColorStop(1, 'rgba(255, 255, 255, 0)')
  ctx.fillStyle = gradient
  ctx.beginPath()
  ctx.moveTo(-width, 0)
  ctx.quadraticCurveTo(-width * 0.25, -length * 0.35, 0, -length)
  ctx.quadraticCurveTo(width * 0.25, -length * 0.35, width, 0)
  ctx.closePath()
  ctx.fill()
}

function rays(ctx: CanvasRenderingContext2D, center: number, count: number, offset: number,
              length: number, width: number, inner: string, outer: string) {
  ctx.save()
  ctx.translate(center, center)
  ctx.rotate(offset)
  for (let i = 0; i < count; i++) {
    ray(ctx, length, width, inner, outer)
    ctx.rotate((Math.PI * 2) / count)
  }
  ctx.restore()
}

/** 플레어 본체: 색 빛무리, 대각선 짧은 광선, 색 테두리를 두른 흰 코어. */
export function createFlareBodyImage(color: string): ImageData {
  const [ctx, c] = canvas2d()
  ctx.globalCompositeOperation = 'lighter'

  const glow = ctx.createRadialGradient(c, c, 0, c, c, c * 0.62)
  glow.addColorStop(0, rgba(color, 0.55))
  glow.addColorStop(0.25, rgba(color, 0.28))
  glow.addColorStop(0.6, rgba(color, 0.08))
  glow.addColorStop(1, rgba(color, 0))
  ctx.fillStyle = glow
  ctx.fillRect(0, 0, FLARE_SPRITE_SIZE, FLARE_SPRITE_SIZE)

  rays(ctx, c, 4, Math.PI / 4, c * 0.42, 2.2, 'rgba(255, 255, 255, 0.75)', rgba(color, 0.35))
  rays(ctx, c, 4, 0, c * 0.5, 3.2, 'rgba(255, 255, 255, 0.9)', rgba(color, 0.5))

  const rim = ctx.createRadialGradient(c, c, 0, c, c, c * 0.17)
  rim.addColorStop(0, 'rgba(255, 255, 255, 1)')
  rim.addColorStop(0.35, 'rgba(255, 255, 255, 0.95)')
  rim.addColorStop(0.6, rgba(color, 0.85))
  rim.addColorStop(1, rgba(color, 0))
  ctx.fillStyle = rim
  ctx.beginPath()
  ctx.arc(c, c, c * 0.17, 0, Math.PI * 2)
  ctx.fill()

  return ctx.getImageData(0, 0, FLARE_SPRITE_SIZE, FLARE_SPRITE_SIZE)
}

/** 반짝임: 본체보다 긴 상하좌우 광선. 투명도를 오르내려 반짝이게 한다. */
export function createFlareSparkleImage(color: string): ImageData {
  const [ctx, c] = canvas2d()
  ctx.globalCompositeOperation = 'lighter'
  rays(ctx, c, 4, 0, c * 0.98, 2.6, 'rgba(255, 255, 255, 1)', rgba(color, 0.6))
  const flash = ctx.createRadialGradient(c, c, 0, c, c, c * 0.12)
  flash.addColorStop(0, 'rgba(255, 255, 255, 0.9)')
  flash.addColorStop(1, 'rgba(255, 255, 255, 0)')
  ctx.fillStyle = flash
  ctx.fillRect(0, 0, FLARE_SPRITE_SIZE, FLARE_SPRITE_SIZE)
  return ctx.getImageData(0, 0, FLARE_SPRITE_SIZE, FLARE_SPRITE_SIZE)
}
