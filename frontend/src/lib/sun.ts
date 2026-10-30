const DEG = Math.PI / 180

/**
 * 지금 태양이 바로 머리 위에 있는 지점(태양 직하점).
 *
 * 홈 지구본의 낮·밤 경계를 실제 시각에 맞추는 데 쓴다. 외부 데이터 없이 시각만으로 정해진다.
 * 미국 해군천문대 천문연감(Astronomical Almanac)의 저정밀 태양 위치식으로, 1950~2050년에 오차 약 0.01°다.
 * 태양의 적경·적위를 구하고 그리니치 항성시를 빼서 경도로 바꾼다.
 *
 * 처음엔 NOAA의 일반 태양 위치식(연중 각도의 삼각급수)을 썼는데, 이 식과 비교하니 위도가 최대 0.44°
 * 어긋났다(2026~2027년, 7시간 간격). 길이는 비슷해 더 정확한 쪽을 택했다.
 */
export function subsolarPoint(date: Date): { latitude: number; longitude: number } {
  // J2000.0(2000-01-01 12:00 TT)부터 지난 일수. UT와 TT의 차이(약 70초)는 무시한다.
  const n = date.getTime() / 86_400_000 + 2440587.5 - 2451545.0
  const meanLongitude = 280.46 + 0.9856474 * n
  const meanAnomaly = (357.528 + 0.9856003 * n) * DEG
  const eclipticLongitude =
    (meanLongitude + 1.915 * Math.sin(meanAnomaly) + 0.02 * Math.sin(2 * meanAnomaly)) * DEG
  const obliquity = (23.439 - 0.0000004 * n) * DEG

  const rightAscension = Math.atan2(
    Math.cos(obliquity) * Math.sin(eclipticLongitude),
    Math.cos(eclipticLongitude),
  )
  const declination = Math.asin(Math.sin(obliquity) * Math.sin(eclipticLongitude))
  // 그리니치 평균 항성시(도). 태양의 적경에서 빼면 태양이 머리 위에 있는 경도가 된다.
  const siderealDeg = (18.697374558 + 24.06570982441908 * n) * 15

  const longitude = rightAscension / DEG - siderealDeg
  return {
    latitude: declination / DEG,
    longitude: ((((longitude + 180) % 360) + 360) % 360) - 180,
  }
}
