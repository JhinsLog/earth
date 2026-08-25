/** 이벤트 마커용 십자 성광(4-Point Star Flare) SDF 텍스처를 캔버스로 그려 반환한다. */
export function createStarFlareImageData(): ImageData {
  const size = 128
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')!
  const center = size / 2

  ctx.clearRect(0, 0, size, size)

  const radialGlow = ctx.createRadialGradient(center, center, 0, center, center, center)
  radialGlow.addColorStop(0, 'rgba(255, 255, 255, 1)')
  radialGlow.addColorStop(0.2, 'rgba(255, 255, 255, 0.85)')
  radialGlow.addColorStop(0.5, 'rgba(255, 255, 255, 0.35)')
  radialGlow.addColorStop(1, 'rgba(255, 255, 255, 0)')
  ctx.fillStyle = radialGlow
  ctx.fillRect(0, 0, size, size)

  ctx.fillStyle = 'rgba(255, 255, 255, 0.95)'

  ctx.beginPath()
  ctx.moveTo(center, 2)
  ctx.quadraticCurveTo(center, center, center + 6, center)
  ctx.quadraticCurveTo(center, center, center, size - 2)
  ctx.quadraticCurveTo(center, center, center - 6, center)
  ctx.quadraticCurveTo(center, center, center, 2)
  ctx.closePath()
  ctx.fill()

  ctx.beginPath()
  ctx.moveTo(2, center)
  ctx.quadraticCurveTo(center, center, center, center - 6)
  ctx.quadraticCurveTo(center, center, size - 2, center)
  ctx.quadraticCurveTo(center, center, center, center + 6)
  ctx.quadraticCurveTo(center, center, 2, center)
  ctx.closePath()
  ctx.fill()

  ctx.save()
  ctx.translate(center, center)
  ctx.rotate(Math.PI / 4)
  ctx.beginPath()
  ctx.moveTo(0, -size * 0.3)
  ctx.quadraticCurveTo(0, 0, 3, 0)
  ctx.quadraticCurveTo(0, 0, 0, size * 0.3)
  ctx.quadraticCurveTo(0, 0, -3, 0)
  ctx.quadraticCurveTo(0, 0, 0, -size * 0.3)
  ctx.closePath()
  ctx.fill()
  ctx.restore()

  const coreGrad = ctx.createRadialGradient(center, center, 0, center, center, size * 0.15)
  coreGrad.addColorStop(0, '#ffffff')
  coreGrad.addColorStop(1, 'rgba(255, 255, 255, 0)')
  ctx.fillStyle = coreGrad
  ctx.beginPath()
  ctx.arc(center, center, size * 0.15, 0, Math.PI * 2)
  ctx.fill()

  return ctx.getImageData(0, 0, size, size)
}
