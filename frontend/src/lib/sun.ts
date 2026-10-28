/**
 * 지금 태양이 바로 머리 위에 있는 지점(태양 직하점).
 *
 * 홈 지구본의 낮·밤 경계를 실제 시각에 맞추는 데 쓴다. 천문 계산의 근사식으로 균시차(최대 약 16분,
 * 경도 약 4°)와 연중 궤도 이심률은 무시한다. 화면에서 경계선이 몇 도 어긋나는 정도라 연출에는 충분하다.
 */
export function subsolarPoint(date: Date): { latitude: number; longitude: number } {
  const startOfYear = Date.UTC(date.getUTCFullYear(), 0, 1)
  const dayOfYear = (date.getTime() - startOfYear) / 86_400_000
  // 동지(12월 21일 무렵, 연초에서 약 -10일)에 남회귀선(-23.44°)에 오는 코사인 근사.
  const latitude = -23.44 * Math.cos(((2 * Math.PI) / 365) * (dayOfYear + 10))
  const utcHours = date.getUTCHours() + date.getUTCMinutes() / 60 + date.getUTCSeconds() / 3600
  // UTC 정오에 본초자오선(0°) 위, 한 시간에 15°씩 서쪽으로 간다.
  let longitude = (12 - utcHours) * 15
  if (longitude > 180) longitude -= 360
  if (longitude < -180) longitude += 360
  return { latitude, longitude }
}
