/** 브라우저 언어 설정에서 OpenMapTiles 라벨 필드(name:xx)에 쓸 2자리 언어 코드를 뽑아낸다. */
export function detectLabelLanguage(): string {
  const lang = (typeof navigator !== 'undefined' && navigator.language) || 'en'
  return lang.split('-')[0].toLowerCase()
}
