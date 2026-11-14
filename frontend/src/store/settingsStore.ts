import { create } from 'zustand'
import { trackAction } from '../lib/telemetry'

interface SettingsState {
  /** 홈 라스 밤 지역의 도시 불빛. 플레어와 겹쳐 보여 기본은 끈다. 밤 지역의 도시 위치를 찾을 때 켠다. */
  cityLights: boolean
  setCityLights: (on: boolean) => void
  /** 공공데이터(외부 API)로 만든 플레어를 보일지. 기본은 켠다. 사람이 남긴 플레어만 보고 싶을 때 끈다. */
  publicFlares: boolean
  setPublicFlares: (on: boolean) => void
  /** 지도를 도시 단위로 확대했을 때 지하철·전철 노선을 보일지. 기본은 켠다. 플레어보다 아래에 그려져 가리지 않는다. */
  subwayLines: boolean
  setSubwayLines: (on: boolean) => void
}

const CITY_LIGHTS_KEY = 'earth:cityLights'
const PUBLIC_FLARES_KEY = 'earth:publicFlares'
const SUBWAY_LINES_KEY = 'earth:subwayLines'

// 화면 취향일 뿐이라 브라우저에만 둔다. 저장소를 못 쓰는 환경(사생활 보호 창 등)에서는 기본값으로 동작한다.
function readCityLights(): boolean {
  try {
    return localStorage.getItem(CITY_LIGHTS_KEY) === 'on'
  } catch {
    return false
  }
}

/** 따로 끄지 않았으면 켠다. */
function readOnByDefault(key: string): boolean {
  try {
    return localStorage.getItem(key) !== 'off'
  } catch {
    return true
  }
}

function save(key: string, on: boolean) {
  try {
    localStorage.setItem(key, on ? 'on' : 'off')
  } catch {
    // 저장 실패는 이번 방문 동안만 적용되는 것으로 충분하다.
  }
}

export const useSettingsStore = create<SettingsState>((set) => ({
  cityLights: readCityLights(),
  setCityLights: (on) => {
    trackAction('settings.change', { key: 'cityLights', on })
    save(CITY_LIGHTS_KEY, on)
    set({ cityLights: on })
  },
  publicFlares: readOnByDefault(PUBLIC_FLARES_KEY),
  setPublicFlares: (on) => {
    trackAction('settings.change', { key: 'publicFlares', on })
    save(PUBLIC_FLARES_KEY, on)
    set({ publicFlares: on })
  },
  subwayLines: readOnByDefault(SUBWAY_LINES_KEY),
  setSubwayLines: (on) => {
    trackAction('settings.change', { key: 'subwayLines', on })
    save(SUBWAY_LINES_KEY, on)
    set({ subwayLines: on })
  },
}))
