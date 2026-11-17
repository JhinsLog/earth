import { create } from 'zustand'
import { trackAction } from '../lib/telemetry'

interface SettingsState {
  /** 홈 라스 밤 지역의 도시 불빛. 플레어와 겹쳐 보여 기본은 끈다. 밤 지역의 도시 위치를 찾을 때 켠다. */
  cityLights: boolean
  setCityLights: (on: boolean) => void
  /** 공공 정보(교통 돌발 등 지금 벌어지는 상황)와 지진을 보일지. 기본은 켠다. 사람이 남긴 플레어만 보고 싶을 때 끈다. */
  publicFlares: boolean
  setPublicFlares: (on: boolean) => void
  /** 축제·행사(스키장 개장·파리 행사 등 공식 일정) 플레어를 보일지. 기본은 켠다. 며칠씩 떠 있어 지금의 상황만 보고 싶을 때 끈다. */
  festivalFlares: boolean
  setFestivalFlares: (on: boolean) => void
  /** 지도를 도시 단위로 확대했을 때 지하철·전철 노선을 보일지. 기본은 켠다. 플레어보다 아래에 그려져 가리지 않는다. */
  subwayLines: boolean
  setSubwayLines: (on: boolean) => void
  /**
   * 자전. 홈 라스는 실제 자전(하루 한 바퀴), 지도는 저배율에서 천천히 돈다(1시간 한 바퀴). 끄면 보던 자리가 멈춰 있고,
   * 홈 라스의 낮·밤 경계는 실제 시각대로 계속 지표 위를 지나간다(태양 위치는 그대로 현실). 기본은 켠다(현실 지구 우선).
   */
  autoRotate: boolean
  setAutoRotate: (on: boolean) => void
  /**
   * 달빛(0~1, 옵션 창의 막대). 해가 진 지역 전체를 이만큼 달빛으로 밝힌다. 휴대폰에서 길게 눌러 비추면 그 자리가
   * 손가락에 가려서 따로 둔다. 현실 지구 우선 원칙에 따라 저장하지 않고 방문마다 0(실제 밤)에서 시작한다.
   */
  moonlight: number
  setMoonlight: (level: number) => void
}

const CITY_LIGHTS_KEY = 'earth:cityLights'
const PUBLIC_FLARES_KEY = 'earth:publicFlares'
const FESTIVAL_FLARES_KEY = 'earth:festivalFlares'
const SUBWAY_LINES_KEY = 'earth:subwayLines'
const AUTO_ROTATE_KEY = 'earth:autoRotate'

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
  festivalFlares: readOnByDefault(FESTIVAL_FLARES_KEY),
  setFestivalFlares: (on) => {
    trackAction('settings.change', { key: 'festivalFlares', on })
    save(FESTIVAL_FLARES_KEY, on)
    set({ festivalFlares: on })
  },
  subwayLines: readOnByDefault(SUBWAY_LINES_KEY),
  setSubwayLines: (on) => {
    trackAction('settings.change', { key: 'subwayLines', on })
    save(SUBWAY_LINES_KEY, on)
    set({ subwayLines: on })
  },
  autoRotate: readOnByDefault(AUTO_ROTATE_KEY),
  setAutoRotate: (on) => {
    trackAction('settings.change', { key: 'autoRotate', on })
    save(AUTO_ROTATE_KEY, on)
    set({ autoRotate: on })
  },
  moonlight: 0,
  setMoonlight: (level) => set({ moonlight: Math.min(1, Math.max(0, level)) }),
}))
