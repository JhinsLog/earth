import { create } from 'zustand'

interface SettingsState {
  /** 홈 지구본 밤 지역의 도시 불빛. 별과 겹쳐 보여 기본은 끈다. 밤 지역의 도시 위치를 찾을 때 켠다. */
  cityLights: boolean
  setCityLights: (on: boolean) => void
}

const CITY_LIGHTS_KEY = 'earth:cityLights'

// 화면 취향일 뿐이라 브라우저에만 둔다. 저장소를 못 쓰는 환경(사생활 보호 창 등)에서는 기본값으로 동작한다.
function readCityLights(): boolean {
  try {
    return localStorage.getItem(CITY_LIGHTS_KEY) === 'on'
  } catch {
    return false
  }
}

export const useSettingsStore = create<SettingsState>((set) => ({
  cityLights: readCityLights(),
  setCityLights: (on) => {
    try {
      localStorage.setItem(CITY_LIGHTS_KEY, on ? 'on' : 'off')
    } catch {
      // 저장 실패는 이번 방문 동안만 적용되는 것으로 충분하다.
    }
    set({ cityLights: on })
  },
}))
